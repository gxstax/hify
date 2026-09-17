package com.hify.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.entity.Provider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Ollama protocol: GET {base}/api/tags, no authentication; model names in
 * {@code models[].name}.
 */
@Component
public class OllamaAdapter extends AbstractProviderAdapter {

  private static final Set<String> TYPES = Set.of("OLLAMA");

  public OllamaAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
    super(llmHttpClient, objectMapper);
  }

  @Override
  public Set<String> supportedTypes() {
    return TYPES;
  }

  @Override
  public List<String> listModels(Provider provider) {
    String body = llmHttpClient.get(
        joinUrl(provider.getBaseUrl(), "/api/tags"),
        Map.of(),
        PROBE_TIMEOUT);
    return parseModelIds(body, "models", "name");
  }

  /** TODO: map onto Ollama's native {@code /api/chat} in a later task. */
  @Override
  public ChatResponse chat(Provider provider, ChatRequest request) {
    throw new UnsupportedOperationException(
        "OLLAMA chat completions are not implemented yet");
  }

  /** TODO: Ollama streams plain JSON lines (message.content deltas, done flag). */
  @Override
  public void streamChat(Provider provider, ChatRequest request, ChatStreamCallback callback) {
    throw new UnsupportedOperationException(
        "OLLAMA streaming is not implemented yet");
  }
}
