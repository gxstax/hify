package com.hify.provider.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Internal server-side model invocation settings. Never expose this object through controllers. */
public final class ModelInvocationConfig {

  private final Long modelConfigId;
  private final String modelId;
  private final String providerType;
  private final String baseUrl;
  private final String apiKey;

  public ModelInvocationConfig(Long modelConfigId, String modelId, String providerType,
      String baseUrl, String apiKey) {
    this.modelConfigId = modelConfigId;
    this.modelId = modelId;
    this.providerType = providerType;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
  }

  public Long getModelConfigId() {
    return modelConfigId;
  }

  public String getModelId() {
    return modelId;
  }

  public String getProviderType() {
    return providerType;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  @JsonIgnore
  public String getApiKey() {
    return apiKey;
  }

  @Override
  public String toString() {
    return "ModelInvocationConfig{modelConfigId=" + modelConfigId
        + ", modelId='" + modelId + '\''
        + ", providerType='" + providerType + '\''
        + ", baseUrl='" + baseUrl + '\''
        + ", apiKey='***'}";
  }
}
