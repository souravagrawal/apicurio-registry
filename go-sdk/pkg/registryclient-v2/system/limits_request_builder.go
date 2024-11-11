package system

import (
	"context"
	i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f "github.com/microsoft/kiota-abstractions-go"
)

// LimitsRequestBuilder retrieve resource limits information
type LimitsRequestBuilder struct {
	i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.BaseRequestBuilder
}

// LimitsRequestBuilderGetRequestConfiguration configuration for the request such as headers, query parameters, and middleware options.
type LimitsRequestBuilderGetRequestConfiguration struct {
	// Request headers
	Headers *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestHeaders
	// Request options
	Options []i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestOption
}

// NewLimitsRequestBuilderInternal instantiates a new LimitsRequestBuilder and sets the default values.
func NewLimitsRequestBuilderInternal(pathParameters map[string]string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *LimitsRequestBuilder {
	m := &LimitsRequestBuilder{
		BaseRequestBuilder: *i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewBaseRequestBuilder(requestAdapter, "{+baseurl}/system/limits", pathParameters),
	}
	return m
}

// NewLimitsRequestBuilder instantiates a new LimitsRequestBuilder and sets the default values.
func NewLimitsRequestBuilder(rawUrl string, requestAdapter i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestAdapter) *LimitsRequestBuilder {
	urlParams := make(map[string]string)
	urlParams["request-raw-url"] = rawUrl
	return NewLimitsRequestBuilderInternal(urlParams, requestAdapter)
}

// Get this operation retrieves the list of limitations on used resources, that are applied on the current instance of Registry.
// returns a Limitsable when successful
// returns a Error error when the service returns a 500 status code
func (m *LimitsRequestBuilder) Get(ctx context.Context, requestConfiguration *LimitsRequestBuilderGetRequestConfiguration) (i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.Limitsable, error) {
	requestInfo, err := m.ToGetRequestInformation(ctx, requestConfiguration)
	if err != nil {
		return nil, err
	}
	errorMapping := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.ErrorMappings{
		"500": i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateErrorFromDiscriminatorValue,
	}
	res, err := m.BaseRequestBuilder.RequestAdapter.Send(ctx, requestInfo, i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.CreateLimitsFromDiscriminatorValue, errorMapping)
	if err != nil {
		return nil, err
	}
	if res == nil {
		return nil, nil
	}
	return res.(i80228d093fd3b582ec81b86f113cc707692a60cdd08bae7a390086a8438c7543.Limitsable), nil
}

// ToGetRequestInformation this operation retrieves the list of limitations on used resources, that are applied on the current instance of Registry.
// returns a *RequestInformation when successful
func (m *LimitsRequestBuilder) ToGetRequestInformation(ctx context.Context, requestConfiguration *LimitsRequestBuilderGetRequestConfiguration) (*i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.RequestInformation, error) {
	requestInfo := i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.NewRequestInformationWithMethodAndUrlTemplateAndPathParameters(i2ae4187f7daee263371cb1c977df639813ab50ffa529013b7437480d1ec0158f.GET, m.BaseRequestBuilder.UrlTemplate, m.BaseRequestBuilder.PathParameters)
	if requestConfiguration != nil {
		requestInfo.Headers.AddAll(requestConfiguration.Headers)
		requestInfo.AddRequestOptions(requestConfiguration.Options)
	}
	requestInfo.Headers.TryAdd("Accept", "application/json")
	return requestInfo, nil
}

// WithUrl returns a request builder with the provided arbitrary URL. Using this method means any other path or query parameters are ignored.
// returns a *LimitsRequestBuilder when successful
func (m *LimitsRequestBuilder) WithUrl(rawUrl string) *LimitsRequestBuilder {
	return NewLimitsRequestBuilder(rawUrl, m.BaseRequestBuilder.RequestAdapter)
}
