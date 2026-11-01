package com.hify.mcp.dto;

import com.hify.mcp.entity.McpTool;
import java.util.Map;

public record McpToolResponse(
    Long id,
    Long serverId,
    String name,
    String description,
    Map<String, Object> inputSchema) {

  public static McpToolResponse from(McpTool tool) {
    return new McpToolResponse(tool.getId(), tool.getServerId(), tool.getName(),
        tool.getDescription(), tool.getInputSchema());
  }
}
