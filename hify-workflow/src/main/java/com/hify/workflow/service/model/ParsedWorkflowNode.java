package com.hify.workflow.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.constant.NodeType;

public record ParsedWorkflowNode(
    String nodeKey,
    String name,
    NodeType nodeType,
    NodeConfigDef config,
    JsonNode normalizedConfig,
    Integer configVersion,
    Integer sortOrder) {
}
