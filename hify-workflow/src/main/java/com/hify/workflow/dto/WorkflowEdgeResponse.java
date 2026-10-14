package com.hify.workflow.dto;

import lombok.Data;

@Data
public class WorkflowEdgeResponse {

  private Long id;
  private String sourceNodeKey;
  private String targetNodeKey;
  private String sourcePort;
  private Integer priority;
}
