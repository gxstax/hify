package com.hify.knowledge.config;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.provider.dto.ModelInvocationConfig;
import com.hify.provider.service.ModelConfigService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/** Fails startup when the document processing contract cannot be satisfied. */
@Component
public class KnowledgeConfigurationValidator implements InitializingBean {

  private static final int REQUIRED_VECTOR_DIMENSION = 1536;
  private static final int REQUIRED_CHUNK_SIZE = 512;
  private static final int REQUIRED_CHUNK_OVERLAP = 64;
  private static final int MAX_BATCH_SIZE = 20;
  private static final String REQUIRED_MODEL_ID = "qwen3.7-text-embedding";
  private static final String REQUIRED_PROVIDER_TYPE = "OPENAI_COMPATIBLE";

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
      throw invalid("Embedding batch-size 必须在 1 到 20 之间");
    }
    if (embedding.getTimeout() == null || embedding.getTimeout().isZero()
        || embedding.getTimeout().isNegative()) {
      throw invalid("Embedding timeout 必须大于 0");
    }
    ModelInvocationConfig config =
        modelConfigService.requireInvocationConfig(embedding.getModelConfigId());
    if (!REQUIRED_MODEL_ID.equals(config.getModelId())) {
      throw invalid("知识库 Embedding 模型必须为 qwen3.7-text-embedding");
    }
    if (!REQUIRED_PROVIDER_TYPE.equals(config.getProviderType())) {
      throw invalid("千问 Embedding Provider 类型必须为 OPENAI_COMPATIBLE");
    }
    Object dimensions = config.getInvocationParams().get("dimensions");
    if (!(dimensions instanceof Number value)
        || value.intValue() != REQUIRED_VECTOR_DIMENSION) {
      throw invalid("千问 Embedding dimensions 必须为 1536");
    }
    KnowledgeProperties.Rag rag = properties.getRag();
    if (rag.getTopK() < 1 || rag.getTopK() > 100) {
      throw invalid("RAG top-k 必须在 1 到 100 之间");
    }
    if (!Double.isFinite(rag.getMinSimilarity())
        || rag.getMinSimilarity() < -1 || rag.getMinSimilarity() > 1) {
      throw invalid("RAG min-similarity 必须在 -1 到 1 之间");
    }
  }

  private BizException invalid(String message) {
    return new BizException(ErrorCode.PARAM_ERROR, message);
  }
}
