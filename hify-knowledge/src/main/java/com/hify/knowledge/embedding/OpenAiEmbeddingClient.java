package com.hify.knowledge.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.exception.LlmApiException.LlmErrorType;
import com.hify.common.http.LlmHttpClient;
import com.hify.knowledge.config.KnowledgeProperties;
import com.hify.provider.dto.ModelInvocationConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** OpenAI-compatible batch embedding client for the fixed pgvector dimension. */
@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {

  private static final int MAX_BATCH_SIZE = 100;

  private final LlmHttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final KnowledgeProperties properties;

  public OpenAiEmbeddingClient(LlmHttpClient httpClient, ObjectMapper objectMapper,
      KnowledgeProperties properties) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public List<float[]> embed(List<String> texts, ModelInvocationConfig config) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }
    int batchSize = Math.min(MAX_BATCH_SIZE,
        Math.max(1, properties.getEmbedding().getBatchSize()));
    List<float[]> result = new ArrayList<>(texts.size());
    for (int start = 0; start < texts.size(); start += batchSize) {
      int end = Math.min(start + batchSize, texts.size());
      result.addAll(embedBatch(texts.subList(start, end), config));
    }
    return result;
  }

  private List<float[]> embedBatch(List<String> texts, ModelInvocationConfig config) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", config.getModelId());
    payload.put("input", texts);
    String body;
    try {
      body = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "Embedding 请求序列化失败");
    }

    String response = httpClient.post(embeddingUrl(config.getBaseUrl()), Map.of(
        "Authorization", "Bearer " + config.getApiKey(),
        "Content-Type", "application/json"), body, properties.getEmbedding().getTimeout());
    return parseResponse(response, texts.size());
  }

  private List<float[]> parseResponse(String body, int expectedCount) {
    try {
      JsonNode data = objectMapper.readTree(body).path("data");
      if (!data.isArray() || data.size() != expectedCount) {
        throw invalidResponse("Embedding 响应数量与请求不一致");
      }
      float[][] ordered = new float[expectedCount][];
      for (JsonNode item : data) {
        int index = item.path("index").asInt(-1);
        JsonNode values = item.path("embedding");
        if (index < 0 || index >= expectedCount || !values.isArray()) {
          throw invalidResponse("Embedding 响应索引或向量格式无效");
        }
        if (ordered[index] != null) {
          throw invalidResponse("Embedding 响应包含重复索引");
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
          vector[i] = (float) values.get(i).asDouble(Double.NaN);
          if (!Float.isFinite(vector[i])) {
            throw invalidResponse("Embedding 响应包含无效数值");
          }
        }
        if (vector.length != properties.getVectorDimension()) {
          throw invalidResponse("Embedding 向量维度必须为 " + properties.getVectorDimension());
        }
        ordered[index] = vector;
      }
      if (Arrays.stream(ordered).anyMatch(value -> value == null)) {
        throw invalidResponse("Embedding 响应缺少索引");
      }
      return Arrays.asList(ordered);
    } catch (JsonProcessingException e) {
      throw invalidResponse("Embedding 响应不是合法 JSON");
    }
  }

  private String embeddingUrl(String baseUrl) {
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String lowerBase = base.toLowerCase(Locale.ROOT);
    if (lowerBase.endsWith("/v1/embeddings")) {
      return base;
    }
    String suffix = lowerBase.endsWith("/v1") ? "" : "/v1";
    return base + suffix + "/embeddings";
  }

  private LlmApiException invalidResponse(String message) {
    return new LlmApiException(LlmErrorType.INVALID_REQUEST, message);
  }
}
