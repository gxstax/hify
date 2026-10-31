package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.config.ConditionNodeConfig;
import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class ConditionNodeExecutor implements NodeExecutor {

  @Override
  public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
    try {
      ConditionNodeConfig conditionConfig = ExecutorSupport.requireConfig(
          config, ConditionNodeConfig.class, nodeType());
      boolean result = evaluate(ctx.resolve(conditionConfig.expression()));
      ctx.set(node.getNodeKey(), conditionConfig.outputVariable(), result);
    } catch (Exception e) {
      throw ExecutorSupport.wrap(node.getNodeKey(), e);
    }
  }

  @Override
  public String nodeType() {
    return NodeType.CONDITION.name();
  }

  private boolean evaluate(String expression) {
    String normalized = expression.trim();
    if ("true".equalsIgnoreCase(normalized)) {
      return true;
    }
    if ("false".equalsIgnoreCase(normalized)) {
      return false;
    }

    int equalsIndex = normalized.indexOf("==");
    int notEqualsIndex = normalized.indexOf("!=");
    if ((equalsIndex >= 0) == (notEqualsIndex >= 0)) {
      throw invalidExpression(expression);
    }

    String operator = equalsIndex >= 0 ? "==" : "!=";
    int operatorIndex = equalsIndex >= 0 ? equalsIndex : notEqualsIndex;
    if (normalized.indexOf(operator, operatorIndex + operator.length()) >= 0) {
      throw invalidExpression(expression);
    }

    String left = unquote(normalized.substring(0, operatorIndex).trim());
    String right = unquote(normalized.substring(operatorIndex + operator.length()).trim());
    if (left.isEmpty() || right.isEmpty()) {
      throw invalidExpression(expression);
    }
    boolean equal = left.equals(right);
    return "==".equals(operator) ? equal : !equal;
  }

  private String unquote(String value) {
    if (value.length() >= 2) {
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }

  private BizException invalidExpression(String expression) {
    return new BizException(ErrorCode.PARAM_ERROR, "不支持的条件表达式: " + expression);
  }
}
