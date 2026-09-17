package com.hify.agent.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** Create request for {@code POST /api/v1/agents}. */
@Data
public class AgentCreateReq {

  @NotBlank(message = "Agent 名称不能为空")
  @Size(max = 128, message = "Agent 名称长度不能超过 128")
  private String name;

  @Size(max = 512, message = "描述长度不能超过 512")
  private String description;

  /** System prompt; no length cap (TEXT column). */
  private String systemPrompt;

  @NotNull(message = "必须绑定模型")
  private Long modelConfigId;

  @DecimalMin(value = "0", message = "temperature 不能小于 0")
  @DecimalMax(value = "2", message = "temperature 不能大于 2")
  private Double temperature;

  @Min(value = 1, message = "maxTokens 必须大于 0")
  private Integer maxTokens;

  /** Recent turns kept as context; null falls back to 10. */
  @Min(value = 1, message = "maxContextTurns 必须大于 0")
  private Integer maxContextTurns;

  /** Bound MCP server ids (mcp_server.id); duplicates are collapsed. */
  private List<Long> toolIds;
}
