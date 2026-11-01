package com.hify.mcp.service.impl;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.dto.McpToolResponse;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.entity.McpTool;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.mapper.McpToolMapper;
import com.hify.mcp.service.McpToolService;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class McpToolServiceImpl implements McpToolService {

  private final McpToolMapper toolMapper;
  private final McpServerMapper serverMapper;

  @Override
  public void requireUsableTools(Collection<Long> toolIds) {
    loadUsableTools(toolIds);
  }

  @Override
  public List<McpToolResponse> listUsableTools(Collection<Long> toolIds) {
    return loadUsableTools(toolIds).stream().map(McpToolResponse::from).toList();
  }

  private List<McpTool> loadUsableTools(Collection<Long> toolIds) {
    if (toolIds == null || toolIds.isEmpty()) {
      return List.of();
    }
    Set<Long> expected = new LinkedHashSet<>(toolIds);
    Map<Long, McpTool> tools = toolMapper.selectBatchIds(expected).stream()
        .collect(Collectors.toMap(McpTool::getId, Function.identity()));
    if (tools.size() != expected.size()) {
      throw new BizException(ErrorCode.MCP_TOOL_NOT_FOUND);
    }
    Set<Long> serverIds = tools.values().stream()
        .map(McpTool::getServerId)
        .collect(Collectors.toSet());
    Map<Long, McpServer> servers = serverMapper.selectBatchIds(serverIds).stream()
        .collect(Collectors.toMap(McpServer::getId, Function.identity()));
    boolean unavailable = tools.values().stream()
        .map(McpTool::getServerId)
        .map(servers::get)
        .anyMatch(server -> server == null || !Boolean.TRUE.equals(server.getEnabled()));
    if (unavailable) {
      throw new BizException(ErrorCode.MCP_SERVER_DISABLED);
    }
    return expected.stream().map(tools::get).toList();
  }
}
