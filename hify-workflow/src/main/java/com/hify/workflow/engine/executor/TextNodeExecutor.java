package com.hify.workflow.engine.executor;

import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.config.TextNodeConfig;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class TextNodeExecutor implements NodeExecutor {

  public static final String OUTPUT_VARIABLE = "output";

  @Override
  public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
    try {
      TextNodeConfig textConfig = ExecutorSupport.requireConfig(
          config, TextNodeConfig.class, nodeType());
      ctx.set(node.getNodeKey(), OUTPUT_VARIABLE, ctx.resolve(textConfig.template()));
    } catch (Exception e) {
      throw ExecutorSupport.wrap(node.getNodeKey(), e);
    }
  }

  @Override
  public String nodeType() {
    return NodeType.TEXT.name();
  }
}
