package com.hify.provider.adapter;

import lombok.Data;

/**
 * A tool invocation requested by the model (or, after execution, the record
 * of one). {@code arguments} stays a raw JSON string exactly as the model
 * produced it — parsing belongs to the tool executor, not the adapter.
 */
@Data
public class ToolCall {

  /** Provider-assigned call id; echoed back on the matching tool message. */
  private String id;

  /** Tool (function) name. */
  private String name;

  /** JSON string of the arguments, as produced by the model. */
  private String arguments;
}
