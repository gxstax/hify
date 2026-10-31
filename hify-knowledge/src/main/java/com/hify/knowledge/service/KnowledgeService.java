package com.hify.knowledge.service;

import com.hify.knowledge.dto.KnowledgeChunk;
import java.util.List;

/** Cross-module facade for knowledge retrieval. */
public interface KnowledgeService {

  List<KnowledgeChunk> searchChunks(Long knowledgeBaseId, String query, int topK);
}
