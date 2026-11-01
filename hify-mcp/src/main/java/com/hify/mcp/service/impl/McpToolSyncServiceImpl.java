package com.hify.mcp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.dto.RemoteMcpTool;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.entity.McpTool;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.mapper.McpToolMapper;
import com.hify.mcp.service.McpToolSyncService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class McpToolSyncServiceImpl implements McpToolSyncService {

  private final McpServerMapper serverMapper;
  private final McpToolMapper toolMapper;

  @Override
  @Transactional(
      transactionManager = "mysqlTransactionManager",
      rollbackFor = Exception.class)
  public int sync(Long serverId, List<RemoteMcpTool> tools) {
    McpServer server = serverMapper.selectById(serverId);
    if (server == null) {
      throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
    }
    HashSet<String> names = new HashSet<>();
    for (RemoteMcpTool tool : tools) {
      if (tool.name() == null || tool.name().isBlank() || !names.add(tool.name())) {
        throw new BizException(ErrorCode.PARAM_ERROR, "MCP 工具名称为空或重复");
      }
    }

    Map<String, McpTool> existing = toolMapper.selectList(Wrappers.<McpTool>lambdaQuery()
            .eq(McpTool::getServerId, serverId))
        .stream()
        .collect(Collectors.toMap(McpTool::getName, Function.identity()));
    for (RemoteMcpTool remote : tools) {
      McpTool tool = existing.get(remote.name());
      if (tool == null) {
        tool = new McpTool();
        tool.setServerId(serverId);
        tool.setName(remote.name());
        tool.setDescription(remote.description());
        tool.setInputSchema(remote.inputSchema());
        toolMapper.insert(tool);
      } else {
        McpTool update = new McpTool();
        update.setId(tool.getId());
        update.setDescription(remote.description());
        update.setInputSchema(remote.inputSchema());
        toolMapper.updateById(update);
      }
    }
    return tools.size();
  }
}
