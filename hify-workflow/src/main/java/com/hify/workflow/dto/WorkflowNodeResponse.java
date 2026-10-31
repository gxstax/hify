package com.hify.workflow.dto;

import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.constant.NodeType;
import lombok.Data;

@Data
public class WorkflowNodeResponse {

  private Long id;
  private String nodeKey;
  private String name;
  private NodeType nodeType;
  private NodeConfigDef config;
  private Integer configVersion;
  private Integer sortOrder;
}
