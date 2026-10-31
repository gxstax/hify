package com.hify.workflow.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hify.common.exception.BizException;
import com.hify.workflow.config.ConditionNodeConfig;
import com.hify.workflow.config.EndNodeConfig;
import com.hify.workflow.config.NodeConfigParser;
import com.hify.workflow.config.StartNodeConfig;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.dto.WorkflowCreateReq;
import com.hify.workflow.dto.WorkflowEdgeReq;
import com.hify.workflow.dto.WorkflowNodeReq;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionValidatorTest {

  private WorkflowDefinitionValidator validator;

  @BeforeEach
  void setUp() {
    NodeConfigParser parser = mock(NodeConfigParser.class);
    when(parser.parse(eq(NodeType.START), anyInt(), any()))
        .thenReturn(new StartNodeConfig(List.of()));
    when(parser.parse(eq(NodeType.CONDITION), anyInt(), any()))
        .thenReturn(new ConditionNodeConfig("true", "result"));
    when(parser.parse(eq(NodeType.END), anyInt(), any()))
        .thenReturn(new EndNodeConfig("condition.result"));
    validator = new WorkflowDefinitionValidator(parser);
  }

  @Test
  void shouldAcceptUniqueTrueAndFalseConditionPorts() {
    assertDoesNotThrow(() -> validator.validate(request(List.of(
        edge("start", "condition", "default"),
        edge("condition", "endTrue", "true"),
        edge("condition", "endFalse", "false")))));
  }

  @Test
  void shouldRejectUnsupportedConditionPort() {
    assertThrows(BizException.class, () -> validator.validate(request(List.of(
        edge("start", "condition", "default"),
        edge("condition", "endTrue", "yes"),
        edge("condition", "endFalse", "false")))));
  }

  @Test
  void shouldRejectDuplicateConditionPort() {
    assertThrows(BizException.class, () -> validator.validate(request(List.of(
        edge("start", "condition", "default"),
        edge("condition", "endTrue", "true"),
        edge("condition", "endFalse", "true"),
        edge("condition", "endFalse", "false")))));
  }

  @Test
  void shouldRequireBothConditionPorts() {
    assertThrows(BizException.class, () -> validator.validate(request(List.of(
        edge("start", "condition", "default"),
        edge("condition", "endTrue", "true")))));
  }

  private WorkflowCreateReq request(List<WorkflowEdgeReq> edges) {
    WorkflowCreateReq request = new WorkflowCreateReq();
    request.setNodes(List.of(
        node("start", NodeType.START),
        node("condition", NodeType.CONDITION),
        node("endTrue", NodeType.END),
        node("endFalse", NodeType.END)));
    request.setEdges(edges);
    return request;
  }

  private WorkflowNodeReq node(String key, NodeType type) {
    WorkflowNodeReq node = new WorkflowNodeReq();
    node.setNodeKey(key);
    node.setName(key);
    node.setNodeType(type.name());
    node.setConfigVersion(1);
    node.setConfig(JsonNodeFactory.instance.objectNode());
    node.setSortOrder(0);
    return node;
  }

  private WorkflowEdgeReq edge(String source, String target, String port) {
    WorkflowEdgeReq edge = new WorkflowEdgeReq();
    edge.setSourceNodeKey(source);
    edge.setTargetNodeKey(target);
    edge.setSourcePort(port);
    edge.setPriority(0);
    return edge;
  }
}
