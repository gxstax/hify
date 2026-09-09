package com.hify.provider.dto;

import com.hify.provider.entity.ProviderHealth;
import java.util.List;
import lombok.Data;

/**
 * Detail payload: sanitized provider + its models + the latest health probe.
 * ProviderHealth rows carry no credentials, so the entity is used directly.
 */
@Data
public class ProviderDetailResp {

  private ProviderResp provider;
  private List<ModelConfigResp> models;
  private ProviderHealth health;

  public static ProviderDetailResp from(ProviderDetail detail) {
    ProviderDetailResp resp = new ProviderDetailResp();
    resp.setProvider(ProviderResp.from(detail.getProvider()));
    resp.setModels(detail.getModelConfigs().stream().map(ModelConfigResp::from).toList());
    resp.setHealth(detail.getProviderHealth());
    return resp;
  }
}
