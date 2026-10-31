package com.hify.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.workflow.constant.WorkflowRunStatus;
import com.hify.workflow.entity.WorkflowNode;
import com.hify.workflow.entity.WorkflowNodeRun;
import com.hify.workflow.entity.WorkflowRun;
import com.hify.workflow.mapper.WorkflowNodeRunMapper;
import com.hify.workflow.mapper.WorkflowRunMapper;
import com.hify.workflow.service.WorkflowRunPersistenceService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkflowRunPersistenceServiceImpl implements WorkflowRunPersistenceService {

  private static final int MAX_ERROR_LENGTH = 500;

  private final WorkflowRunMapper workflowRunMapper;
  private final WorkflowNodeRunMapper workflowNodeRunMapper;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager",
      propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public Long startWorkflowRun(Long workflowId, String input) {
    WorkflowRun run = new WorkflowRun();
    run.setWorkflowId(workflowId);
    run.setStatus(WorkflowRunStatus.RUNNING.name());
    run.setInput(input);
    run.setCreatedAt(LocalDateTime.now());
    if (workflowRunMapper.insert(run) != 1) {
      throw new IllegalStateException("创建工作流运行记录失败: workflowId=" + workflowId);
    }
    return run.getId();
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager",
      propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void completeWorkflowRun(Long runId, Object output, long elapsedMs) {
    if (runId == null) {
      return;
    }
    int updated = workflowRunMapper.update(null,
        Wrappers.<WorkflowRun>lambdaUpdate()
            .eq(WorkflowRun::getId, runId)
            .eq(WorkflowRun::getStatus, WorkflowRunStatus.RUNNING.name())
            .set(WorkflowRun::getStatus, WorkflowRunStatus.SUCCESS.name())
            .set(WorkflowRun::getOutput, output == null ? null : String.valueOf(output))
            .set(WorkflowRun::getElapsedMs, elapsedMs)
            .set(WorkflowRun::getFinishedAt, LocalDateTime.now()));
    if (updated != 1) {
      throw new IllegalStateException("工作流运行成功状态更新异常: runId=" + runId);
    }
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager",
      propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void failWorkflowRun(Long runId, String error, long elapsedMs) {
    if (runId == null) {
      return;
    }
    int updated = workflowRunMapper.update(null,
        Wrappers.<WorkflowRun>lambdaUpdate()
            .eq(WorkflowRun::getId, runId)
            .eq(WorkflowRun::getStatus, WorkflowRunStatus.RUNNING.name())
            .set(WorkflowRun::getStatus, WorkflowRunStatus.FAILED.name())
            .set(WorkflowRun::getError, truncateError(error))
            .set(WorkflowRun::getElapsedMs, elapsedMs)
            .set(WorkflowRun::getFinishedAt, LocalDateTime.now()));
    if (updated != 1) {
      throw new IllegalStateException("工作流运行失败状态更新异常: runId=" + runId);
    }
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager",
      propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public Long startNodeRun(Long workflowRunId, WorkflowNode node) {
    if (workflowRunId == null) {
      return null;
    }
    WorkflowNodeRun run = new WorkflowNodeRun();
    run.setWorkflowRunId(workflowRunId);
    run.setNodeKey(node.getNodeKey());
    run.setNodeType(node.getNodeType());
    run.setStatus(WorkflowRunStatus.RUNNING.name());
    run.setCreatedAt(LocalDateTime.now());
    if (workflowNodeRunMapper.insert(run) != 1) {
      throw new IllegalStateException("创建节点运行记录失败: nodeKey=" + node.getNodeKey());
    }
    return run.getId();
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager",
      propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void completeNodeRun(Long nodeRunId, Map<String, Object> outputs, long elapsedMs) {
    if (nodeRunId == null) {
      return;
    }
    WorkflowNodeRun update = new WorkflowNodeRun();
    update.setStatus(WorkflowRunStatus.SUCCESS.name());
    update.setOutputs(objectMapper.valueToTree(outputs));
    update.setElapsedMs(elapsedMs);
    update.setFinishedAt(LocalDateTime.now());
    int updated = workflowNodeRunMapper.update(update,
        Wrappers.<WorkflowNodeRun>lambdaUpdate()
            .eq(WorkflowNodeRun::getId, nodeRunId)
            .eq(WorkflowNodeRun::getStatus, WorkflowRunStatus.RUNNING.name()));
    if (updated != 1) {
      throw new IllegalStateException("节点运行成功状态更新异常: nodeRunId=" + nodeRunId);
    }
  }

  @Override
  @Transactional(transactionManager = "mysqlTransactionManager",
      propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void failNodeRun(Long nodeRunId, String error, long elapsedMs) {
    if (nodeRunId == null) {
      return;
    }
    int updated = workflowNodeRunMapper.update(null,
        Wrappers.<WorkflowNodeRun>lambdaUpdate()
            .eq(WorkflowNodeRun::getId, nodeRunId)
            .eq(WorkflowNodeRun::getStatus, WorkflowRunStatus.RUNNING.name())
            .set(WorkflowNodeRun::getStatus, WorkflowRunStatus.FAILED.name())
            .set(WorkflowNodeRun::getError, truncateError(error))
            .set(WorkflowNodeRun::getElapsedMs, elapsedMs)
            .set(WorkflowNodeRun::getFinishedAt, LocalDateTime.now()));
    if (updated != 1) {
      throw new IllegalStateException("节点运行失败状态更新异常: nodeRunId=" + nodeRunId);
    }
  }

  static String truncateError(String error) {
    if (error == null || error.length() <= MAX_ERROR_LENGTH) {
      return error;
    }
    return error.substring(0, MAX_ERROR_LENGTH);
  }
}
