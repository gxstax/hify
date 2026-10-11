package com.hify.provider.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.provider.dto.ModelConfigBrief;
import com.hify.provider.dto.ModelInvocationConfig;
import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import com.hify.provider.mapper.ModelConfigMapper;
import com.hify.provider.mapper.ProviderMapper;
import com.hify.provider.service.ModelConfigService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Model config business rules exposed to other modules. */
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

  private final ModelConfigMapper modelConfigMapper;
  private final ProviderMapper providerMapper;

  @Override
  public ModelConfigBrief requireUsable(Long id) {
    ModelConfig model = modelConfigMapper.selectById(id);
    if (model == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在: id=" + id);
    }
    if (!Boolean.TRUE.equals(model.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "模型已禁用: " + model.getName());
    }
    Provider provider = providerMapper.selectById(model.getProviderId());
    if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR,
          "模型所属供应商不可用: " + model.getName());
    }
    return ModelConfigBrief.of(model, provider);
  }

  @Override
  public ModelInvocationConfig requireInvocationConfig(Long id) {
    ModelConfig model = modelConfigMapper.selectById(id);
    if (model == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在: id=" + id);
    }
    if (!Boolean.TRUE.equals(model.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "模型已禁用: " + model.getName());
    }
    Provider provider = providerMapper.selectById(model.getProviderId());
    if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR,
          "模型所属供应商不可用: " + model.getName());
    }
    if (isBlank(model.getModelId()) || isBlank(provider.getBaseUrl())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "模型调用配置不完整: " + model.getName());
    }
    Object apiKeyValue = provider.getAuthConfig() == null
        ? null : provider.getAuthConfig().get("apiKey");
    String apiKey = apiKeyValue instanceof String value ? value : null;
    if (isBlank(apiKey)) {
      throw new BizException(ErrorCode.PARAM_ERROR, "模型供应商未配置 API Key: " + provider.getName());
    }
    return new ModelInvocationConfig(model.getId(), model.getModelId(), provider.getType(),
        provider.getBaseUrl(), apiKey);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  @Override
  public Map<Long, ModelConfigBrief> mapByIds(Collection<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    List<ModelConfig> models = modelConfigMapper.selectBatchIds(ids);
    if (models.isEmpty()) {
      return Map.of();
    }
    Set<Long> providerIds = models.stream()
        .map(ModelConfig::getProviderId)
        .collect(Collectors.toSet());
    Map<Long, Provider> providerById = providerMapper.selectBatchIds(providerIds).stream()
        .collect(Collectors.toMap(Provider::getId, p -> p));
    return models.stream().collect(Collectors.toMap(
        ModelConfig::getId, m -> ModelConfigBrief.of(m, providerById.get(m.getProviderId()))));
  }

  @Override
  public List<ModelConfigBrief> listAvailable() {
    List<Provider> providers = providerMapper.selectList(
        Wrappers.<Provider>lambdaQuery().eq(Provider::getEnabled, true));
    if (providers.isEmpty()) {
      return List.of();
    }
    Map<Long, Provider> providerById = providers.stream()
        .collect(Collectors.toMap(Provider::getId, p -> p));
    return modelConfigMapper.selectList(Wrappers.<ModelConfig>lambdaQuery()
            .in(ModelConfig::getProviderId, providerById.keySet())
            .eq(ModelConfig::getEnabled, true)
            .orderByDesc(ModelConfig::getId))
        .stream()
        .map(m -> ModelConfigBrief.of(m, providerById.get(m.getProviderId())))
        .toList();
  }
}
