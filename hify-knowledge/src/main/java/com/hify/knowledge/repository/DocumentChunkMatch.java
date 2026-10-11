package com.hify.knowledge.repository;

/** One document chunk returned by cosine-similarity retrieval. */
public record DocumentChunkMatch(
    long id,
    long knowledgeBaseId,
    long documentId,
    int chunkIndex,
    String content,
    int tokenCount,
    double similarity) {
}
