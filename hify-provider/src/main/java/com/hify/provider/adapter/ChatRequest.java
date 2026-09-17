package com.hify.provider.adapter;

import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Provider-neutral chat completion request. Built by the chat engine from an
 * agent's configuration + conversation window; adapters translate it into
 * their protocol's payload.
 */
@Data
public class ChatRequest {

  /** Model id as the provider API expects (model_config.model_id). */
  private String model;

  /** Full message sequence: system + history + current turn. */
  private List<ChatMessage> messages;

  /** Sampling temperature; null = provider default. */
  private Double temperature;

  /** Max output tokens; null = provider default. */
  private Integer maxTokens;

  /**
   * Long-tail parameters merged into the payload as-is (top_p, stop, ...);
   * agent-level values override model-level defaults before they land here.
   */
  private Map<String, Object> extraParams;

  /** Tools offered to the model; null/empty = plain completion. */
  private List<ToolSpec> tools;
}
