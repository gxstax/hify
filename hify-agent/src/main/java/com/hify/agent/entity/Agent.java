package com.hify.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Agent definition: model binding + system prompt (+ tools via agent_tool). */
@Getter
@Setter
@TableName("agent")
public class Agent extends BaseEntity {

  /** Unique agent name shown in the console. */
  private String name;

  /** Short description. */
  private String description;

  /** System prompt defining agent behavior. */
  private String systemPrompt;

  /** Bound model, model_config.id (app-layer FK, provider module). */
  private Long modelConfigId;

  /** Sampling temperature; NULL = provider default. */
  private Double temperature;

  /** Max output tokens; NULL = provider default. */
  private Integer maxTokens;

  /** How many recent conversation turns are kept as context (default 10). */
  private Integer maxContextTurns;

  /** 1 = enabled, 0 = disabled. */
  private Boolean enabled;
}
