package com.hify.mcp.service;

import com.hify.mcp.dto.RemoteMcpTool;
import java.util.List;
import java.util.Map;

public interface McpRemoteClient {

  List<RemoteMcpTool> listTools(String endpoint);

  String callTool(String endpoint, String toolName, Map<String, Object> arguments);
}
