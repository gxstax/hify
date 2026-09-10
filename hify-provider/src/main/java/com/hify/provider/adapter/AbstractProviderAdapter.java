package com.hify.provider.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.exception.LlmApiException.LlmErrorType;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.entity.Provider;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Shared plumbing for provider adapters: HTTP client, JSON parsing, auth
 * extraction and URL joining. Subclasses only declare their protocol.
 */
public abstract class AbstractProviderAdapter implements ProviderAdapter {

  /** Connectivity probe timeout (CLAUDE.md: 10s). */
  protected static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);

  protected final LlmHttpClient llmHttpClient;
  private final ObjectMapper objectMapper;

  protected AbstractProviderAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
    this.llmHttpClient = llmHttpClient;
    this.objectMapper = objectMapper;
  }

  /** Read the apiKey out of auth_config, or fail with a config error. */
  protected String requireApiKey(Provider provider) {
    Map<String, Object> config = provider.getAuthConfig();
    Object apiKey = config == null ? null : config.get("apiKey");
    if (apiKey == null || apiKey.toString().isBlank()) {
      throw new BizException(ErrorCode.PARAM_ERROR,
          "供应商未配置 apiKey: " + provider.getName());
    }
    return apiKey.toString();
  }

  /** {base}/v1/models — tolerates a base_url that already ends with /v1. */
  protected String v1ModelsUrl(String baseUrl) {
    String base = trimSlash(baseUrl);
    return base.toLowerCase(Locale.ROOT).endsWith("/v1") ? base + "/models" : base + "/v1/models";
  }

  protected String joinUrl(String baseUrl, String path) {
    return trimSlash(baseUrl) + path;
  }

  private String trimSlash(String url) {
    if (url == null || url.isBlank()) {
      throw new BizException(ErrorCode.PARAM_ERROR, "供应商未配置 base_url");
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /** Extract the advertised model count from the JSON array field. */
  protected ConnectionTestResult parseModelList(String body, String arrayField, long start) {
    JsonNode array;
    try {
      array = objectMapper.readTree(body).path(arrayField);
    } catch (JsonProcessingException e) {
      throw new LlmApiException(LlmErrorType.INVALID_REQUEST,
          "连通性响应不是合法 JSON（base_url 是否正确？）", e);
    }
    if (!array.isArray()) {
      throw new LlmApiException(LlmErrorType.INVALID_REQUEST,
          "响应中缺少模型列表字段: " + arrayField);
    }
    return ConnectionTestResult.success(elapsed(start), array.size());
  }

  protected long elapsed(long start) {
    return System.currentTimeMillis() - start;
  }
}
