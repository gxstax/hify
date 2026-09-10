package com.hify.provider.dto;

import com.hify.provider.entity.Provider;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Provider view returned to clients. Deliberately excludes authConfig —
 * credentials must never leave the server. API keys are write-only.
 *
 * <p>{@code healthStatus / healthLatencyMs / enabledModelCount} are filled
 * only by the list endpoint (joined from provider_health and model_config);
 * on detail payloads they stay null.
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

  /** Latest probe status: UP / DOWN / DEGRADED / UNKNOWN (from provider_health). */
  private String healthStatus;

  /** Latest probe latency, ms. */
  private Integer healthLatencyMs;

  /** Number of enabled model configs under this provider. */
  private Long enabledModelCount;

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
