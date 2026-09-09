package com.hify.provider.dto;

import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import com.hify.provider.entity.ProviderHealth;
import java.util.List;
import lombok.Data;

/**
 * Detail view of a provider: the row itself plus its models and health probe.
 * providerHealth is null when no probe has run yet.
 */
@Data
public class ProviderDetail {

  private Provider provider;

  private List<ModelConfig> modelConfigs;

  private ProviderHealth providerHealth;

  public static ProviderDetail of(Provider provider, List<ModelConfig> modelConfigs,
      ProviderHealth providerHealth) {
    ProviderDetail detail = new ProviderDetail();
    detail.setProvider(provider);
    detail.setModelConfigs(modelConfigs);
    detail.setProviderHealth(providerHealth);
    return detail;
  }
}
