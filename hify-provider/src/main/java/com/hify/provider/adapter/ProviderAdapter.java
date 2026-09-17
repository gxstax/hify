package com.hify.provider.adapter;

import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import java.util.List;
import java.util.Set;

/**
 * Protocol adapter for one LLM provider family: it knows how to reach and
 * interpret that provider's API.
 *
 * <p>Implementations are Spring beans (@Component); {@link ProviderAdapterFactory}
 * collects them automatically, so "registering" a new provider means writing
 * an adapter and keeping its {@link #supportedTypes()} in sync with the
 * provider type enum. See .claude/skills/provider-adapter for the full recipe.
 *
 * <p>{@link #testConnection} is provided by {@link AbstractProviderAdapter}
 * (list models + count); subclasses only implement {@link #listModels}.
 */
public interface ProviderAdapter {

  /** Provider type values (upper case) this adapter handles. */
  Set<String> supportedTypes();

  /**
   * Fetch the model ids advertised by the provider.
   *
   * <p>Used by connectivity tests today and by the upcoming model-sync
   * feature (import remote models into model_config).
   *
   * @throws com.hify.common.exception.LlmApiException on transport/HTTP failure
   * @throws com.hify.common.exception.BizException on configuration errors
   *         (missing apiKey / base_url) — not a probe outcome
   */
  List<String> listModels(Provider provider);

  /**
   * Probe connectivity by listing the provider's models.
   *
   * @throws com.hify.common.exception.LlmApiException on transport/HTTP failure
   *         (the caller turns it into a failed {@link ConnectionTestResult})
   * @throws com.hify.common.exception.BizException on configuration errors
   */
  ConnectionTestResult testConnection(Provider provider);

  /**
   * One-shot (non-streaming) chat completion.
   *
   * @throws com.hify.common.exception.LlmApiException classified transport/HTTP
   *         failure (see CLAUDE.md retry rules)
   */
  ChatResponse chat(Provider provider, ChatRequest request);

  /**
   * Streaming chat completion. Blocking call: deltas flow through the callback
   * until the provider ends the stream, then {@code onComplete} fires once
   * with the aggregated result. Run it on the {@code llmExecutor} pool.
   *
   * @throws com.hify.common.exception.LlmApiException classified failure
   */
  void streamChat(Provider provider, ChatRequest request, ChatStreamCallback callback);
}
