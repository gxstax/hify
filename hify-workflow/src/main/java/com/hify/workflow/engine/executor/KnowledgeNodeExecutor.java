package com.hify.workflow.engine.executor;

import com.hify.knowledge.dto.KnowledgeChunk;
import com.hify.knowledge.service.KnowledgeService;
import com.hify.workflow.config.KnowledgeConfig;
import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeNodeExecutor implements NodeExecutor {

  private final KnowledgeService knowledgeService;

  @Override
  public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
    try {
      KnowledgeConfig knowledgeConfig = ExecutorSupport.requireConfig(
          config, KnowledgeConfig.class, nodeType());
      String query = ctx.resolve(knowledgeConfig.query());
      List<KnowledgeChunk> chunks = knowledgeService.searchChunks(
          knowledgeConfig.knowledgeBaseId(), query, knowledgeConfig.topK());
      ctx.set(node.getNodeKey(), knowledgeConfig.outputVariable(), render(chunks));
    } catch (Exception e) {
      throw ExecutorSupport.wrap(node.getNodeKey(), e);
    }
  }

  @Override
  public String nodeType() {
    return NodeType.KNOWLEDGE.name();
  }

  private String render(List<KnowledgeChunk> chunks) {
    StringBuilder output = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      if (i > 0) {
        output.append("\n\n");
      }
      KnowledgeChunk chunk = chunks.get(i);
      output.append("[来源 ").append(i + 1)
          .append(" | 文档: ").append(chunk.documentName())
          .append(" | 分块: ").append(chunk.chunkIndex() + 1)
          .append("]\n").append(chunk.content());
    }
    return output.toString();
  }
}
