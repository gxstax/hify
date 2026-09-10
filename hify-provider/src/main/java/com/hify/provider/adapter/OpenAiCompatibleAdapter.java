package com.hify.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * OpenAI-style protocol: GET {base}/v1/models with {@code Authorization: Bearer}.
 * Covers OpenAI itself, DeepSeek and self-hosted OpenAI-compatible gateways.
 */
@Component
public class OpenAiCompatibleAdapter extends AbstractProviderAdapter {

  private static final Set<String> TYPES = Set.of("OPENAI", "DEEPSEEK", "OPENAI_COMPATIBLE");

  public OpenAiCompatibleAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
    super(llmHttpClient, objectMapper);
  }

  @Override
  public Set<String> supportedTypes() {
    return TYPES;
  }

  @Override
  public ConnectionTestResult testConnection(Provider provider) {
    long start = System.currentTimeMillis();
    String body = llmHttpClient.get(
        v1ModelsUrl(provider.getBaseUrl()),
        Map.of("Authorization", "Bearer " + requireApiKey(provider)),
        PROBE_TIMEOUT);
    return parseModelList(body, "data", start);
  }
}
