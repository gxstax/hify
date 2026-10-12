package com.hify.knowledge.service;

import com.hify.knowledge.dto.RagRetrievalResult;

/** Cross-module facade for knowledge-base retrieval. */
public interface RagService {

  /**
   * Embed and retrieve on the calling thread. The caller must run this method
   * on llmExecutor because it performs a blocking external model call.
   */
  RagRetrievalResult retrieve(Long knowledgeBaseId, String query);
}
