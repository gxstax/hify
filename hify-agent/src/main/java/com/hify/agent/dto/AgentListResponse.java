package com.hify.agent.dto;

import com.hify.agent.entity.Agent;
import com.hify.provider.dto.ModelConfigBrief;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * List-row payload of an agent: core fields plus the bound model's display
 * info and the number of bound tools (no system prompt — keep rows light).
 */
@Data
public class AgentListResponse {

  private Long id;
  private String name;
  private String description;

  private Long modelConfigId;
  private String modelName;
  private String providerName;

  private Double temperature;
  private Integer maxTokens;
  private Integer maxContextTurns;
  private Boolean enabled;

  /** Number of bound MCP servers (agent_tool rows). */
  private Long toolCount;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static AgentListResponse of(Agent agent, ModelConfigBrief model, long toolCount) {
    AgentListResponse resp = new AgentListResponse();
    resp.setId(agent.getId());
    resp.setName(agent.getName());
    resp.setDescription(agent.getDescription());
    resp.setModelConfigId(agent.getModelConfigId());
    resp.setModelName(model == null ? null : model.getName());
    resp.setProviderName(model == null ? null : model.getProviderName());
    resp.setTemperature(agent.getTemperature());
    resp.setMaxTokens(agent.getMaxTokens());
    resp.setMaxContextTurns(agent.getMaxContextTurns());
    resp.setEnabled(agent.getEnabled());
    resp.setToolCount(toolCount);
    resp.setCreatedAt(agent.getCreatedAt());
    resp.setUpdatedAt(agent.getUpdatedAt());
    return resp;
  }
}
