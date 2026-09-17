package com.hify.provider.dto;

import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import lombok.Data;

/**
 * Cross-module view of a model config (used by other modules, e.g. agent
 * binding): identity plus enough provider context for display. No credentials.
 */
@Data
public class ModelConfigBrief {

  private Long id;
  private String name;
  private String modelId;
  private Long providerId;
  /** Null when the owning provider was deleted. */
  private String providerName;
  private String providerType;
  private Integer contextSize;

  public static ModelConfigBrief of(ModelConfig model, Provider provider) {
    ModelConfigBrief brief = new ModelConfigBrief();
    brief.setId(model.getId());
    brief.setName(model.getName());
    brief.setModelId(model.getModelId());
    brief.setProviderId(model.getProviderId());
    brief.setProviderName(provider == null ? null : provider.getName());
    brief.setProviderType(provider == null ? null : provider.getType());
    brief.setContextSize(model.getContextSize());
    return brief;
  }

}
