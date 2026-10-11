package com.hify.knowledge.repository;

import java.time.OffsetDateTime;

/** One persisted chunk row without its embedding vector. */
public record DocumentChunkRow(
    long id,
    long documentId,
    int chunkIndex,
    String content,
    int tokenCount,
    OffsetDateTime createdAt) {
}
