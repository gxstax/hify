package com.hify.knowledge.processing;

/** Immutable data passed between document chunking, embedding and persistence stages. */
public record ChunkDTO(int chunkIndex, String content, int tokenCount, float[] embedding) {

  public ChunkDTO withEmbedding(float[] value) {
    return new ChunkDTO(chunkIndex, content, tokenCount, value);
  }
}
