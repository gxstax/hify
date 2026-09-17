package com.hify.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * M:N binding between an agent and an MCP server (agent_tool table).
 * Logical delete: unbinding sets deleted = 1.
 */
@Getter
@Setter
@TableName("agent_tool")
public class AgentTool extends BaseEntity {

  /** agent.id (app-layer FK). */
  private Long agentId;

  /** mcp_server.id (app-layer FK; column named tool_id). */
  private Long toolId;
}
