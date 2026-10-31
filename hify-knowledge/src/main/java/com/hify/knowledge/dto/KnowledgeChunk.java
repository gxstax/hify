package com.hify.knowledge.dto;

/** One knowledge chunk returned by semantic search. */
public record KnowledgeChunk(
    long chunkId,
    long knowledgeBaseId,
    long documentId,
    String documentName,
    int chunkIndex,
    String content,
    int tokenCount,
    double similarity) {
}
