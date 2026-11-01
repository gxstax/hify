package com.hify.mcp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.mcp.config.McpEndpoint;
import com.hify.mcp.dto.McpConnectionTestResponse;
import com.hify.mcp.dto.McpServerCreateReq;
import com.hify.mcp.dto.McpServerDetailResponse;
import com.hify.mcp.dto.McpServerListResponse;
import com.hify.mcp.dto.McpServerUpdateReq;
import com.hify.mcp.dto.McpToolResponse;
import com.hify.mcp.dto.RemoteMcpTool;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.entity.McpTool;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.mapper.McpToolMapper;
import com.hify.mcp.service.McpRemoteClient;
import com.hify.mcp.service.McpServerService;
import com.hify.mcp.service.McpToolReferenceChecker;
import com.hify.mcp.service.McpToolSyncService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

  private final McpServerMapper serverMapper;
  private final McpToolMapper toolMapper;
  private final McpRemoteClient remoteClient;
  private final McpToolSyncService toolSyncService;
  private final List<McpToolReferenceChecker> referenceCheckers;

  @Override
  public McpServerDetailResponse create(McpServerCreateReq req) {
    McpEndpoint.parse(req.getEndpoint());
    ensureNameFree(req.getName(), null);
    McpServer server = new McpServer();
    server.setName(req.getName());
    server.setEndpoint(req.getEndpoint());
    server.setDescription(req.getDescription() == null ? "" : req.getDescription());
    server.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
    serverMapper.insert(server);
    return detail(server);
  }

  @Override
  public PageResult<McpServerListResponse> list(int page, int pageSize, String keyword,
      Boolean enabled) {
    Page<McpServer> mpPage = PageHelper.toPage(page, pageSize);
    serverMapper.selectPage(mpPage, Wrappers.<McpServer>lambdaQuery()
        .eq(enabled != null, McpServer::getEnabled, enabled)
        .like(keyword != null && !keyword.isBlank(), McpServer::getName, keyword)
        .orderByDesc(McpServer::getId));
    List<McpServer> servers = mpPage.getRecords();
    if (servers.isEmpty()) {
      return new PageResult<>(List.of(), mpPage.getTotal(), (int) mpPage.getCurrent(),
          (int) mpPage.getSize());
    }
    List<Long> ids = servers.stream().map(McpServer::getId).toList();
    Map<Long, Long> counts = toolMapper.selectList(Wrappers.<McpTool>lambdaQuery()
            .in(McpTool::getServerId, ids))
        .stream()
        .collect(Collectors.groupingBy(McpTool::getServerId, Collectors.counting()));
    List<McpServerListResponse> items = servers.stream()
        .map(server -> McpServerListResponse.of(server,
            counts.getOrDefault(server.getId(), 0L)))
        .toList();
    return new PageResult<>(items, mpPage.getTotal(), (int) mpPage.getCurrent(),
        (int) mpPage.getSize());
  }

  @Override
  public McpServerDetailResponse getDetail(Long id) {
    return detail(requireServer(id));
  }

  @Override
  public McpServerDetailResponse update(Long id, McpServerUpdateReq req) {
    requireServer(id);
    McpEndpoint.parse(req.getEndpoint());
    ensureNameFree(req.getName(), id);
    McpServer update = new McpServer();
    update.setId(id);
    update.setName(req.getName());
    update.setEndpoint(req.getEndpoint());
    update.setDescription(req.getDescription() == null ? "" : req.getDescription());
    update.setEnabled(req.getEnabled());
    serverMapper.updateById(update);
    return getDetail(id);
  }

  @Override
  @Transactional(
      transactionManager = "mysqlTransactionManager",
      rollbackFor = Exception.class)
  public void delete(Long id) {
    requireServer(id);
    List<Long> toolIds = toolMapper.selectList(Wrappers.<McpTool>lambdaQuery()
            .eq(McpTool::getServerId, id))
        .stream().map(McpTool::getId).toList();
    if (!toolIds.isEmpty()
        && referenceCheckers.stream().anyMatch(checker -> checker.anyReferenced(toolIds))) {
      throw new BizException(ErrorCode.MCP_SERVER_IN_USE);
    }
    toolMapper.delete(Wrappers.<McpTool>lambdaQuery().eq(McpTool::getServerId, id));
    serverMapper.deleteById(id);
  }

  @Override
  public McpConnectionTestResponse testConnection(Long id) {
    McpServer server = requireServer(id);
    long started = System.nanoTime();
    try {
      List<RemoteMcpTool> tools = remoteClient.listTools(server.getEndpoint());
      int synced = toolSyncService.sync(id, tools);
      return McpConnectionTestResponse.success(elapsedMs(started), tools.size(), synced);
    } catch (Exception e) {
      return McpConnectionTestResponse.failure(elapsedMs(started), safeMessage(e));
    }
  }

  private McpServerDetailResponse detail(McpServer server) {
    List<McpToolResponse> tools = toolMapper.selectList(Wrappers.<McpTool>lambdaQuery()
            .eq(McpTool::getServerId, server.getId())
            .orderByAsc(McpTool::getName))
        .stream().map(McpToolResponse::from).toList();
    return McpServerDetailResponse.of(server, tools);
  }

  private McpServer requireServer(Long id) {
    McpServer server = serverMapper.selectById(id);
    if (server == null) {
      throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND);
    }
    return server;
  }

  private void ensureNameFree(String name, Long excludeId) {
    Long count = serverMapper.selectCount(Wrappers.<McpServer>lambdaQuery()
        .eq(McpServer::getName, name)
        .ne(excludeId != null, McpServer::getId, excludeId));
    if (count != null && count > 0) {
      throw new BizException(ErrorCode.MCP_SERVER_NAME_DUPLICATE);
    }
  }

  private long elapsedMs(long started) {
    return (System.nanoTime() - started) / 1_000_000;
  }

  private String safeMessage(Exception e) {
    String message = e.getMessage();
    return message == null || message.isBlank()
        ? ErrorCode.MCP_SERVER_CONNECTION_FAILED.getMessage() : message;
  }
}
