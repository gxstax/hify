package com.hify.mcp.service;

import com.hify.mcp.dto.McpToolResponse;
import java.util.Collection;
import java.util.List;

public interface McpToolService {

  void requireUsableTools(Collection<Long> toolIds);

  List<McpToolResponse> listUsableTools(Collection<Long> toolIds);
}
