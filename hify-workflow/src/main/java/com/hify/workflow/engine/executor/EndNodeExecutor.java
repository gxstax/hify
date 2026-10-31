package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.config.EndNodeConfig;
import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class EndNodeExecutor implements NodeExecutor {

  public static final String OUTPUT_VARIABLE = "output";

  @Override
  public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
    try {
      EndNodeConfig endConfig = ExecutorSupport.requireConfig(
          config, EndNodeConfig.class, nodeType());
      String outputVariable = normalizeOutputVariable(endConfig.outputVariable());
      Object output = ctx.get(outputVariable);
      if (output == null) {
        throw new BizException(ErrorCode.PARAM_ERROR,
            "END 节点输出变量不存在: " + outputVariable);
      }
      ctx.set(node.getNodeKey(), OUTPUT_VARIABLE, String.valueOf(output));
    } catch (Exception e) {
      throw ExecutorSupport.wrap(node.getNodeKey(), e);
    }
  }

  @Override
  public String nodeType() {
    return NodeType.END.name();
  }

  private String normalizeOutputVariable(String outputVariable) {
    if (outputVariable.startsWith("{{") && outputVariable.endsWith("}}")) {
      return outputVariable.substring(2, outputVariable.length() - 2);
    }
    return outputVariable;
  }
}
