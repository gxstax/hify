package com.hify.knowledge.config;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.provider.dto.ModelInvocationConfig;
import com.hify.provider.service.ModelConfigService;
import java.util.Set;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Fails startup when the document processing contract cannot be satisfied. */
@Component
public class KnowledgeConfigurationValidator implements InitializingBean {

  private static final int REQUIRED_VECTOR_DIMENSION = 1536;
  private static final int REQUIRED_CHUNK_SIZE = 512;
  private static final int REQUIRED_CHUNK_OVERLAP = 64;
  private static final int MAX_BATCH_SIZE = 100;
  private static final Set<String> SUPPORTED_PROVIDER_TYPES =
      Set.of("OPENAI", "OPENAI_COMPATIBLE");

  private final KnowledgeProperties properties;
  private final ModelConfigService modelConfigService;

  public KnowledgeConfigurationValidator(KnowledgeProperties properties,
      ModelConfigService modelConfigService) {
    this.properties = properties;
    this.modelConfigService = modelConfigService;
  }

  @Override
  public void afterPropertiesSet() {
    KnowledgeProperties.Embedding embedding = properties.getEmbedding();
    if (embedding.getModelConfigId() == null) {
      throw invalid("必须配置 hify.knowledge.embedding.model-config-id");
    }
    if (properties.getVectorDimension() != REQUIRED_VECTOR_DIMENSION) {
      throw invalid("知识库向量维度必须为 1536");
    }
    if (properties.getChunkSize() != REQUIRED_CHUNK_SIZE
        || properties.getChunkOverlap() != REQUIRED_CHUNK_OVERLAP) {
      throw invalid("知识库分块参数必须为 chunk-size=512、chunk-overlap=64");
    }
    if (embedding.getBatchSize() < 1 || embedding.getBatchSize() > MAX_BATCH_SIZE) {
      throw invalid("Embedding batch-size 必须在 1 到 100 之间");
    }
    if (embedding.getTimeout() == null || embedding.getTimeout().isZero()
        || embedding.getTimeout().isNegative()) {
      throw invalid("Embedding timeout 必须大于 0");
    }
    ModelInvocationConfig config =
        modelConfigService.requireInvocationConfig(embedding.getModelConfigId());
    if (!SUPPORTED_PROVIDER_TYPES.contains(config.getProviderType())) {
      throw invalid("Embedding 模型仅支持 OPENAI 或 OPENAI_COMPATIBLE Provider");
    }
  }

  private BizException invalid(String message) {
    return new BizException(ErrorCode.PARAM_ERROR, message);
  }
}
