package com.hify.mcp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.dto.McpDebugReq;
import com.hify.mcp.dto.McpDebugResponse;
import com.hify.mcp.dto.McpServerCreateReq;
import com.hify.mcp.dto.McpServerDetailResponse;
import com.hify.mcp.dto.McpServerListResponse;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.entity.McpTool;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.mapper.McpToolMapper;
import com.hify.mcp.service.McpClientService;
import com.hify.mcp.service.McpRemoteClient;
import com.hify.mcp.service.McpToolSyncService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerServiceImplTest {

  private final McpServerMapper serverMapper = mock(McpServerMapper.class);
  private final McpToolMapper toolMapper = mock(McpToolMapper.class);
  private final McpRemoteClient remoteClient = mock(McpRemoteClient.class);
  private final McpClientService clientService = mock(McpClientService.class);
  private final McpToolSyncService syncService = mock(McpToolSyncService.class);
  private final McpServerServiceImpl service = new McpServerServiceImpl(
      serverMapper, toolMapper, remoteClient, clientService, syncService, List.of());

  @Test
  void createAppliesSchemaDefaults() {
    prepareCreate();
    McpServerCreateReq req = request(null, null);

    McpServerDetailResponse result = service.create(req);

    assertEquals("", result.description());
    assertEquals(Boolean.TRUE, result.enabled());
  }

  @Test
  void createPreservesDescriptionAndExplicitDisabledState() {
    prepareCreate();
    McpServerCreateReq req = request("测试服务", false);

    McpServerDetailResponse result = service.create(req);

    assertEquals("测试服务", result.description());
    assertEquals(Boolean.FALSE, result.enabled());
  }

  @Test
  void responseFactoriesIncludeDescription() {
    McpServer server = new McpServer();
    server.setId(1L);
    server.setName("mock");
    server.setEndpoint("http://localhost:9000/mcp");
    server.setDescription("说明");
    server.setEnabled(true);

    assertEquals("说明", McpServerListResponse.of(server, 0).description());
    assertEquals("说明", McpServerDetailResponse.of(server, List.of()).description());
  }

  @Test
  void debugCallsRegisteredToolAndReturnsElapsedTime() {
    prepareDebugServer();
    McpTool tool = new McpTool();
    tool.setServerId(1L);
    tool.setName("refund");
    when(toolMapper.selectOne(any())).thenReturn(tool);
    when(clientService.callTool(eq(1L), eq("refund"), any()))
        .thenReturn("success");
    McpDebugReq req = debugRequest(Map.of("amount", 12.5));

    McpDebugResponse result = service.debug(1L, req);

    assertEquals("success", result.result());
    assertTrue(result.elapsedMs() >= 0);
    verify(clientService).callTool(1L, "refund", Map.of("amount", 12.5));
  }

  @Test
  void debugNormalizesNullArguments() {
    prepareDebugServer();
    McpTool tool = new McpTool();
    tool.setServerId(1L);
    tool.setName("refund");
    when(toolMapper.selectOne(any())).thenReturn(tool);
    when(clientService.callTool(1L, "refund", Map.of())).thenReturn("success");
    McpDebugReq req = debugRequest(null);

    service.debug(1L, req);

    verify(clientService).callTool(1L, "refund", Map.of());
  }

  @Test
  void debugRejectsToolOutsideServer() {
    prepareDebugServer();
    when(toolMapper.selectOne(any())).thenReturn(null);
    McpDebugReq req = debugRequest(Map.of());

    BizException exception = assertThrows(BizException.class,
        () -> service.debug(1L, req));

    assertEquals(ErrorCode.MCP_TOOL_NOT_FOUND, exception.getErrorCode());
    verify(clientService, never()).callTool(any(), any(), any());
  }

  @Test
  void debugPreservesClientError() {
    prepareDebugServer();
    McpTool tool = new McpTool();
    tool.setServerId(1L);
    tool.setName("refund");
    when(toolMapper.selectOne(any())).thenReturn(tool);
    when(clientService.callTool(any(), any(), any()))
        .thenThrow(new BizException(ErrorCode.MCP_SERVER_DISABLED));
    McpDebugReq req = debugRequest(Map.of());

    BizException exception = assertThrows(BizException.class,
        () -> service.debug(1L, req));

    assertEquals(ErrorCode.MCP_SERVER_DISABLED, exception.getErrorCode());
  }

  @Test
  void debugRejectsMissingServerBeforeToolLookup() {
    when(serverMapper.selectById(1L)).thenReturn(null);
    McpDebugReq req = debugRequest(Map.of());

    BizException exception = assertThrows(BizException.class,
        () -> service.debug(1L, req));

    assertEquals(ErrorCode.MCP_SERVER_NOT_FOUND, exception.getErrorCode());
    verify(toolMapper, never()).selectOne(any());
    verify(clientService, never()).callTool(any(), any(), any());
  }

  private void prepareDebugServer() {
    McpServer server = new McpServer();
    server.setId(1L);
    server.setEnabled(true);
    when(serverMapper.selectById(1L)).thenReturn(server);
  }

  private McpDebugReq debugRequest(Map<String, Object> arguments) {
    McpDebugReq req = new McpDebugReq();
    req.setToolName("refund");
    req.setArguments(arguments);
    return req;
  }

  private void prepareCreate() {
    when(serverMapper.selectCount(any())).thenReturn(0L);
    doAnswer(invocation -> {
      McpServer server = invocation.getArgument(0);
      server.setId(1L);
      return 1;
    }).when(serverMapper).insert(any(McpServer.class));
    when(toolMapper.selectList(any())).thenReturn(List.of());
  }

  private McpServerCreateReq request(String description, Boolean enabled) {
    McpServerCreateReq req = new McpServerCreateReq();
    req.setName("mock");
    req.setEndpoint("http://localhost:9000/mcp");
    req.setDescription(description);
    req.setEnabled(enabled);
    return req;
  }
}
