package com.hify.workflow.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class WorkflowDetailResponse {

  private Long id;
  private String name;
  private String description;
  private Integer version;
  private Boolean enabled;
  private List<WorkflowNodeResponse> nodes = new ArrayList<>();
  private List<WorkflowEdgeResponse> edges = new ArrayList<>();
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
