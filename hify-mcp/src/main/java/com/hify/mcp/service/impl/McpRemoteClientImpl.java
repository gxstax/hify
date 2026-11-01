package com.hify.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.config.McpEndpoint;
import com.hify.mcp.config.McpProperties;
import com.hify.mcp.dto.RemoteMcpTool;
import com.hify.mcp.service.McpRemoteClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpRemoteClientImpl implements McpRemoteClient {

  private final McpProperties properties;

  @Override
  public List<RemoteMcpTool> listTools(String endpoint) {
    try (McpSyncClient client = createClient(endpoint)) {
      client.initialize();
      List<RemoteMcpTool> tools = new ArrayList<>();
      String cursor = null;
      do {
        McpSchema.ListToolsResult result = cursor == null
            ? client.listTools() : client.listTools(cursor);
        for (McpSchema.Tool tool : result.tools()) {
          tools.add(new RemoteMcpTool(tool.name(), tool.description(), toMap(tool.inputSchema())));
        }
        cursor = result.nextCursor();
      } while (cursor != null && !cursor.isBlank());
      return tools;
    }
  }

  @Override
  public String callTool(String endpoint, String toolName, Map<String, Object> arguments) {
    try (McpSyncClient client = createClient(endpoint)) {
      client.initialize();
      McpSchema.CallToolResult result = client.callTool(
          new McpSchema.CallToolRequest(toolName, arguments == null ? Map.of() : arguments));
      if (Boolean.TRUE.equals(result.isError())) {
        throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED);
      }
      return result.content().stream()
          .filter(McpSchema.TextContent.class::isInstance)
          .map(McpSchema.TextContent.class::cast)
          .map(McpSchema.TextContent::text)
          .toList()
          .stream()
          .collect(java.util.stream.Collectors.joining("\n"));
    }
  }

  private McpSyncClient createClient(String endpoint) {
    McpEndpoint parts = McpEndpoint.parse(endpoint);
    HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder(parts.baseUri())
            .endpoint(parts.path())
            .clientBuilder(HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()))
            .build();
    return McpClient.sync(transport)
        .requestTimeout(properties.getRequestTimeout())
        .build();
  }

  private Map<String, Object> toMap(McpSchema.JsonSchema schema) {
    Map<String, Object> value = new LinkedHashMap<>();
    if (schema == null) {
      return value;
    }
    put(value, "type", schema.type());
    put(value, "properties", schema.properties());
    put(value, "required", schema.required());
    put(value, "additionalProperties", schema.additionalProperties());
    put(value, "$defs", schema.defs());
    put(value, "definitions", schema.definitions());
    return value;
  }

  private void put(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }
}
