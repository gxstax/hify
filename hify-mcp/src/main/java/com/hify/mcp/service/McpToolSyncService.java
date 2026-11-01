package com.hify.mcp.service;

import com.hify.mcp.dto.RemoteMcpTool;
import java.util.List;

public interface McpToolSyncService {

  int sync(Long serverId, List<RemoteMcpTool> tools);
}
