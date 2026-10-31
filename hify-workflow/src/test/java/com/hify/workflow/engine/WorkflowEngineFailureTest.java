package com.hify.workflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.config.NodeConfigParser;
import com.hify.workflow.config.StartNodeConfig;
import com.hify.workflow.config.TextNodeConfig;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.executor.NodeExecutor;
import com.hify.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.workflow.engine.executor.StartNodeExecutor;
import com.hify.workflow.entity.Workflow;
import com.hify.workflow.entity.WorkflowEdge;
import com.hify.workflow.entity.WorkflowNode;
import com.hify.workflow.mapper.WorkflowEdgeMapper;
import com.hify.workflow.mapper.WorkflowMapper;
import com.hify.workflow.mapper.WorkflowNodeMapper;
import com.hify.workflow.service.WorkflowRunPersistenceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowEngineFailureTest {

  private WorkflowMapper workflowMapper;
  private WorkflowNodeMapper nodeMapper;
  private WorkflowEdgeMapper edgeMapper;
  private NodeConfigParser parser;
  private WorkflowRunPersistenceService persistence;

  @BeforeEach
  void setUp() {
    workflowMapper = mock(WorkflowMapper.class);
    nodeMapper = mock(WorkflowNodeMapper.class);
    edgeMapper = mock(WorkflowEdgeMapper.class);
    parser = mock(NodeConfigParser.class);
    persistence = mock(WorkflowRunPersistenceService.class);
    Workflow workflow = new Workflow();
    workflow.setId(1L);
    workflow.setEnabled(true);
    when(workflowMapper.selectById(1L)).thenReturn(workflow);
    when(persistence.startWorkflowRun(1L, "input")).thenReturn(10L);
  }

  @Test
  void shouldPreserveBizExceptionAndPersistNodeAndWorkflowFailure() {
    BizException original = new BizException(ErrorCode.PARAM_ERROR, "executor failed");
    WorkflowEngine engine = engineWith(new StartNodeExecutor(), failingTextExecutor(original));
    prepareExecutorFailureGraph();
    when(persistence.startNodeRun(anyLong(), any())).thenReturn(20L, 21L);

    BizException thrown = assertThrows(BizException.class, () -> engine.execute(1L, "input"));

    assertSame(original, thrown);
    verify(persistence).failNodeRun(eq(21L), eq("executor failed"), anyLong());
    verify(persistence).failWorkflowRun(eq(10L), eq("executor failed"), anyLong());
  }

  @Test
  void shouldWrapOrdinaryExecutorExceptionAndSuppressPersistenceFailures() {
    IllegalStateException original = new IllegalStateException("boom");
    WorkflowEngine engine = engineWith(new StartNodeExecutor(), failingTextExecutor(original));
    prepareExecutorFailureGraph();
    when(persistence.startNodeRun(anyLong(), any())).thenReturn(20L, 21L);
    doThrow(new IllegalStateException("node record failed"))
        .when(persistence).failNodeRun(anyLong(), any(), anyLong());
    doThrow(new IllegalStateException("workflow record failed"))
        .when(persistence).failWorkflowRun(anyLong(), any(), anyLong());

    BizException thrown = assertThrows(BizException.class, () -> engine.execute(1L, "input"));

    assertEquals(ErrorCode.SYSTEM_ERROR, thrown.getErrorCode());
    assertSame(original, thrown.getCause());
    assertEquals(2, thrown.getSuppressed().length);
  }

  @Test
  void shouldFailWorkflowForDanglingTarget() {
    WorkflowNode start = node(1L, "start", NodeType.START);
    when(nodeMapper.selectList(any())).thenReturn(List.of(start));
    when(edgeMapper.selectList(any())).thenReturn(List.of(edge("start", "missing")));
    when(parser.parseStored(any(), any(), any(), any())).thenReturn(new StartNodeConfig(List.of()));

    BizException thrown = assertThrows(BizException.class,
        () -> engineWith(new StartNodeExecutor()).execute(1L, "input"));

    assertTrue(thrown.getMessage().contains("不存在的节点"));
    verify(persistence).failWorkflowRun(anyLong(), any(), anyLong());
  }

  @Test
  void shouldRejectMissingAndMultipleStartNodes() {
    when(edgeMapper.selectList(any())).thenReturn(List.of());
    when(nodeMapper.selectList(any())).thenReturn(List.of(node(2L, "text", NodeType.TEXT)));
    assertThrows(BizException.class,
        () -> engineWith(new StartNodeExecutor()).execute(1L, "input"));

    when(nodeMapper.selectList(any())).thenReturn(List.of(
        node(1L, "start1", NodeType.START), node(2L, "start2", NodeType.START)));
    assertThrows(BizException.class,
        () -> engineWith(new StartNodeExecutor()).execute(1L, "input"));

    verify(persistence, times(2)).failWorkflowRun(anyLong(), any(), anyLong());
  }

  @Test
  void shouldRejectNodeWithoutOutgoingEdge() {
    WorkflowNode start = node(1L, "start", NodeType.START);
    when(nodeMapper.selectList(any())).thenReturn(List.of(start));
    when(edgeMapper.selectList(any())).thenReturn(List.of());
    when(parser.parseStored(any(), any(), any(), any())).thenReturn(new StartNodeConfig(List.of()));

    BizException thrown = assertThrows(BizException.class,
        () -> engineWith(new StartNodeExecutor()).execute(1L, "input"));

    assertTrue(thrown.getMessage().contains("没有匹配的出边"));
    verify(persistence).failWorkflowRun(anyLong(), any(), anyLong());
  }

  @Test
  void shouldStopBeforeExecutingFiftyFirstNode() {
    WorkflowNode start = node(1L, "start", NodeType.START);
    when(nodeMapper.selectList(any())).thenReturn(List.of(start));
    when(edgeMapper.selectList(any())).thenReturn(List.of(edge("start", "start")));
    when(parser.parseStored(any(), any(), any(), any())).thenReturn(new StartNodeConfig(List.of()));
    WorkflowEngine engine = engineWith(new StartNodeExecutor());

    BizException thrown = assertThrows(BizException.class, () -> engine.execute(1L, "input"));

    assertTrue(thrown.getMessage().contains("50"));
    verify(persistence, times(50)).startNodeRun(anyLong(), any());
    verify(persistence).failWorkflowRun(anyLong(), any(), anyLong());
  }

  private void prepareExecutorFailureGraph() {
    WorkflowNode start = node(1L, "start", NodeType.START);
    WorkflowNode text = node(2L, "text", NodeType.TEXT);
    when(nodeMapper.selectList(any())).thenReturn(List.of(start, text));
    when(edgeMapper.selectList(any())).thenReturn(List.of(edge("start", "text")));
    when(parser.parseStored(NodeType.START, 1, null, 1L))
        .thenReturn(new StartNodeConfig(List.of()));
    when(parser.parseStored(NodeType.TEXT, 1, null, 2L))
        .thenReturn(new TextNodeConfig("ignored"));
  }

  private NodeExecutor failingTextExecutor(RuntimeException failure) {
    return new NodeExecutor() {
      @Override
      public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        throw failure;
      }

      @Override
      public String nodeType() {
        return NodeType.TEXT.name();
      }
    };
  }

  private WorkflowEngine engineWith(NodeExecutor... executors) {
    return new WorkflowEngine(workflowMapper, nodeMapper, edgeMapper, parser,
        new NodeExecutorRegistry(List.of(executors)), persistence);
  }

  private WorkflowNode node(Long id, String key, NodeType type) {
    WorkflowNode node = new WorkflowNode();
    node.setId(id);
    node.setNodeKey(key);
    node.setNodeType(type.name());
    node.setConfigVersion(1);
    return node;
  }

  private WorkflowEdge edge(String source, String target) {
    WorkflowEdge edge = new WorkflowEdge();
    edge.setSourceNodeKey(source);
    edge.setTargetNodeKey(target);
    edge.setSourcePort("default");
    edge.setPriority(0);
    return edge;
  }
}
