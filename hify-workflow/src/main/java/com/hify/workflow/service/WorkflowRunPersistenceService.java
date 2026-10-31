package com.hify.workflow.service;

import com.hify.workflow.entity.WorkflowNode;
import java.util.Map;

public interface WorkflowRunPersistenceService {

  Long startWorkflowRun(Long workflowId, String input);

  void completeWorkflowRun(Long runId, Object output, long elapsedMs);

  void failWorkflowRun(Long runId, String error, long elapsedMs);

  Long startNodeRun(Long workflowRunId, WorkflowNode node);

  void completeNodeRun(Long nodeRunId, Map<String, Object> outputs, long elapsedMs);

  void failNodeRun(Long nodeRunId, String error, long elapsedMs);
}
