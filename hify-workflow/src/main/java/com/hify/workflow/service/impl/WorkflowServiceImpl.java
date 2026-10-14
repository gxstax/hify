package com.hify.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.workflow.config.NodeConfig;
import com.hify.workflow.config.NodeConfigParser;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.dto.WorkflowCreateReq;
import com.hify.workflow.dto.WorkflowDefinitionReq;
import com.hify.workflow.dto.WorkflowDetailResponse;
import com.hify.workflow.dto.WorkflowEdgeReq;
import com.hify.workflow.dto.WorkflowEdgeResponse;
import com.hify.workflow.dto.WorkflowListResponse;
import com.hify.workflow.dto.WorkflowNodeResponse;
import com.hify.workflow.dto.WorkflowUpdateReq;
import com.hify.workflow.entity.Workflow;
import com.hify.workflow.entity.WorkflowEdge;
import com.hify.workflow.entity.WorkflowNode;
import com.hify.workflow.mapper.WorkflowEdgeMapper;
import com.hify.workflow.mapper.WorkflowMapper;
import com.hify.workflow.mapper.WorkflowNodeMapper;
import com.hify.workflow.service.WorkflowDefinitionValidator;
import com.hify.workflow.service.WorkflowService;
import com.hify.workflow.service.model.ParsedWorkflowNode;
import com.hify.workflow.service.model.ValidatedWorkflowDefinition;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

  private static final int BATCH_SIZE = 200;
  private static final String DEFAULT_SOURCE_PORT = "default";

  private final WorkflowMapper workflowMapper;
  private final WorkflowNodeMapper workflowNodeMapper;
  private final WorkflowEdgeMapper workflowEdgeMapper;
  private final WorkflowDefinitionValidator definitionValidator;
  private final NodeConfigParser nodeConfigParser;

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
  public WorkflowDetailResponse createWorkflow(WorkflowCreateReq request) {
    ValidatedWorkflowDefinition definition = definitionValidator.validate(request);
    Workflow workflow = toWorkflow(request);
    requireAffected(workflowMapper.insert(workflow), 1, "创建工作流失败");
    insertDefinition(workflow.getId(), definition);
    return loadDetail(workflow.getId());
  }

  @Override
  public PageResult<WorkflowListResponse> listWorkflows(int page, int pageSize, String keyword,
      Boolean enabled) {
    LambdaQueryWrapper<Workflow> wrapper = Wrappers.<Workflow>lambdaQuery()
        .eq(enabled != null, Workflow::getEnabled, enabled)
        .and(keyword != null && !keyword.isBlank(),
            query -> query.like(Workflow::getName, keyword)
                .or().like(Workflow::getDescription, keyword))
        .orderByDesc(Workflow::getId);
    Page<Workflow> mpPage = PageHelper.toPage(page, pageSize);
    workflowMapper.selectPage(mpPage, wrapper);
    List<WorkflowListResponse> items = mpPage.getRecords().stream()
        .map(WorkflowListResponse::of)
        .toList();
    return new PageResult<>(items, mpPage.getTotal(),
        (int) mpPage.getCurrent(), (int) mpPage.getSize());
  }

  @Override
  public WorkflowDetailResponse getWorkflow(Long id) {
    return loadDetail(id);
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
  public WorkflowDetailResponse updateWorkflow(Long id, WorkflowUpdateReq request) {
    requireWorkflow(id);
    ValidatedWorkflowDefinition definition = definitionValidator.validate(request);

    Workflow update = toWorkflow(request);
    update.setId(id);
    requireAffected(workflowMapper.updateById(update), 1, "更新工作流失败");

    workflowEdgeMapper.delete(Wrappers.<WorkflowEdge>lambdaQuery()
        .eq(WorkflowEdge::getWorkflowId, id));
    workflowNodeMapper.delete(Wrappers.<WorkflowNode>lambdaQuery()
        .eq(WorkflowNode::getWorkflowId, id));
    insertDefinition(id, definition);
    return loadDetail(id);
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager", rollbackFor = Exception.class)
  public void deleteWorkflow(Long id) {
    requireWorkflow(id);
    workflowEdgeMapper.delete(Wrappers.<WorkflowEdge>lambdaQuery()
        .eq(WorkflowEdge::getWorkflowId, id));
    workflowNodeMapper.delete(Wrappers.<WorkflowNode>lambdaQuery()
        .eq(WorkflowNode::getWorkflowId, id));
    requireAffected(workflowMapper.deleteById(id), 1, "删除工作流失败");
  }

  private Workflow toWorkflow(WorkflowDefinitionReq request) {
    Workflow workflow = new Workflow();
    workflow.setName(request.getName());
    workflow.setDescription(request.getDescription() == null ? "" : request.getDescription());
    workflow.setVersion(request.getVersion());
    workflow.setEnabled(request.getEnabled());
    return workflow;
  }

  private void insertDefinition(Long workflowId, ValidatedWorkflowDefinition definition) {
    List<WorkflowNode> nodes = definition.nodes().stream()
        .map(node -> toNode(workflowId, node))
        .toList();
    List<WorkflowEdge> edges = definition.edges().stream()
        .map(edge -> toEdge(workflowId, edge))
        .toList();
    insertNodeBatches(nodes);
    insertEdgeBatches(edges);
  }

  private WorkflowNode toNode(Long workflowId, ParsedWorkflowNode parsed) {
    WorkflowNode node = new WorkflowNode();
    node.setWorkflowId(workflowId);
    node.setNodeKey(parsed.nodeKey());
    node.setName(parsed.name());
    node.setNodeType(parsed.nodeType().name());
    node.setConfig(parsed.normalizedConfig());
    node.setConfigVersion(parsed.configVersion());
    node.setSortOrder(parsed.sortOrder());
    return node;
  }

  private WorkflowEdge toEdge(Long workflowId, WorkflowEdgeReq request) {
    WorkflowEdge edge = new WorkflowEdge();
    edge.setWorkflowId(workflowId);
    edge.setSourceNodeKey(request.getSourceNodeKey());
    edge.setTargetNodeKey(request.getTargetNodeKey());
    edge.setSourcePort(request.getSourcePort() == null || request.getSourcePort().isBlank()
        ? DEFAULT_SOURCE_PORT : request.getSourcePort());
    edge.setPriority(request.getPriority());
    return edge;
  }

  private void insertNodeBatches(List<WorkflowNode> nodes) {
    for (int from = 0; from < nodes.size(); from += BATCH_SIZE) {
      List<WorkflowNode> batch = nodes.subList(from, Math.min(from + BATCH_SIZE, nodes.size()));
      requireAffected(workflowNodeMapper.insertBatch(batch), batch.size(), "批量创建工作流节点失败");
    }
  }

  private void insertEdgeBatches(List<WorkflowEdge> edges) {
    for (int from = 0; from < edges.size(); from += BATCH_SIZE) {
      List<WorkflowEdge> batch = edges.subList(from, Math.min(from + BATCH_SIZE, edges.size()));
      requireAffected(workflowEdgeMapper.insertBatch(batch), batch.size(), "批量创建工作流连接失败");
    }
  }

  private Workflow requireWorkflow(Long id) {
    Workflow workflow = workflowMapper.selectById(id);
    if (workflow == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "工作流不存在: id=" + id);
    }
    return workflow;
  }

  private WorkflowDetailResponse loadDetail(Long id) {
    Workflow workflow = requireWorkflow(id);
    List<WorkflowNode> nodes = workflowNodeMapper.selectList(
        Wrappers.<WorkflowNode>lambdaQuery()
            .eq(WorkflowNode::getWorkflowId, id)
            .orderByAsc(WorkflowNode::getSortOrder)
            .orderByAsc(WorkflowNode::getId));
    List<WorkflowEdge> edges = workflowEdgeMapper.selectList(
        Wrappers.<WorkflowEdge>lambdaQuery()
            .eq(WorkflowEdge::getWorkflowId, id)
            .orderByAsc(WorkflowEdge::getPriority)
            .orderByAsc(WorkflowEdge::getId));

    WorkflowDetailResponse response = new WorkflowDetailResponse();
    response.setId(workflow.getId());
    response.setName(workflow.getName());
    response.setDescription(workflow.getDescription() == null ? "" : workflow.getDescription());
    response.setVersion(workflow.getVersion());
    response.setEnabled(workflow.getEnabled());
    response.setNodes(nodes.stream().map(this::toNodeResponse).toList());
    response.setEdges(edges.stream().map(this::toEdgeResponse).toList());
    response.setCreatedAt(workflow.getCreatedAt());
    response.setUpdatedAt(workflow.getUpdatedAt());
    return response;
  }

  private WorkflowNodeResponse toNodeResponse(WorkflowNode node) {
    NodeType nodeType;
    try {
      nodeType = NodeType.valueOf(node.getNodeType());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "工作流节点类型数据异常");
    }
    NodeConfig config = nodeConfigParser.parseStored(
        nodeType, node.getConfigVersion(), node.getConfig(), node.getId());
    WorkflowNodeResponse response = new WorkflowNodeResponse();
    response.setId(node.getId());
    response.setNodeKey(node.getNodeKey());
    response.setName(node.getName());
    response.setNodeType(nodeType);
    response.setConfig(config);
    response.setConfigVersion(node.getConfigVersion());
    response.setSortOrder(node.getSortOrder());
    return response;
  }

  private WorkflowEdgeResponse toEdgeResponse(WorkflowEdge edge) {
    WorkflowEdgeResponse response = new WorkflowEdgeResponse();
    response.setId(edge.getId());
    response.setSourceNodeKey(edge.getSourceNodeKey());
    response.setTargetNodeKey(edge.getTargetNodeKey());
    response.setSourcePort(edge.getSourcePort());
    response.setPriority(edge.getPriority());
    return response;
  }

  private void requireAffected(int actual, int expected, String message) {
    if (actual != expected) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, message);
    }
  }
}
