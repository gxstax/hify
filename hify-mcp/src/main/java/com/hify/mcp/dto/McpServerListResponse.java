package com.hify.mcp.dto;

import com.hify.mcp.entity.McpServer;
import java.time.LocalDateTime;

public record McpServerListResponse(
    Long id,
    String name,
    String endpoint,
    String description,
    Boolean enabled,
    long toolCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static McpServerListResponse of(McpServer server, long toolCount) {
    return new McpServerListResponse(server.getId(), server.getName(), server.getEndpoint(),
        server.getDescription(), server.getEnabled(), toolCount, server.getCreatedAt(),
        server.getUpdatedAt());
  }
}
