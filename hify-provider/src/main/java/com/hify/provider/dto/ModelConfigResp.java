package com.hify.provider.dto;

import com.hify.provider.entity.ModelConfig;
import java.time.LocalDateTime;
import lombok.Data;

/** Model config view for the provider detail payload. */
@Data
public class ModelConfigResp {

  private Long id;
  private Long providerId;
  private String name;
  private String modelId;
  private Integer contextSize;
  private Boolean enabled;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static ModelConfigResp from(ModelConfig m) {
    ModelConfigResp resp = new ModelConfigResp();
    resp.setId(m.getId());
    resp.setProviderId(m.getProviderId());
    resp.setName(m.getName());
    resp.setModelId(m.getModelId());
    resp.setContextSize(m.getContextSize());
    resp.setEnabled(m.getEnabled());
    resp.setCreatedAt(m.getCreatedAt());
    resp.setUpdatedAt(m.getUpdatedAt());
    return resp;
  }
}
