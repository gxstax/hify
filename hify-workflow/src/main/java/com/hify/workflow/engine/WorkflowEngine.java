package com.hify.workflow.engine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.config.ConditionNodeConfig;
import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.config.NodeConfigParser;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.executor.NodeExecutor;
import com.hify.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.workflow.entity.Workflow;
import com.hify.workflow.entity.WorkflowEdge;
import com.hify.workflow.entity.WorkflowNode;
import com.hify.workflow.mapper.WorkflowEdgeMapper;
import com.hify.workflow.mapper.WorkflowMapper;
import com.hify.workflow.mapper.WorkflowNodeMapper;
import com.hify.workflow.service.WorkflowRunPersistenceService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

  private static final String DEFAULT_PORT = "default";
  private static final int MAX_EXECUTED_NODES = 50;

  private final WorkflowMapper workflowMapper;
  private final WorkflowNodeMapper workflowNodeMapper;
  private final WorkflowEdgeMapper workflowEdgeMapper;
  private final NodeConfigParser nodeConfigParser;
  private final NodeExecutorRegistry nodeExecutorRegistry;
  private final WorkflowRunPersistenceService persistenceService;

  public WorkflowExecutionResult execute(Long workflowId, String userMessage) {
    Workflow workflow = workflowMapper.selectById(workflowId);
    if (workflow == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在");
    }
    if (!Boolean.TRUE.equals(workflow.getEnabled())) {
      throw new BizException(ErrorCode.PARAM_ERROR, "工作流未启用");
    }

    List<WorkflowNode> nodes = workflowNodeMapper.selectList(
        Wrappers.<WorkflowNode>lambdaQuery()
            .eq(WorkflowNode::getWorkflowId, workflowId)
            .orderByAsc(WorkflowNode::getSortOrder, WorkflowNode::getId));
    List<WorkflowEdge> edges = workflowEdgeMapper.selectList(
        Wrappers.<WorkflowEdge>lambdaQuery()
            .eq(WorkflowEdge::getWorkflowId, workflowId)
            .orderByAsc(WorkflowEdge::getSourceNodeKey, WorkflowEdge::getPriority,
                WorkflowEdge::getId));

    long workflowStartedAt = System.nanoTime();
    Long runId = tryStartWorkflowRun(workflowId, userMessage);
    try {
      if (nodes == null) {
        throw new BizException(ErrorCode.SYSTEM_ERROR, "工作流节点数据为空");
      }
      if (edges == null) {
        throw new BizException(ErrorCode.SYSTEM_ERROR, "工作流边数据为空");
      }
      Map<String, WorkflowNode> nodeByKey = indexNodes(nodes);
      WorkflowNode current = findStartNode(nodes);
      Map<String, List<WorkflowEdge>> edgesBySource = indexEdges(edges);
      ExecutionContext context = new ExecutionContext(runId, userMessage);
      Object output;
      int executedNodes = 0;

      while (true) {
        if (executedNodes >= MAX_EXECUTED_NODES) {
          throw new BizException(ErrorCode.PARAM_ERROR,
              "工作流执行节点数不能超过 " + MAX_EXECUTED_NODES);
        }
        executedNodes++;
        long nodeStartedAt = System.nanoTime();
        Long nodeRunId = tryStartNodeRun(runId, current);
        NodeType nodeType;
        NodeConfigDef config;
        try {
          nodeType = parseNodeType(current);
          config = nodeConfigParser.parseStored(
              nodeType, current.getConfigVersion(), current.getConfig(), current.getId());
          NodeExecutor executor = nodeExecutorRegistry.get(current.getNodeType());
          executor.execute(current, config, context);
        } catch (RuntimeException e) {
          BizException executionFailure = asBizException(e);
          tryFailNodeRun(nodeRunId, executionFailure, elapsedMs(nodeStartedAt));
          throw executionFailure;
        }
        tryCompleteNodeRun(nodeRunId, context, elapsedMs(nodeStartedAt));

        if (nodeType == NodeType.END) {
          output = context.get(current.getNodeKey(), "output");
          break;
        }
        WorkflowEdge edge = selectNextEdge(current, nodeType, config, context, edgesBySource);
        current = nodeByKey.get(edge.getTargetNodeKey());
        if (current == null) {
          throw new BizException(ErrorCode.PARAM_ERROR,
              "边指向不存在的节点: " + edge.getTargetNodeKey());
        }
      }

      String finalOutput = output == null ? "" : String.valueOf(output);
      tryCompleteWorkflowRun(runId, finalOutput, elapsedMs(workflowStartedAt));
      return new WorkflowExecutionResult(runId, finalOutput);
    } catch (RuntimeException e) {
      BizException failure = asBizException(e);
      tryFailWorkflowRun(runId, failure, elapsedMs(workflowStartedAt));
      throw failure;
    }
  }

  private Map<String, WorkflowNode> indexNodes(List<WorkflowNode> nodes) {
    Map<String, WorkflowNode> result = new HashMap<>();
    for (WorkflowNode node : nodes) {
      if (result.putIfAbsent(node.getNodeKey(), node) != null) {
        throw new BizException(ErrorCode.PARAM_ERROR, "工作流节点 key 重复: " + node.getNodeKey());
      }
    }
    return result;
  }

  private WorkflowNode findStartNode(List<WorkflowNode> nodes) {
    List<WorkflowNode> startNodes = nodes.stream()
        .filter(node -> parseNodeType(node) == NodeType.START)
        .toList();
    if (startNodes.size() != 1) {
      throw new BizException(ErrorCode.PARAM_ERROR, "工作流必须且只能有一个 START 节点");
    }
    return startNodes.get(0);
  }

  private Map<String, List<WorkflowEdge>> indexEdges(List<WorkflowEdge> edges) {
    Map<String, List<WorkflowEdge>> result = new HashMap<>();
    for (WorkflowEdge edge : edges) {
      result.computeIfAbsent(edge.getSourceNodeKey(), ignored -> new ArrayList<>()).add(edge);
    }
    Comparator<WorkflowEdge> comparator = Comparator
        .comparing(WorkflowEdge::getPriority)
        .thenComparing(WorkflowEdge::getId);
    result.values().forEach(sourceEdges -> sourceEdges.sort(comparator));
    return result;
  }

  private WorkflowEdge selectNextEdge(WorkflowNode current, NodeType nodeType,
      NodeConfigDef config, ExecutionContext context,
      Map<String, List<WorkflowEdge>> edgesBySource) {
    List<WorkflowEdge> sourceEdges = edgesBySource.getOrDefault(current.getNodeKey(), List.of());
    String selectedPort = DEFAULT_PORT;
    if (nodeType == NodeType.CONDITION) {
      ConditionNodeConfig conditionConfig = (ConditionNodeConfig) config;
      Object conditionResult = context.get(current.getNodeKey(), conditionConfig.outputVariable());
      if (conditionResult instanceof Boolean booleanResult) {
        selectedPort = booleanResult.toString();
      } else {
        throw new BizException(ErrorCode.PARAM_ERROR,
            "CONDITION 节点输出不是 Boolean: " + current.getNodeKey());
      }
      String conditionPort = selectedPort;
      return sourceEdges.stream()
          .filter(edge -> conditionPort.equals(edge.getSourcePort()))
          .findFirst()
          .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR,
              "节点没有匹配的出边: " + current.getNodeKey() + "." + conditionPort));
    }
    return sourceEdges.stream()
        .filter(edge -> DEFAULT_PORT.equals(edge.getSourcePort()))
        .findFirst()
        .or(() -> sourceEdges.stream().findFirst())
        .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR,
            "节点没有匹配的出边: " + current.getNodeKey()));
  }

  private NodeType parseNodeType(WorkflowNode node) {
    try {
      return NodeType.fromExternalValue(node.getNodeType());
    } catch (IllegalArgumentException e) {
      throw new BizException(ErrorCode.SYSTEM_ERROR,
          "工作流节点类型数据异常: " + node.getNodeKey(), e);
    }
  }

  private Long tryStartWorkflowRun(Long workflowId, String input) {
    try {
      return persistenceService.startWorkflowRun(workflowId, input);
    } catch (RuntimeException e) {
      log.warn("Failed to persist workflow run start: workflowId={}", workflowId, e);
      return null;
    }
  }

  private Long tryStartNodeRun(Long runId, WorkflowNode node) {
    try {
      return persistenceService.startNodeRun(runId, node);
    } catch (RuntimeException e) {
      log.warn("Failed to persist node run start: workflowRunId={}, nodeKey={}",
          runId, node.getNodeKey(), e);
      return null;
    }
  }

  private void tryCompleteNodeRun(Long nodeRunId, ExecutionContext context, long elapsedMs) {
    try {
      persistenceService.completeNodeRun(nodeRunId, context.snapshot(), elapsedMs);
    } catch (RuntimeException e) {
      log.warn("Failed to persist node run completion: nodeRunId={}", nodeRunId, e);
    }
  }

  private void tryCompleteWorkflowRun(Long runId, Object output, long elapsedMs) {
    try {
      persistenceService.completeWorkflowRun(runId, output, elapsedMs);
    } catch (RuntimeException e) {
      log.warn("Failed to persist workflow run completion: workflowRunId={}", runId, e);
    }
  }

  private void tryFailNodeRun(Long nodeRunId, BizException failure, long elapsedMs) {
    try {
      persistenceService.failNodeRun(nodeRunId, failure.getMessage(), elapsedMs);
    } catch (RuntimeException persistenceFailure) {
      log.warn("Failed to persist node run failure: nodeRunId={}", nodeRunId,
          persistenceFailure);
      failure.addSuppressed(persistenceFailure);
    }
  }

  private void tryFailWorkflowRun(Long runId, BizException failure, long elapsedMs) {
    try {
      persistenceService.failWorkflowRun(runId, failure.getMessage(), elapsedMs);
    } catch (RuntimeException persistenceFailure) {
      log.warn("Failed to persist workflow run failure: workflowRunId={}", runId,
          persistenceFailure);
      failure.addSuppressed(persistenceFailure);
    }
  }

  private BizException asBizException(RuntimeException failure) {
    if (failure instanceof BizException bizException) {
      return bizException;
    }
    return new BizException(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getMessage(), failure);
  }

  private long elapsedMs(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }
}
