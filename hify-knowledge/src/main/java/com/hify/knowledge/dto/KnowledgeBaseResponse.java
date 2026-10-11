package com.hify.knowledge.dto;

import com.hify.knowledge.entity.KnowledgeBase;
import java.time.LocalDateTime;
import lombok.Data;

/** Knowledge-base API response. */
@Data
public class KnowledgeBaseResponse {

  private Long id;
  private String name;
  private String description;
  private Boolean enabled;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
    KnowledgeBaseResponse response = new KnowledgeBaseResponse();
    response.setId(knowledgeBase.getId());
    response.setName(knowledgeBase.getName());
    response.setDescription(knowledgeBase.getDescription() == null
        ? "" : knowledgeBase.getDescription());
    response.setEnabled(knowledgeBase.getEnabled());
    response.setCreatedAt(knowledgeBase.getCreatedAt());
    response.setUpdatedAt(knowledgeBase.getUpdatedAt());
    return response;
  }
}
