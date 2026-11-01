package com.hify.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.service.McpClientService;
import com.hify.mcp.service.McpRemoteClient;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpClientServiceImpl implements McpClientService {

  private final McpServerMapper serverMapper;
  private final McpRemoteClient remoteClient;

  @Override
  public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
    McpServer server = requireEnabledServer(mcpServerId);
    try {
      return remoteClient.callTool(server.getEndpoint(), toolName, arguments);
    } catch (BizException e) {
      throw e;
    } catch (Exception e) {
      throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED,
          ErrorCode.MCP_TOOL_CALL_FAILED.getMessage(), e);
    }
  }

  @Override
  public List<String> listTools(Long mcpServerId) {
    McpServer server = requireEnabledServer(mcpServerId);
    try {
      return remoteClient.listTools(server.getEndpoint()).stream()
          .map(tool -> tool.name())
          .toList();
    } catch (Exception e) {
      throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND,
          ErrorCode.MCP_SERVER_NOT_FOUND.getMessage(), e);
    }
  }

  private McpServer requireEnabledServer(Long id) {
    McpServer server = serverMapper.selectById(id);
    if (server == null) {
      throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
    }
    if (!Boolean.TRUE.equals(server.getEnabled())) {
      throw new BizException(ErrorCode.MCP_SERVER_DISABLED);
    }
    return server;
  }
}
