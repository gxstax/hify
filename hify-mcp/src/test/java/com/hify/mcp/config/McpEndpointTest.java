package com.hify.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

class McpEndpointTest {

  @Test
  void parsesCompleteEndpoint() {
    McpEndpoint endpoint = McpEndpoint.parse(
        "https://mcp.example.com:8443/api/mcp?tenant=hify");

    assertEquals("https://mcp.example.com:8443", endpoint.baseUri());
    assertEquals("/api/mcp?tenant=hify", endpoint.path());
  }

  @Test
  void defaultsEmptyPathToRoot() {
    McpEndpoint endpoint = McpEndpoint.parse("http://localhost:9000");

    assertEquals("http://localhost:9000", endpoint.baseUri());
    assertEquals("/", endpoint.path());
  }

  @Test
  void rejectsUnsupportedSchemeAndCredentials() {
    assertThrows(BizException.class, () -> McpEndpoint.parse("ftp://example.com/mcp"));
    assertThrows(BizException.class,
        () -> McpEndpoint.parse("https://user:pass@example.com/mcp"));
  }
}
