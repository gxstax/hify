package com.hify.knowledge.dto;

import com.hify.knowledge.entity.Document;
import java.time.LocalDateTime;
import lombok.Data;

/** Document metadata and asynchronous processing state. */
@Data
public class DocumentResponse {

  private Long id;
  private Long knowledgeBaseId;
  private String name;
  private String fileType;
  private Long fileSize;
  private String status;
  private String errorMessage;
  private Integer chunkCount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static DocumentResponse from(Document document) {
    DocumentResponse response = new DocumentResponse();
    response.setId(document.getId());
    response.setKnowledgeBaseId(document.getKnowledgeBaseId());
    response.setName(document.getName() == null ? "" : document.getName());
    response.setFileType(document.getFileType() == null ? "" : document.getFileType());
    response.setFileSize(document.getFileSize());
    response.setStatus(document.getStatus() == null ? "" : document.getStatus());
    response.setErrorMessage(document.getErrorMessage() == null ? "" : document.getErrorMessage());
    response.setChunkCount(document.getChunkCount() == null ? 0 : document.getChunkCount());
    response.setCreatedAt(document.getCreatedAt());
    response.setUpdatedAt(document.getUpdatedAt());
    return response;
  }
}
