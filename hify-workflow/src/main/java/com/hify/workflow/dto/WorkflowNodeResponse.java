package com.hify.workflow.dto;

import com.hify.workflow.config.NodeConfig;
import com.hify.workflow.constant.NodeType;
import lombok.Data;

@Data
public class WorkflowNodeResponse {

  private Long id;
  private String nodeKey;
  private String name;
  private NodeType nodeType;
  private NodeConfig config;
  private Integer configVersion;
  private Integer sortOrder;
}
