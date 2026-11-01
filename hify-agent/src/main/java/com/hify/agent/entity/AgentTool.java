package com.hify.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** M:N binding between an Agent and an MCP tool. */
@Getter
@Setter
@TableName("agent_tool")
public class AgentTool {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** agent.id (app-layer FK). */
  private Long agentId;

  /** mcp_tool.id (app-layer FK). */
  private Long toolId;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;
}
