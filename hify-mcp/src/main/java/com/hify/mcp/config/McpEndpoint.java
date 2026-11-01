package com.hify.mcp.config;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;

public record McpEndpoint(String baseUri, String path) {

  public static McpEndpoint parse(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      throw invalidEndpoint();
    }
    try {
      URI uri = new URI(endpoint);
      String scheme = uri.getScheme();
      if (!uri.isAbsolute() || uri.getHost() == null
          || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
          || uri.getUserInfo() != null || uri.getFragment() != null) {
        throw invalidEndpoint();
      }
      URI base = new URI(scheme, null, uri.getHost(), uri.getPort(), null, null, null);
      String path = uri.getRawPath();
      if (path == null || path.isEmpty()) {
        path = "/";
      }
      if (uri.getRawQuery() != null) {
        path += "?" + uri.getRawQuery();
      }
      return new McpEndpoint(base.toString(), path);
    } catch (URISyntaxException | IllegalArgumentException e) {
      throw invalidEndpoint(e);
    }
  }

  private static BizException invalidEndpoint() {
    return new BizException(ErrorCode.MCP_ENDPOINT_INVALID);
  }

  private static BizException invalidEndpoint(Throwable cause) {
    return new BizException(ErrorCode.MCP_ENDPOINT_INVALID,
        ErrorCode.MCP_ENDPOINT_INVALID.getMessage(), cause);
  }
}
