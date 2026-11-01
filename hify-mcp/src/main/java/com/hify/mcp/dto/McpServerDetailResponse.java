package com.hify.mcp.dto;

import com.hify.mcp.entity.McpServer;
import java.time.LocalDateTime;
import java.util.List;

public record McpServerDetailResponse(
    Long id,
    String name,
    String endpoint,
    String description,
    Boolean enabled,
    List<McpToolResponse> tools,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static McpServerDetailResponse of(McpServer server, List<McpToolResponse> tools) {
    return new McpServerDetailResponse(server.getId(), server.getName(), server.getEndpoint(),
        server.getDescription(), server.getEnabled(), tools, server.getCreatedAt(),
        server.getUpdatedAt());
  }
}
