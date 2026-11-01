package com.hify.mcp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hify.mcp.dto.McpServerCreateReq;
import com.hify.mcp.dto.McpServerDetailResponse;
import com.hify.mcp.dto.McpServerListResponse;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.mapper.McpToolMapper;
import com.hify.mcp.service.McpRemoteClient;
import com.hify.mcp.service.McpToolSyncService;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpServerServiceImplTest {

  private final McpServerMapper serverMapper = mock(McpServerMapper.class);
  private final McpToolMapper toolMapper = mock(McpToolMapper.class);
  private final McpRemoteClient remoteClient = mock(McpRemoteClient.class);
  private final McpToolSyncService syncService = mock(McpToolSyncService.class);
  private final McpServerServiceImpl service = new McpServerServiceImpl(
      serverMapper, toolMapper, remoteClient, syncService, List.of());

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
