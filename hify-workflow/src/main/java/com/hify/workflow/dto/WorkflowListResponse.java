package com.hify.workflow.dto;

import com.hify.workflow.entity.Workflow;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WorkflowListResponse {

  private Long id;
  private String name;
  private String description;
  private Integer version;
  private Boolean enabled;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static WorkflowListResponse of(Workflow workflow) {
    WorkflowListResponse response = new WorkflowListResponse();
    response.setId(workflow.getId());
    response.setName(workflow.getName());
    response.setDescription(workflow.getDescription() == null ? "" : workflow.getDescription());
    response.setVersion(workflow.getVersion());
    response.setEnabled(workflow.getEnabled());
    response.setCreatedAt(workflow.getCreatedAt());
    response.setUpdatedAt(workflow.getUpdatedAt());
    return response;
  }
}
