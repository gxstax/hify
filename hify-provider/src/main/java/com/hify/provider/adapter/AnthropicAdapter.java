package com.hify.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.entity.Provider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Anthropic protocol: GET {base}/v1/models with {@code x-api-key} and the
 * required {@code anthropic-version} header; model ids in {@code data[].id}.
 */
@Component
public class AnthropicAdapter extends AbstractProviderAdapter {

  private static final Set<String> TYPES = Set.of("ANTHROPIC");

  /** Anthropic requires an explicit API version header. */
  private static final String ANTHROPIC_VERSION = "2023-06-01";

  public AnthropicAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
    super(llmHttpClient, objectMapper);
  }

  @Override
  public Set<String> supportedTypes() {
    return TYPES;
  }

  @Override
  public List<String> listModels(Provider provider) {
    String body = llmHttpClient.get(
        v1ModelsUrl(provider.getBaseUrl()),
        Map.of("x-api-key", requireApiKey(provider), "anthropic-version", ANTHROPIC_VERSION),
        PROBE_TIMEOUT);
    return parseModelIds(body, "data", "id");
  }

  /** TODO: map onto the Messages API ({@code /v1/messages}) in a later task. */
  @Override
  public ChatResponse chat(Provider provider, ChatRequest request) {
    throw new UnsupportedOperationException(
        "ANTHROPIC chat completions are not implemented yet");
  }

  /** TODO: Anthropic streams with named events (content_block_delta/message_stop). */
  @Override
  public void streamChat(Provider provider, ChatRequest request, ChatStreamCallback callback) {
    throw new UnsupportedOperationException(
        "ANTHROPIC streaming is not implemented yet");
  }
}
