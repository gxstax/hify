package com.hify.workflow.service.model;

import com.hify.workflow.dto.WorkflowEdgeReq;
import java.util.List;

public record ValidatedWorkflowDefinition(
    List<ParsedWorkflowNode> nodes,
    List<WorkflowEdgeReq> edges) {

  public ValidatedWorkflowDefinition {
    nodes = List.copyOf(nodes);
    edges = List.copyOf(edges);
  }
}
