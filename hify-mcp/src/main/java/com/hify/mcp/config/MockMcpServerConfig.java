package com.hify.mcp.config;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Local Streamable HTTP MCP server used for end-to-end development tests. */
@Configuration
public class MockMcpServerConfig {

  @Bean
  public HttpServletStreamableServerTransportProvider mockMcpTransportProvider() {
    return HttpServletStreamableServerTransportProvider.builder()
        .mcpEndpoint("/mcp")
        .securityValidator(DefaultServerTransportSecurityValidator.builder()
            .allowedHost("localhost:8080")
            .allowedHost("127.0.0.1:8080")
            .build())
        .build();
  }

  @Bean
  public ServletRegistrationBean<HttpServletStreamableServerTransportProvider>
      mockMcpServletRegistration(
          HttpServletStreamableServerTransportProvider mockMcpTransportProvider) {
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> registration =
        new ServletRegistrationBean<>(mockMcpTransportProvider, "/mcp");
    registration.setName("mockMcpStreamableHttpServlet");
    registration.setLoadOnStartup(1);
    registration.setAsyncSupported(true);
    return registration;
  }

  @Bean(destroyMethod = "close")
  public McpSyncServer mockMcpServer(
      HttpServletStreamableServerTransportProvider mockMcpTransportProvider) {
    McpServerFeatures.SyncToolSpecification mockTool =
        McpServerFeatures.SyncToolSpecification.builder()
            .tool(McpSchema.Tool.builder()
                .name("mock_success")
                .description("Local mock tool that returns a successful result")
                .inputSchema(McpJsonDefaults.getMapper(), """
                    {
                      "type": "object",
                      "properties": {},
                      "additionalProperties": false
                    }
                    """)
                .build())
            .callHandler((exchange, request) -> McpSchema.CallToolResult.builder()
                .addTextContent("成功")
                .isError(false)
                .build())
            .build();

    return McpServer.sync(mockMcpTransportProvider)
        .serverInfo("hify-mock-mcp", "1.0.0")
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(List.of(mockTool))
        .build();
  }
}
