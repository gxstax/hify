package com.hify.agent.dto;

import com.hify.agent.entity.Agent;
import com.hify.provider.dto.ModelConfigBrief;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Detail payload of an agent: its own fields plus the bound model's display
 * info (resolved through the provider module) and the bound tool ids.
 */
@Data
public class AgentDetailResponse {

  private Long id;
  private String name;
  private String description;
  private String systemPrompt;

  private Long modelConfigId;
  private String modelName;
  private String providerName;

  private Double temperature;
  private Integer maxTokens;
  private Integer maxContextTurns;
  private Boolean enabled;

  /** Bound MCP server ids (mcp_server.id). */
  private List<Long> toolIds;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static AgentDetailResponse of(Agent agent, ModelConfigBrief model, List<Long> toolIds) {
    AgentDetailResponse resp = new AgentDetailResponse();
    resp.setId(agent.getId());
    resp.setName(agent.getName());
    resp.setDescription(agent.getDescription());
    resp.setSystemPrompt(agent.getSystemPrompt());
    resp.setModelConfigId(agent.getModelConfigId());
    resp.setModelName(model == null ? null : model.getName());
    resp.setProviderName(model == null ? null : model.getProviderName());
    resp.setTemperature(agent.getTemperature());
    resp.setMaxTokens(agent.getMaxTokens());
    resp.setMaxContextTurns(agent.getMaxContextTurns());
    resp.setEnabled(agent.getEnabled());
    resp.setToolIds(toolIds);
    resp.setCreatedAt(agent.getCreatedAt());
    resp.setUpdatedAt(agent.getUpdatedAt());
    return resp;
  }
}
