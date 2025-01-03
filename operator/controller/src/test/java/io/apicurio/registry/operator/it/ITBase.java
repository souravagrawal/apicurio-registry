package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.Constants;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkus.logging.Log;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.*;

import java.io.FileInputStream;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ITBase {

    public static final String DEPLOYMENT_TARGET = "test.operator.deployment-target";
    public static final String OPERATOR_DEPLOYMENT_PROP = "test.operator.deployment";
    public static final String INGRESS_HOST_PROP = "test.operator.ingress-host";
    public static final String INGRESS_SKIP_PROP = "test.operator.ingress-skip";
    public static final String CLEANUP = "test.operator.cleanup";
    public static final String GENERATED_RESOURCES_FOLDER = "target/kubernetes/";
    public static final String CRD_FILE = "../model/target/classes/META-INF/fabric8/apicurioregistries3.registry.apicur.io-v1.yml";

    public enum OperatorDeployment {
        local, remote
    }

    protected static OperatorDeployment operatorDeployment;
    protected static Instance<Reconciler<? extends HasMetadata>> reconcilers;
    protected static QuarkusConfigurationService configuration;
    protected static KubernetesClient client;
    protected static PortForwardManager portForwardManager;
    protected static IngressManager ingressManager;
    protected static String deploymentTarget;
    protected static String namespace;
    protected static boolean cleanup;
    private static Operator operator;

    @BeforeAll
    public static void before() throws Exception {
        configuration = CDI.current().select(QuarkusConfigurationService.class).get();
        reconcilers = CDI.current().select(new TypeLiteral<>() {
        });
        operatorDeployment = ConfigProvider.getConfig()
                .getOptionalValue(OPERATOR_DEPLOYMENT_PROP, OperatorDeployment.class)
                .orElse(OperatorDeployment.local);
        deploymentTarget = ConfigProvider.getConfig().getOptionalValue(DEPLOYMENT_TARGET, String.class)
                .orElse("k8s");
        cleanup = ConfigProvider.getConfig().getOptionalValue(CLEANUP, Boolean.class).orElse(true);

        setDefaultAwaitilityTimings();
        calculateNamespace();
        createK8sClient();
        createCRDs();
        createNamespace();

        portForwardManager = new PortForwardManager(client, namespace);
        ingressManager = new IngressManager(client, namespace);

        if (operatorDeployment == OperatorDeployment.remote) {
            createGeneratedResources();
        } else {
            createOperator();
            registerReconcilers();
            operator.start();
        }
    }

    @BeforeEach
    public void beforeEach(TestInfo testInfo) {
        String testClassName = testInfo.getTestClass().map(c -> c.getSimpleName() + ".").orElse("");
        Log.info("\n------- STARTING: " + testClassName + testInfo.getDisplayName() + "\n"
                + "------- Namespace: " + namespace + "\n" + "------- Mode: "
                + ((operatorDeployment == OperatorDeployment.remote) ? "remote" : "local") + "\n"
                + "------- Deployment target: " + deploymentTarget);
    }

    private static void createK8sClient() {
        client = new KubernetesClientBuilder()
                .withConfig(new ConfigBuilder(Config.autoConfigure(null)).withNamespace(namespace).build())
                .build();
    }

    private static void createGeneratedResources() throws Exception {
        Log.info("Creating generated resources into Namespace " + namespace);
        try (var fis = new FileInputStream(GENERATED_RESOURCES_FOLDER + deploymentTarget + ".json")) {
            KubernetesList resources = Serialization.unmarshal(fis);

            resources.getItems().stream().forEach(r -> {
                if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding) {
                    var crb = (ClusterRoleBinding) r;
                    crb.getSubjects().stream().forEach(s -> s.setNamespace(namespace));
                    // TODO: We need to patch the generated resources, because the referenced ClusterRole name
                    // is
                    // wrong.
                    if ("apicurioregistry3reconciler-cluster-role-binding"
                            .equals(crb.getMetadata().getName())) {
                        crb.getRoleRef().setName("apicurioregistry3reconciler-cluster-role");
                    }
                }
                client.resource(r).inNamespace(namespace).createOrReplace();
            });
        }
    }

    private static void cleanGeneratedResources() throws Exception {
        if (cleanup) {
            Log.info("Deleting generated resources from Namespace " + namespace);
            try (var fis = new FileInputStream(GENERATED_RESOURCES_FOLDER + deploymentTarget + ".json")) {
                KubernetesList resources = Serialization.unmarshal(fis);

                resources.getItems().stream().forEach(r -> {
                    if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding) {
                        var crb = (ClusterRoleBinding) r;
                        crb.getSubjects().stream().forEach(s -> s.setNamespace(namespace));
                    }
                    client.resource(r).inNamespace(namespace).delete();
                });
            }
        }
    }

    private static void createCRDs() {
        Log.info("Creating CRDs");
        try {
            var crd = client.load(new FileInputStream(CRD_FILE));
            crd.createOrReplace();
            await().ignoreExceptions().until(() -> {
                crd.resources().forEach(r -> assertThat(r.get()).isNotNull());
                return true;
            });
        } catch (Exception e) {
            Log.warn("Failed to create the CRD, retrying", e);
            createCRDs();
        }
    }

    private static void registerReconcilers() {
        Log.info("Registering reconcilers for operator : " + operator + " [" + operatorDeployment + "]");

        for (Reconciler<?> reconciler : reconcilers) {
            Log.info("Register and apply : " + reconciler.getClass().getName());
            operator.register(reconciler);
        }
    }

    private static void createOperator() {
        operator = new Operator(configurationServiceOverrider -> {
            configurationServiceOverrider.withKubernetesClient(client);
        });
    }

    private static void createNamespace() {
        Log.info("Creating Namespace " + namespace);
        client.resource(
                new NamespaceBuilder().withNewMetadata().addToLabels("app", "apicurio-registry-operator-test")
                        .withName(namespace).endMetadata().build())
                .create();
    }

    private static void calculateNamespace() {
        namespace = ("apicurio-registry-operator-test-" + UUID.randomUUID()).substring(0, 63);
    }

    private static void setDefaultAwaitilityTimings() {
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(5));
        Awaitility.setDefaultTimeout(Duration.ofSeconds(5 * 60));
    }

    @AfterEach
    public void cleanup() {
        if (cleanup) {
            Log.info("Deleting CRs");
            client.resources(ApicurioRegistry3.class).delete();
            await().untilAsserted(() -> {
                var registryDeployments = client.apps().deployments().inNamespace(namespace)
                        .withLabels(Constants.BASIC_LABELS).list().getItems();
                assertThat(registryDeployments.size()).isZero();
            });
        }
    }

    @AfterAll
    public static void after() throws Exception {
        portForwardManager.stop();
        if (operatorDeployment == OperatorDeployment.local) {
            Log.info("Stopping Operator");
            operator.stop();

            Log.info("Creating new K8s Client");
            // create a new client bc operator has closed the old one
            createK8sClient();
        } else {
            cleanGeneratedResources();
        }

        if (cleanup) {
            Log.info("Deleting namespace : " + namespace);
            assertThat(client.namespaces().withName(namespace).delete()).isNotNull();
        }
        client.close();
    }
}
