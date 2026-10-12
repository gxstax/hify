package com.hify.provider.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;

/** Internal server-side model invocation settings. Never expose this object through controllers. */
public final class ModelInvocationConfig {

  private final Long modelConfigId;
  private final String modelId;
  private final String providerType;
  private final String baseUrl;
  private final String apiKey;
  private final Map<String, Object> invocationParams;

  public ModelInvocationConfig(Long modelConfigId, String modelId, String providerType,
      String baseUrl, String apiKey, Map<String, Object> invocationParams) {
    this.modelConfigId = modelConfigId;
    this.modelId = modelId;
    this.providerType = providerType;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.invocationParams = invocationParams == null ? Map.of() : Map.copyOf(invocationParams);
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

  @JsonIgnore
  public Map<String, Object> getInvocationParams() {
    return invocationParams;
  }

  @Override
  public String toString() {
    return "ModelInvocationConfig{modelConfigId=" + modelConfigId
        + ", modelId='" + modelId + '\''
        + ", providerType='" + providerType + '\''
        + ", baseUrl='" + baseUrl + '\''
        + ", apiKey='***', invocationParamKeys=" + invocationParams.keySet() + '}';
  }
}
