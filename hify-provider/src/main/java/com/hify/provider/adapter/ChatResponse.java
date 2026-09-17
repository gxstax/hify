package com.hify.provider.adapter;

import java.util.List;
import lombok.Data;

/**
 * Provider-neutral chat completion result. For streaming calls this is the
 * aggregated outcome delivered once when the stream completes.
 */
@Data
public class ChatResponse {

  /** Assistant text; may be null on pure tool-call turns. */
  private String content;

  /** Tool calls requested by the model; empty when it answered directly. */
  private List<ToolCall> toolCalls;

  /** Provider stop reason: stop | tool_calls | length | ... */
  private String finishReason;

  /** Token usage when the provider reports it (may be null). */
  private Integer promptTokens;
  private Integer completionTokens;

  /** True when the model wants tools executed before it can continue. */
  public boolean hasToolCalls() {
    return toolCalls != null && !toolCalls.isEmpty();
  }
}
