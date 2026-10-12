package com.hify.knowledge.dto;

/** Source metadata for one chunk injected into a RAG response. */
public record RagCitation(
    long chunkId,
    long knowledgeBaseId,
    long documentId,
    String documentName,
    int chunkIndex,
    double similarity) {
}
