package com.hify.provider.service;

import com.hify.common.dto.PageResult;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.dto.ProviderDetail;
import com.hify.provider.dto.ProviderResp;
import com.hify.provider.entity.Provider;

/**
 * Business facade of provider access instances. All rules live in the
 * implementation; controllers only validate parameters and delegate here.
 */
public interface ProviderService {

  /** Create an access instance; the name must not collide with an existing one. */
  Provider createProvider(Provider provider);

  /** Provider + its model configs + latest health probe (null when never probed). */
  ProviderDetail getDetail(Long id);

  /**
   * Page through providers, optionally filtered by type / enabled, newest
   * first. Each item carries its latest health probe and enabled-model count
   * (joined in the service; not cached — health data must stay fresh).
   */
  PageResult<ProviderResp> listProviders(int page, int pageSize, String type, Boolean enabled);

  /** Update non-null fields of the provider with the given id. */
  void updateProvider(Long id, Provider update);

  /** Logical delete of the provider and its model configs; health row is purged. */
  void deleteProvider(Long id);

  /**
   * Probe connectivity by calling the provider's model-list endpoint with the
   * auth of its type. 10s timeout; never throws — failures come back as
   * {@code success = false}.
   */
  ConnectionTestResult testConnection(Long id);
}
