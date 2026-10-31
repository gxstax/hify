package com.hify.workflow.engine.executor;

import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.config.StartNodeConfig;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class StartNodeExecutor implements NodeExecutor {

  @Override
  public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
    ExecutorSupport.requireConfig(config, StartNodeConfig.class, nodeType());
  }

  @Override
  public String nodeType() {
    return NodeType.START.name();
  }
}
