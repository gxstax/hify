package com.hify.workflow.engine.executor;

import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;

public interface NodeExecutor {

  void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx);

  String nodeType();
}
