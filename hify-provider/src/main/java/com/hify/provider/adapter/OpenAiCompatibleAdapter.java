package com.hify.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible providers: same wire protocol as {@link OpenAiAdapter}
 * (Bearer auth, {@code /v1/models}, {@code data[].id}) — no extra code, only
 * a different set of provider types. Covers self-hosted gateways and vendors
 * following the OpenAI API shape (e.g. DeepSeek).
 */
@Component
public class OpenAiCompatibleAdapter extends OpenAiAdapter {

  private static final Set<String> TYPES = Set.of("OPENAI_COMPATIBLE", "DEEPSEEK");

  public OpenAiCompatibleAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
    super(llmHttpClient, objectMapper);
  }

  @Override
  public Set<String> supportedTypes() {
    return TYPES;
  }
}
