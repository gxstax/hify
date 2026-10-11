package com.hify.knowledge.dto;

import com.hify.knowledge.repository.DocumentChunkRow;
import java.time.OffsetDateTime;
import lombok.Data;

/** A document chunk response; embedding vectors are intentionally omitted. */
@Data
public class DocumentChunkResponse {

  private Long id;
  private Long documentId;
  private Integer chunkIndex;
  private String content;
  private Integer tokenCount;
  private OffsetDateTime createdAt;

  public static DocumentChunkResponse from(DocumentChunkRow row) {
    DocumentChunkResponse response = new DocumentChunkResponse();
    response.setId(row.id());
    response.setDocumentId(row.documentId());
    response.setChunkIndex(row.chunkIndex());
    response.setContent(row.content());
    response.setTokenCount(row.tokenCount());
    response.setCreatedAt(row.createdAt());
    return response;
  }
}
