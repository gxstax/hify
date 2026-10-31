package com.hify.provider.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.resilience.CircuitBreakerService;
import com.hify.provider.adapter.ChatMessage;
import com.hify.provider.adapter.ChatRequest;
import com.hify.provider.adapter.ChatResponse;
import com.hify.provider.adapter.ProviderAdapter;
import com.hify.provider.adapter.ProviderAdapterFactory;
import com.hify.provider.entity.ModelConfig;
import com.hify.provider.entity.Provider;
import com.hify.provider.mapper.ModelConfigMapper;
import com.hify.provider.mapper.ProviderMapper;
import com.hify.provider.service.LlmInvocationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Synchronous one-shot LLM invocation service. */
@Service
@RequiredArgsConstructor
public class LlmInvocationServiceImpl implements LlmInvocationService {

  private final ModelConfigMapper modelConfigMapper;
  private final ProviderMapper providerMapper;
  private final ProviderAdapterFactory providerAdapterFactory;
  private final CircuitBreakerService circuitBreakerService;

  @Override
  public String invoke(Long modelConfigId, String prompt) {
    ModelConfig model = modelConfigMapper.selectById(modelConfigId);
    if (model == null) {
      throw new BizException(ErrorCode.NOT_FOUND,
          "模型配置不存在: id=" + modelConfigId);
    }
    if (!Boolean.TRUE.equals(model.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "模型已禁用: " + model.getName());
    }
    if (isBlank(model.getModelId())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "模型 ID 不能为空: " + model.getName());
    }

    Provider provider = providerMapper.selectById(model.getProviderId());
    if (provider == null) {
      throw new BizException(ErrorCode.NOT_FOUND,
          "模型所属供应商不存在: id=" + model.getProviderId());
    }
    if (!Boolean.TRUE.equals(provider.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "供应商已禁用: " + provider.getName());
    }

    ChatRequest request = new ChatRequest();
    request.setModel(model.getModelId());
    request.setMessages(List.of(ChatMessage.user(prompt)));
    request.setExtraParams(model.getExtraParams());

    ProviderAdapter adapter = providerAdapterFactory.getAdapter(provider.getType());
    ChatResponse response = circuitBreakerService.executeWithRetry(
        provider.getName(), () -> adapter.chat(provider, request));
    if (response == null || isBlank(response.getContent())) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "模型响应内容为空");
    }
    return response.getContent();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
