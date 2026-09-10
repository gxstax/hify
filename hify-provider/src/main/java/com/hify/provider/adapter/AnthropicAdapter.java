package com.hify.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Anthropic protocol: GET {base}/v1/models with {@code x-api-key} and the
 * required {@code anthropic-version} header.
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
  public ConnectionTestResult testConnection(Provider provider) {
    long start = System.currentTimeMillis();
    String body = llmHttpClient.get(
        v1ModelsUrl(provider.getBaseUrl()),
        Map.of("x-api-key", requireApiKey(provider), "anthropic-version", ANTHROPIC_VERSION),
        PROBE_TIMEOUT);
    return parseModelList(body, "data", start);
  }
}
