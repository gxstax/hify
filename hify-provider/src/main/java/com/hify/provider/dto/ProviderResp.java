package com.hify.provider.dto;

import com.hify.provider.entity.Provider;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Provider view returned to clients. Deliberately excludes authConfig —
 * credentials must never leave the server. API keys are write-only.
 */
@Data
public class ProviderResp {

  private Long id;
  private String name;
  private String type;
  private String baseUrl;
  private String description;
  private Boolean enabled;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static ProviderResp from(Provider p) {
    ProviderResp resp = new ProviderResp();
    resp.setId(p.getId());
    resp.setName(p.getName());
    resp.setType(p.getType());
    resp.setBaseUrl(p.getBaseUrl());
    resp.setDescription(p.getDescription());
    resp.setEnabled(p.getEnabled());
    resp.setCreatedAt(p.getCreatedAt());
    resp.setUpdatedAt(p.getUpdatedAt());
    return resp;
  }
}
