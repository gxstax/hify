package com.hify.provider.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.entity.Provider;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * OpenAI protocol: GET {base}/v1/models with {@code Authorization: Bearer},
 * model ids in {@code data[].id}.
 *
 * <p>Serves as the base for OpenAI-compatible providers — subclasses only
 * override {@link #supportedTypes()} (see {@link OpenAiCompatibleAdapter}).
 */
@Component
public class OpenAiAdapter extends AbstractProviderAdapter {

  private static final Set<String> TYPES = Set.of("OPENAI");

  public OpenAiAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
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
        bearerHeaders(provider),
        PROBE_TIMEOUT);
    return parseModelIds(body, "data", "id");
  }
}
