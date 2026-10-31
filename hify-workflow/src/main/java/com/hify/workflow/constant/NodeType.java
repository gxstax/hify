package com.hify.workflow.constant;

public enum NodeType {
  START,
  END,
  LLM,
  API_CALL,
  CONDITION,
  KNOWLEDGE,
  TEXT;

  public static NodeType fromExternalValue(String value) {
    if (value == null) {
      throw new IllegalArgumentException("节点类型不能为空");
    }
    return switch (value) {
      case "START" -> START;
      case "END" -> END;
      case "LLM" -> LLM;
      case "API_CALL", "HTTP" -> API_CALL;
      case "CONDITION" -> CONDITION;
      case "KNOWLEDGE" -> KNOWLEDGE;
      case "TEXT" -> TEXT;
      default -> throw new IllegalArgumentException("不支持的节点类型: " + value);
    };
  }
}
