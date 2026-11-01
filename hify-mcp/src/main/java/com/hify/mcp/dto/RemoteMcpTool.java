package com.hify.mcp.dto;

import java.util.Map;

public record RemoteMcpTool(
    String name,
    String description,
    Map<String, Object> inputSchema) {
}
