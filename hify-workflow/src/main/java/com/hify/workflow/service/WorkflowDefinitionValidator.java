package com.hify.workflow.service;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.config.NodeConfig;
import com.hify.workflow.config.NodeConfigParser;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.dto.WorkflowDefinitionReq;
import com.hify.workflow.dto.WorkflowEdgeReq;
import com.hify.workflow.dto.WorkflowNodeReq;
import com.hify.workflow.service.model.ParsedWorkflowNode;
import com.hify.workflow.service.model.ValidatedWorkflowDefinition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowDefinitionValidator {

  private final NodeConfigParser nodeConfigParser;

  public ValidatedWorkflowDefinition validate(WorkflowDefinitionReq request) {
    Set<String> nodeKeys = new HashSet<>();
    List<ParsedWorkflowNode> parsedNodes = request.getNodes().stream()
        .map(node -> parseNode(node, nodeKeys))
        .toList();

    validateNodeTypes(parsedNodes);
    validateEdges(request.getEdges(), nodeKeys, parsedNodes);
    return new ValidatedWorkflowDefinition(parsedNodes, request.getEdges());
  }

  private ParsedWorkflowNode parseNode(WorkflowNodeReq node, Set<String> nodeKeys) {
    if (!nodeKeys.add(node.getNodeKey())) {
      throw new BizException(ErrorCode.PARAM_ERROR,
          "nodeKey 在工作流内必须唯一: " + node.getNodeKey());
    }
    try {
      NodeType nodeType = NodeType.valueOf(node.getNodeType());
      NodeConfig config = nodeConfigParser.parse(
          nodeType, node.getConfigVersion(), node.getConfig());
      return new ParsedWorkflowNode(
          node.getNodeKey(), node.getName(), nodeType, config,
          nodeConfigParser.serialize(config), node.getConfigVersion(), node.getSortOrder());
    } catch (BizException | IllegalArgumentException e) {
      ErrorCode errorCode = e instanceof BizException bizException
          ? bizException.getErrorCode() : ErrorCode.PARAM_ERROR;
      throw new BizException(errorCode,
          "节点 " + node.getNodeKey() + " 配置错误: " + e.getMessage());
    }
  }

  private void validateNodeTypes(List<ParsedWorkflowNode> nodes) {
    long startCount = nodes.stream()
        .filter(node -> node.nodeType() == NodeType.START)
        .count();
    long endCount = nodes.stream()
        .filter(node -> node.nodeType() == NodeType.END)
        .count();
    if (startCount != 1) {
      throw new BizException(ErrorCode.PARAM_ERROR, "工作流必须且只能包含一个 START 节点");
    }
    if (endCount < 1) {
      throw new BizException(ErrorCode.PARAM_ERROR, "工作流至少包含一个 END 节点");
    }
  }

  private void validateEdges(List<WorkflowEdgeReq> edges, Set<String> nodeKeys,
      List<ParsedWorkflowNode> nodes) {
    Set<String> startKeys = keysOfType(nodes, NodeType.START);
    Set<String> endKeys = keysOfType(nodes, NodeType.END);
    for (WorkflowEdgeReq edge : edges) {
      if (!nodeKeys.contains(edge.getSourceNodeKey())) {
        throw new BizException(ErrorCode.PARAM_ERROR,
            "边引用的起始节点不存在: " + edge.getSourceNodeKey());
      }
      if (!nodeKeys.contains(edge.getTargetNodeKey())) {
        throw new BizException(ErrorCode.PARAM_ERROR,
            "边引用的目标节点不存在: " + edge.getTargetNodeKey());
      }
      if (startKeys.contains(edge.getTargetNodeKey())) {
        throw new BizException(ErrorCode.PARAM_ERROR, "START 节点不能有入边");
      }
      if (endKeys.contains(edge.getSourceNodeKey())) {
        throw new BizException(ErrorCode.PARAM_ERROR, "END 节点不能有出边");
      }
    }
  }

  private Set<String> keysOfType(List<ParsedWorkflowNode> nodes, NodeType type) {
    Set<String> keys = new HashSet<>();
    nodes.stream()
        .filter(node -> node.nodeType() == type)
        .map(ParsedWorkflowNode::nodeKey)
        .forEach(keys::add);
    return keys;
  }
}
