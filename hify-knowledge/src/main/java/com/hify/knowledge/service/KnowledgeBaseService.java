package com.hify.knowledge.service;

import com.hify.common.dto.PageResult;
import com.hify.knowledge.dto.KnowledgeBaseCreateReq;
import com.hify.knowledge.dto.KnowledgeBaseResponse;
import com.hify.knowledge.dto.KnowledgeBaseUpdateReq;

/** Business facade for knowledge-base management. */
public interface KnowledgeBaseService {

  KnowledgeBaseResponse createKnowledgeBase(KnowledgeBaseCreateReq req);

  PageResult<KnowledgeBaseResponse> listKnowledgeBases(int page, int size, String name);

  KnowledgeBaseResponse getKnowledgeBase(Long id);

  KnowledgeBaseResponse updateKnowledgeBase(Long id, KnowledgeBaseUpdateReq req);

  void deleteKnowledgeBase(Long id);
}
