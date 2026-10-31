package com.hify.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.knowledge.config.KnowledgeProperties;
import com.hify.knowledge.dto.KnowledgeChunk;
import com.hify.knowledge.embedding.EmbeddingClient;
import com.hify.knowledge.entity.Document;
import com.hify.knowledge.entity.KnowledgeBase;
import com.hify.knowledge.mapper.DocumentMapper;
import com.hify.knowledge.mapper.KnowledgeBaseMapper;
import com.hify.knowledge.repository.DocumentChunkMatch;
import com.hify.knowledge.repository.DocumentChunkRepository;
import com.hify.knowledge.service.KnowledgeService;
import com.hify.provider.dto.ModelInvocationConfig;
import com.hify.provider.service.ModelConfigService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Semantic knowledge retrieval shared by RAG and other modules. */
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

  private static final int MAX_TOP_K = 100;

  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final DocumentMapper documentMapper;
  private final DocumentChunkRepository chunkRepository;
  private final EmbeddingClient embeddingClient;
  private final ModelConfigService modelConfigService;
  private final KnowledgeProperties properties;

  @Override
  public List<KnowledgeChunk> searchChunks(Long knowledgeBaseId, String query, int topK) {
    if (query == null || query.isBlank()) {
      throw new BizException(ErrorCode.PARAM_ERROR, "RAG 查询内容不能为空");
    }
    if (topK < 1 || topK > MAX_TOP_K) {
      throw new BizException(ErrorCode.PARAM_ERROR, "topK 必须在 1 到 " + MAX_TOP_K + " 之间");
    }
    KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
    if (knowledgeBase == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在: id=" + knowledgeBaseId);
    }
    if (!Boolean.TRUE.equals(knowledgeBase.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "知识库已禁用: " + knowledgeBase.getName());
    }

    ModelInvocationConfig config = modelConfigService.requireInvocationConfig(
        properties.getEmbedding().getModelConfigId());
    List<float[]> vectors = embeddingClient.embed(List.of(query), config);
    if (vectors.size() != 1) {
      throw new IllegalStateException("RAG query embedding count mismatch");
    }

    List<DocumentChunkMatch> matches = chunkRepository.searchSimilar(
        List.of(knowledgeBaseId), vectors.getFirst(), topK,
        properties.getRag().getMinSimilarity());
    if (matches.isEmpty()) {
      return List.of();
    }

    Map<Long, Document> documents = documentMapper.selectList(
            Wrappers.<Document>lambdaQuery()
                .in(Document::getId, matches.stream().map(DocumentChunkMatch::documentId).toList()))
        .stream()
        .collect(Collectors.toMap(Document::getId, Function.identity()));

    return matches.stream().map(match -> {
      Document document = documents.get(match.documentId());
      String documentName = document == null || document.getName() == null
          ? "文档 #" + match.documentId() : document.getName();
      return new KnowledgeChunk(match.id(), match.knowledgeBaseId(), match.documentId(),
          documentName, match.chunkIndex(), match.content(), match.tokenCount(),
          match.similarity());
    }).toList();
  }
}
