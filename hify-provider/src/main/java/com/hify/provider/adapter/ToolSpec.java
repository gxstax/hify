package com.hify.provider.adapter;

import java.util.Map;
import lombok.Data;

/**
 * A tool offered to the model. Adapters map it onto their wire format
 * (OpenAI {@code tools[].function}, Anthropic {@code tools[]}, ...).
 */
@Data
public class ToolSpec {

  private String name;

  private String description;

  /** JSON Schema of the parameters, e.g. {"type":"object","properties":{...}}. */
  private Map<String, Object> parameters;
}
