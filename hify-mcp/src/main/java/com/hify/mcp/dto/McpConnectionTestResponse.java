package com.hify.mcp.dto;

public record McpConnectionTestResponse(
    boolean success,
    long latencyMs,
    int toolCount,
    int syncedToolCount,
    String errorMessage) {

  public static McpConnectionTestResponse success(long latencyMs, int toolCount,
      int syncedToolCount) {
    return new McpConnectionTestResponse(true, latencyMs, toolCount, syncedToolCount, "");
  }

  public static McpConnectionTestResponse failure(long latencyMs, String errorMessage) {
    return new McpConnectionTestResponse(false, latencyMs, 0, 0, errorMessage);
  }
}
