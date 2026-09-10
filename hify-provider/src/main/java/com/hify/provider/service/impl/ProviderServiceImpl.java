package com.hify.provider.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.constant.CacheNames;
import com.hify.common.dto.PageResult;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.exception.LlmApiException;
import com.hify.common.exception.LlmApiException.LlmErrorType;
import com.hify.common.http.LlmHttpClient;
import com.hify.common.util.PageHelper;
import com.hify.provider.dto.ConnectionTestResult;
import com.hify.provider.dto.ProviderDetail;
import com.hify.provider.dto.ProviderResp;
import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import com.hify.provider.entity.ProviderHealth;
import com.hify.provider.mapper.ModelConfigMapper;
import com.hify.provider.mapper.ProviderHealthMapper;
import com.hify.provider.mapper.ProviderMapper;
import com.hify.provider.service.ProviderService;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Provider business logic.
 *
 * <p>Cache (CLAUDE.md: provider configs, TTL 30min): reads are annotated
 * {@code @Cacheable} on cache {@code provider-cache}; every write evicts the
 * whole cache (allEntries) because paged lists can match any key combination.
 * ModelConfig / ProviderHealth changes made through other services must evict
 * this cache too — see CacheNames.PROVIDER.
 */
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

  /** Connectivity probe timeout (CLAUDE.md: 10s). */
  private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);

  private final ProviderMapper providerMapper;
  private final ModelConfigMapper modelConfigMapper;
  private final ProviderHealthMapper providerHealthMapper;
  private final LlmHttpClient llmHttpClient;
  private final ObjectMapper objectMapper;

  @Override
  @CacheEvict(cacheNames = CacheNames.PROVIDER, allEntries = true)
  public Provider createProvider(Provider provider) {
    ensureNameFree(provider.getName(), null);
    provider.setId(null); // never trust a caller-supplied id
    providerMapper.insert(provider);
    return provider;
  }

  @Override
  @Cacheable(cacheNames = CacheNames.PROVIDER, key = "'detail:' + #id")
  public ProviderDetail getDetail(Long id) {
    Provider provider = requireProvider(id);
    List<ModelConfig> modelConfigs = modelConfigMapper.selectList(
        Wrappers.<ModelConfig>lambdaQuery()
            .eq(ModelConfig::getProviderId, id)
            .orderByDesc(ModelConfig::getId));
    ProviderHealth health = providerHealthMapper.selectOne(
        Wrappers.<ProviderHealth>lambdaQuery().eq(ProviderHealth::getProviderId, id));
    return ProviderDetail.of(provider, modelConfigs, health);
  }

  @Override
  // Intentionally NOT cached: items carry live health probe data that changes
  // every minute (HealthCheckTask); a 30min cache would freeze it.
  public PageResult<ProviderResp> listProviders(int page, int pageSize, String type,
      Boolean enabled) {
    LambdaQueryWrapper<Provider> wrapper = Wrappers.<Provider>lambdaQuery()
        .eq(type != null && !type.isBlank(), Provider::getType, type)
        .eq(enabled != null, Provider::getEnabled, enabled)
        .orderByDesc(Provider::getId);
    Page<Provider> mpPage = PageHelper.toPage(page, pageSize);
    providerMapper.selectPage(mpPage, wrapper);

    List<ProviderResp> items = toRespList(mpPage.getRecords());
    return new PageResult<>(items, mpPage.getTotal(), (int) mpPage.getCurrent(),
        (int) mpPage.getSize());
  }

  /** Map entities to responses, batch-joining health probe and model counts. */
  private List<ProviderResp> toRespList(List<Provider> providers) {
    if (providers.isEmpty()) {
      return List.of();
    }
    List<Long> ids = providers.stream().map(Provider::getId).toList();

    Map<Long, ProviderHealth> healthByProvider = providerHealthMapper
        .selectList(Wrappers.<ProviderHealth>lambdaQuery().in(ProviderHealth::getProviderId, ids))
        .stream()
        .collect(Collectors.toMap(ProviderHealth::getProviderId, h -> h, (a, b) -> a));

    Map<Long, Long> modelCountByProvider = modelConfigMapper
        .selectList(Wrappers.<ModelConfig>lambdaQuery()
            .in(ModelConfig::getProviderId, ids)
            .eq(ModelConfig::getEnabled, true))
        .stream()
        .collect(Collectors.groupingBy(ModelConfig::getProviderId, Collectors.counting()));

    return providers.stream().map(p -> {
      ProviderResp resp = ProviderResp.from(p);
      ProviderHealth health = healthByProvider.get(p.getId());
      resp.setHealthStatus(health == null || health.getStatus() == null
          ? "UNKNOWN" : health.getStatus());
      resp.setHealthLatencyMs(health == null ? null : health.getLatencyMs());
      resp.setEnabledModelCount(modelCountByProvider.getOrDefault(p.getId(), 0L));
      return resp;
    }).toList();
  }

  @Override
  @CacheEvict(cacheNames = CacheNames.PROVIDER, allEntries = true)
  public void updateProvider(Long id, Provider update) {
    requireProvider(id);
    ensureNameFree(update.getName(), id);
    update.setId(id);
    // non-null fields only; createdAt/updatedAt auto-fill by MP infrastructure
    providerMapper.updateById(update);
  }

  @Override
  @CacheEvict(cacheNames = CacheNames.PROVIDER, allEntries = true)
  public void deleteProvider(Long id) {
    requireProvider(id);
    providerMapper.deleteById(id);                       // logical delete
    modelConfigMapper.delete(Wrappers.<ModelConfig>lambdaQuery()
        .eq(ModelConfig::getProviderId, id));            // logical delete (BaseEntity)
    providerHealthMapper.delete(Wrappers.<ProviderHealth>lambdaQuery()
        .eq(ProviderHealth::getProviderId, id));         // physical (no deleted column)
  }

  /** Load the row by id or fail with not-found. */
  private Provider requireProvider(Long id) {
    Provider provider = providerMapper.selectById(id);
    if (provider == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: id=" + id);
    }
    return provider;
  }

  /** Reject a duplicate name; {@code excludeId} skips the provider being updated. */
  private void ensureNameFree(String name, Long excludeId) {
    Long count = providerMapper.selectCount(Wrappers.<Provider>lambdaQuery()
        .eq(Provider::getName, name)
        .ne(excludeId != null, Provider::getId, excludeId));
    if (count != null && count > 0) {
      throw new BizException(ErrorCode.PARAM_ERROR, "供应商名称已存在: " + name);
    }
  }

  // ----------------------------------------------------------------------
  // Connectivity probing — dispatch by provider.type
  // ----------------------------------------------------------------------

  @Override
  public ConnectionTestResult testConnection(Long id) {
    Provider provider = requireProvider(id);
    long start = System.currentTimeMillis();
    try {
      return switch (provider.getType() == null ? "" : provider.getType().toUpperCase(Locale.ROOT)) {
        case "OPENAI", "DEEPSEEK", "OPENAI_COMPATIBLE" -> probeOpenAiCompatible(provider, start);
        case "ANTHROPIC" -> probeAnthropic(provider, start);
        case "OLLAMA" -> probeOllama(provider, start);
        default -> throw new BizException(ErrorCode.PARAM_ERROR,
            "不支持的供应商类型: " + provider.getType());
      };
    } catch (LlmApiException e) {
      // Transport/HTTP failures are a probe result, not an exception
      return ConnectionTestResult.failure(elapsed(start), e.getErrorType() + ": " + e.getMessage());
    }
  }

  /** OPENAI / DEEPSEEK / OPENAI_COMPATIBLE: GET {base}/v1/models with Authorization: Bearer. */
  private ConnectionTestResult probeOpenAiCompatible(Provider provider, long start) {
    String body = llmHttpClient.get(
        v1ModelsUrl(provider.getBaseUrl()),
        Map.of("Authorization", "Bearer " + requireApiKey(provider)),
        PROBE_TIMEOUT);
    return parseModelList(body, "data", start);
  }

  /** ANTHROPIC: GET {base}/v1/models with x-api-key + anthropic-version headers. */
  private ConnectionTestResult probeAnthropic(Provider provider, long start) {
    String body = llmHttpClient.get(
        v1ModelsUrl(provider.getBaseUrl()),
        Map.of("x-api-key", requireApiKey(provider), "anthropic-version", "2023-06-01"),
        PROBE_TIMEOUT);
    return parseModelList(body, "data", start);
  }

  /** OLLAMA: GET {base}/api/tags, no auth. */
  private ConnectionTestResult probeOllama(Provider provider, long start) {
    String body = llmHttpClient.get(
        joinUrl(provider.getBaseUrl(), "/api/tags"),
        Map.of(),
        PROBE_TIMEOUT);
    return parseModelList(body, "models", start);
  }

  /** Read the apiKey out of auth_config, or fail with a config error. */
  private String requireApiKey(Provider provider) {
    Map<String, Object> config = provider.getAuthConfig();
    Object apiKey = config == null ? null : config.get("apiKey");
    if (apiKey == null || apiKey.toString().isBlank()) {
      throw new BizException(ErrorCode.PARAM_ERROR,
          "供应商未配置 apiKey: " + provider.getName());
    }
    return apiKey.toString();
  }

  /** {base}/v1/models — tolerates a base_url that already ends with /v1. */
  private String v1ModelsUrl(String baseUrl) {
    String base = trimSlash(baseUrl);
    return base.toLowerCase(Locale.ROOT).endsWith("/v1") ? base + "/models" : base + "/v1/models";
  }

  private String joinUrl(String baseUrl, String path) {
    return trimSlash(baseUrl) + path;
  }

  private String trimSlash(String url) {
    if (url == null || url.isBlank()) {
      throw new BizException(ErrorCode.PARAM_ERROR, "供应商未配置 base_url");
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  /** Extract the advertised model count from the JSON array field. */
  private ConnectionTestResult parseModelList(String body, String arrayField, long start) {
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

  private long elapsed(long start) {
    return System.currentTimeMillis() - start;
  }
}
