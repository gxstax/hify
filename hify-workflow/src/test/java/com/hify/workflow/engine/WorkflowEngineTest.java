package com.hify.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hify.workflow.config.ConditionNodeConfig;
import com.hify.workflow.config.EndNodeConfig;
import com.hify.workflow.config.NodeConfigParser;
import com.hify.workflow.config.StartNodeConfig;
import com.hify.workflow.config.TextNodeConfig;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.executor.ConditionNodeExecutor;
import com.hify.workflow.engine.executor.EndNodeExecutor;
import com.hify.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.workflow.engine.executor.StartNodeExecutor;
import com.hify.workflow.engine.executor.TextNodeExecutor;
import com.hify.workflow.entity.Workflow;
import com.hify.workflow.entity.WorkflowEdge;
import com.hify.workflow.entity.WorkflowNode;
import com.hify.workflow.mapper.WorkflowEdgeMapper;
import com.hify.workflow.mapper.WorkflowMapper;
import com.hify.workflow.mapper.WorkflowNodeMapper;
import com.hify.workflow.service.WorkflowRunPersistenceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowEngineTest {

  @Test
  void shouldExecuteDefaultLinearPathAndPersistSuccessRecordsBestEffort() {
    WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
    WorkflowNodeMapper nodeMapper = mock(WorkflowNodeMapper.class);
    WorkflowEdgeMapper edgeMapper = mock(WorkflowEdgeMapper.class);
    NodeConfigParser parser = mock(NodeConfigParser.class);
    WorkflowRunPersistenceService persistence = mock(WorkflowRunPersistenceService.class);
    NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
        new StartNodeExecutor(), new TextNodeExecutor(), new EndNodeExecutor()));
    WorkflowEngine engine = new WorkflowEngine(workflowMapper, nodeMapper, edgeMapper,
        parser, registry, persistence);

    Workflow workflow = new Workflow();
    workflow.setId(9L);
    workflow.setEnabled(true);
    WorkflowNode start = node(1L, "start", NodeType.START);
    WorkflowNode text = node(2L, "greeting", NodeType.TEXT);
    WorkflowNode end = node(3L, "end", NodeType.END);
    when(workflowMapper.selectById(9L)).thenReturn(workflow);
    when(nodeMapper.selectList(any())).thenReturn(List.of(start, text, end));
    when(edgeMapper.selectList(any())).thenReturn(List.of(
        edge("start", "greeting", "default", 0),
        edge("greeting", "end", "default", 0)));
    when(parser.parseStored(NodeType.START, 1, null, 1L))
        .thenReturn(new StartNodeConfig(List.of()));
    when(parser.parseStored(NodeType.TEXT, 1, null, 2L))
        .thenReturn(new TextNodeConfig("你好，{{start.userMessage}}"));
    when(parser.parseStored(NodeType.END, 1, null, 3L))
        .thenReturn(new EndNodeConfig("greeting.output"));
    when(persistence.startWorkflowRun(9L, "小明")).thenReturn(100L);
    when(persistence.startNodeRun(any(), any())).thenThrow(new IllegalStateException("db down"));

    WorkflowExecutionResult result = engine.execute(9L, "小明");

    assertEquals(100L, result.workflowRunId());
    assertEquals("你好，小明", result.output());
    verify(persistence).completeWorkflowRun(any(), any(), anyLong());
  }

  @Test
  void shouldFollowTrueConditionEdge() {
    assertConditionPath(true, "true path");
  }

  @Test
  void shouldFollowFalseConditionEdge() {
    assertConditionPath(false, "false path");
  }

  private void assertConditionPath(boolean conditionResult, String expectedOutput) {
    WorkflowMapper workflowMapper = mock(WorkflowMapper.class);
    WorkflowNodeMapper nodeMapper = mock(WorkflowNodeMapper.class);
    WorkflowEdgeMapper edgeMapper = mock(WorkflowEdgeMapper.class);
    NodeConfigParser parser = mock(NodeConfigParser.class);
    WorkflowRunPersistenceService persistence = mock(WorkflowRunPersistenceService.class);
    NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(
        new StartNodeExecutor(), new ConditionNodeExecutor(), new TextNodeExecutor(),
        new EndNodeExecutor()));
    WorkflowEngine engine = new WorkflowEngine(workflowMapper, nodeMapper, edgeMapper,
        parser, registry, persistence);

    Workflow workflow = new Workflow();
    workflow.setId(10L);
    workflow.setEnabled(true);
    WorkflowNode start = node(1L, "start", NodeType.START);
    WorkflowNode condition = node(2L, "condition", NodeType.CONDITION);
    WorkflowNode trueText = node(3L, "trueText", NodeType.TEXT);
    WorkflowNode falseText = node(4L, "falseText", NodeType.TEXT);
    WorkflowNode end = node(5L, "end", NodeType.END);
    when(workflowMapper.selectById(10L)).thenReturn(workflow);
    when(nodeMapper.selectList(any())).thenReturn(
        List.of(start, condition, trueText, falseText, end));
    when(edgeMapper.selectList(any())).thenReturn(List.of(
        edge(1L, "start", "condition", "default", 0),
        edge(4L, "condition", "falseText", "false", 20),
        edge(3L, "condition", "trueText", "true", 10),
        edge(5L, "trueText", "end", "default", 0),
        edge(6L, "falseText", "end", "default", 0)));
    when(parser.parseStored(NodeType.START, 1, null, 1L))
        .thenReturn(new StartNodeConfig(List.of()));
    when(parser.parseStored(NodeType.CONDITION, 1, null, 2L))
        .thenReturn(new ConditionNodeConfig(String.valueOf(conditionResult), "result"));
    when(parser.parseStored(NodeType.TEXT, 1, null, 3L))
        .thenReturn(new TextNodeConfig("true path"));
    when(parser.parseStored(NodeType.TEXT, 1, null, 4L))
        .thenReturn(new TextNodeConfig("false path"));
    when(parser.parseStored(NodeType.END, 1, null, 5L))
        .thenReturn(new EndNodeConfig(
            conditionResult ? "trueText.output" : "falseText.output"));

    WorkflowExecutionResult result = engine.execute(10L, "input");

    assertEquals(expectedOutput, result.output());
  }

  private WorkflowNode node(Long id, String key, NodeType type) {
    WorkflowNode node = new WorkflowNode();
    node.setId(id);
    node.setNodeKey(key);
    node.setNodeType(type.name());
    node.setConfigVersion(1);
    return node;
  }

  private WorkflowEdge edge(String source, String target, String port, int priority) {
    return edge(null, source, target, port, priority);
  }

  private WorkflowEdge edge(Long id, String source, String target, String port, int priority) {
    WorkflowEdge edge = new WorkflowEdge();
    edge.setId(id);
    edge.setSourceNodeKey(source);
    edge.setTargetNodeKey(target);
    edge.setSourcePort(port);
    edge.setPriority(priority);
    return edge;
  }
}
