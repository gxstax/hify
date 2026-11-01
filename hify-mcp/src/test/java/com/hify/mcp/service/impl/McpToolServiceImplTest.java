package com.hify.mcp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hify.common.exception.BizException;
import com.hify.mcp.dto.McpToolResponse;
import com.hify.mcp.entity.McpServer;
import com.hify.mcp.entity.McpTool;
import com.hify.mcp.mapper.McpServerMapper;
import com.hify.mcp.mapper.McpToolMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpToolServiceImplTest {

  private final McpToolMapper toolMapper = mock(McpToolMapper.class);
  private final McpServerMapper serverMapper = mock(McpServerMapper.class);
  private final McpToolServiceImpl service = new McpToolServiceImpl(toolMapper, serverMapper);

  @Test
  void returnsUsableToolsInRequestedOrder() {
    McpTool first = tool(1L, 10L, "weather");
    McpTool second = tool(2L, 10L, "order");
    McpServer server = server(10L, true);
    when(toolMapper.selectBatchIds(anyCollection())).thenReturn(List.of(first, second));
    when(serverMapper.selectBatchIds(anyCollection())).thenReturn(List.of(server));

    List<McpToolResponse> result = service.listUsableTools(List.of(2L, 1L));

    assertEquals(List.of(2L, 1L), result.stream().map(McpToolResponse::id).toList());
  }

  @Test
  void rejectsToolsFromDisabledServer() {
    McpTool tool = tool(1L, 10L, "weather");
    when(toolMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tool));
    when(serverMapper.selectBatchIds(anyCollection()))
        .thenReturn(List.of(server(10L, false)));

    assertThrows(BizException.class, () -> service.listUsableTools(List.of(1L)));
  }

  private McpTool tool(Long id, Long serverId, String name) {
    McpTool tool = new McpTool();
    tool.setId(id);
    tool.setServerId(serverId);
    tool.setName(name);
    tool.setInputSchema(java.util.Map.of("type", "object"));
    return tool;
  }

  private McpServer server(Long id, boolean enabled) {
    McpServer server = new McpServer();
    server.setId(id);
    server.setEnabled(enabled);
    return server;
  }
}
