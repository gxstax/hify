package com.hify.workflow.engine.executor;

import com.hify.provider.service.LlmInvocationService;
import com.hify.workflow.config.LlmNodeConfig;
import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

  private final LlmInvocationService llmInvocationService;

  @Override
  public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
    try {
      LlmNodeConfig llmConfig = ExecutorSupport.requireConfig(
          config, LlmNodeConfig.class, nodeType());
      String prompt = ctx.resolve(llmConfig.prompt());
      String content = llmInvocationService.invoke(llmConfig.modelConfigId(), prompt);
      ctx.set(node.getNodeKey(), llmConfig.outputVariable(), content);
    } catch (Exception e) {
      throw ExecutorSupport.wrap(node.getNodeKey(), e);
    }
  }

  @Override
  public String nodeType() {
    return NodeType.LLM.name();
  }
}
