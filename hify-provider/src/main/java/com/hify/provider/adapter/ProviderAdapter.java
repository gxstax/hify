package com.hify.provider.adapter;

import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import java.util.Set;

/**
 * Protocol adapter for one LLM provider family: it knows how to reach and
 * interpret that provider's API. Currently only connectivity probing is
 * protocol-specific; future capabilities (chat completions, model sync)
 * extend this interface.
 *
 * <p>Implementations are Spring beans (@Component); {@link ProviderAdapterFactory}
 * collects them automatically, so "registering" a new provider means writing
 * an adapter and keeping its {@link #supportedTypes()} in sync with the
 * provider type enum. See .claude/skills/provider-adapter for the full recipe.
 */
public interface ProviderAdapter {

  /** Provider type values (upper case) this adapter handles. */
  Set<String> supportedTypes();

  /**
   * Probe the provider's model-list endpoint.
   *
   * @throws com.hify.common.exception.LlmApiException on transport/HTTP failure
   *         (the caller turns it into a failed {@link ConnectionTestResult})
   * @throws com.hify.common.exception.BizException on configuration errors
   *         (missing apiKey / base_url) — these are not probe outcomes
   */
  ConnectionTestResult testConnection(Provider provider);
}
