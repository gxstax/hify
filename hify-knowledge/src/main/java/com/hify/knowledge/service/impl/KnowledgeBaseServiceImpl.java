package com.hify.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.agent.service.AgentService;
import com.hify.common.dto.PageResult;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.knowledge.dto.KnowledgeBaseCreateReq;
import com.hify.knowledge.dto.KnowledgeBaseResponse;
import com.hify.knowledge.dto.KnowledgeBaseUpdateReq;
import com.hify.knowledge.entity.Document;
import com.hify.knowledge.entity.KnowledgeBase;
import com.hify.knowledge.mapper.DocumentMapper;
import com.hify.knowledge.mapper.KnowledgeBaseMapper;
import com.hify.knowledge.repository.DocumentChunkRepository;
import com.hify.knowledge.service.KnowledgeBaseService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Knowledge-base CRUD and cross-database cascade deletion. */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

  private final KnowledgeBaseMapper knowledgeBaseMapper;
  private final DocumentMapper documentMapper;
  private final DocumentChunkRepository documentChunkRepository;
  private final AgentService agentService;

  @Override
  public KnowledgeBaseResponse createKnowledgeBase(KnowledgeBaseCreateReq req) {
    ensureNameFree(req.getName(), null);

    KnowledgeBase knowledgeBase = new KnowledgeBase();
    knowledgeBase.setName(req.getName());
    knowledgeBase.setDescription(normalizeDescription(req.getDescription()));
    knowledgeBase.setEnabled(Boolean.TRUE);
    knowledgeBaseMapper.insert(knowledgeBase);
    return KnowledgeBaseResponse.from(knowledgeBase);
  }

  @Override
  public PageResult<KnowledgeBaseResponse> listKnowledgeBases(int page, int size, String name) {
    Page<KnowledgeBase> mpPage = PageHelper.toPage(page, size);
    knowledgeBaseMapper.selectPage(mpPage, Wrappers.<KnowledgeBase>lambdaQuery()
        .like(name != null && !name.isBlank(), KnowledgeBase::getName, name)
        .orderByDesc(KnowledgeBase::getId));

    Map<Long, Long> documentCounts = mapDocumentCounts(mpPage.getRecords().stream()
        .map(KnowledgeBase::getId)
        .toList());
    List<KnowledgeBaseResponse> list = mpPage.getRecords().stream()
        .map(item -> KnowledgeBaseResponse.from(item,
            documentCounts.getOrDefault(item.getId(), 0L)))
        .toList();
    return new PageResult<>(list, mpPage.getTotal(), (int) mpPage.getCurrent(),
        (int) mpPage.getSize());
  }

  @Override
  public KnowledgeBaseResponse getKnowledgeBase(Long id) {
    KnowledgeBase knowledgeBase = requireKnowledgeBase(id);
    return KnowledgeBaseResponse.from(knowledgeBase, documentCount(id));
  }

  @Override
  public KnowledgeBaseResponse updateKnowledgeBase(Long id, KnowledgeBaseUpdateReq req) {
    KnowledgeBase knowledgeBase = requireKnowledgeBase(id);
    ensureNameFree(req.getName(), id);

    knowledgeBase.setName(req.getName());
    knowledgeBase.setDescription(normalizeDescription(req.getDescription()));
    knowledgeBase.setEnabled(req.getEnabled());
    knowledgeBaseMapper.updateById(knowledgeBase);
    return KnowledgeBaseResponse.from(knowledgeBase, documentCount(id));
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager")
  public void deleteKnowledgeBase(Long id) {
    requireKnowledgeBase(id);
    if (agentService.existsByKnowledgeBaseId(id)) {
      throw new BizException(ErrorCode.PARAM_ERROR, "知识库已被 Agent 绑定，请先解除绑定");
    }

    // No XA is used. Delete vectors first so a removed knowledge base can never
    // leave active chunks behind. If the later MySQL commit fails, retrying this
    // operation is safe because the PostgreSQL update is idempotent.
    documentChunkRepository.logicalDeleteByKnowledgeBaseId(id);
    documentMapper.delete(Wrappers.<Document>lambdaQuery()
        .eq(Document::getKnowledgeBaseId, id));
    knowledgeBaseMapper.deleteById(id);
  }

  private long documentCount(Long knowledgeBaseId) {
    return mapDocumentCounts(List.of(knowledgeBaseId)).getOrDefault(knowledgeBaseId, 0L);
  }

  private Map<Long, Long> mapDocumentCounts(Collection<Long> knowledgeBaseIds) {
    if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
      return Map.of();
    }
    List<Map<String, Object>> rows = documentMapper.selectMaps(
        new QueryWrapper<Document>()
            .select("knowledge_base_id", "COUNT(*) AS document_count")
            .in("knowledge_base_id", knowledgeBaseIds)
            .groupBy("knowledge_base_id"));
    return rows.stream().collect(Collectors.toMap(
        row -> ((Number) row.get("knowledge_base_id")).longValue(),
        row -> ((Number) row.get("document_count")).longValue(),
        (left, right) -> left));
  }

  private KnowledgeBase requireKnowledgeBase(Long id) {
    KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
    if (knowledgeBase == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在: id=" + id);
    }
    return knowledgeBase;
  }

  private void ensureNameFree(String name, Long excludeId) {
    Long count = knowledgeBaseMapper.selectCount(Wrappers.<KnowledgeBase>lambdaQuery()
        .eq(KnowledgeBase::getName, name)
        .ne(excludeId != null, KnowledgeBase::getId, excludeId));
    if (count != null && count > 0) {
      throw new BizException(ErrorCode.PARAM_ERROR, "知识库名称已存在: " + name);
    }
  }

  private String normalizeDescription(String description) {
    return description == null ? "" : description;
  }
}
