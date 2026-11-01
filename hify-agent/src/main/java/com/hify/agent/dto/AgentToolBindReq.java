package com.hify.agent.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * Tool binding request for {@code PUT /api/v1/agents/{id}/tools}.
 * Full replacement semantics: the list becomes the agent's complete binding
 * set (an empty list clears all bindings).
 */
@Data
public class AgentToolBindReq {

  /** MCP tool ids (mcp_tool.id); duplicates are collapsed. */
  @NotNull(message = "toolIds 不能为 null（清空绑定请传空数组）")
  @Size(max = 10, message = "一个 Agent 最多绑定 10 个工具")
  private List<Long> toolIds;
}
