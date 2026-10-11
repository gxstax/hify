package com.hify.knowledge.repository;

/** One pgvector document chunk to persist in a batch. */
public record DocumentChunkWrite(
    long knowledgeBaseId,
    long documentId,
    int chunkIndex,
    String content,
    int tokenCount,
    float[] embedding) {
}
