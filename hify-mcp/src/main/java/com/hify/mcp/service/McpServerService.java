package com.hify.mcp.service;

import com.hify.common.dto.PageResult;
import com.hify.mcp.dto.McpConnectionTestResponse;
import com.hify.mcp.dto.McpServerCreateReq;
import com.hify.mcp.dto.McpServerDetailResponse;
import com.hify.mcp.dto.McpServerListResponse;
import com.hify.mcp.dto.McpServerUpdateReq;

public interface McpServerService {

  McpServerDetailResponse create(McpServerCreateReq req);

  PageResult<McpServerListResponse> list(int page, int pageSize, String keyword, Boolean enabled);

  McpServerDetailResponse getDetail(Long id);

  McpServerDetailResponse update(Long id, McpServerUpdateReq req);

  void delete(Long id);

  McpConnectionTestResponse testConnection(Long id);
}
