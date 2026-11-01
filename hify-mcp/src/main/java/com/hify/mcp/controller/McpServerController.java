package com.hify.mcp.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.mcp.dto.McpConnectionTestResponse;
import com.hify.mcp.dto.McpServerCreateReq;
import com.hify.mcp.dto.McpServerDetailResponse;
import com.hify.mcp.dto.McpServerListResponse;
import com.hify.mcp.dto.McpServerUpdateReq;
import com.hify.mcp.service.McpServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

  private final McpServerService serverService;

  @PostMapping
  public Result<McpServerDetailResponse> create(
      @Valid @RequestBody McpServerCreateReq req) {
    return Result.ok(serverService.create(req));
  }

  @GetMapping
  public Result<PageResult<McpServerListResponse>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean enabled) {
    return Result.ok(serverService.list(page, pageSize, keyword, enabled));
  }

  @GetMapping("/{id}")
  public Result<McpServerDetailResponse> detail(@PathVariable Long id) {
    return Result.ok(serverService.getDetail(id));
  }

  @PutMapping("/{id}")
  public Result<McpServerDetailResponse> update(@PathVariable Long id,
      @Valid @RequestBody McpServerUpdateReq req) {
    return Result.ok(serverService.update(id, req));
  }

  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    serverService.delete(id);
    return Result.ok();
  }

  @PostMapping("/{id}/test")
  public Result<McpConnectionTestResponse> test(@PathVariable Long id) {
    return Result.ok(serverService.testConnection(id));
  }
}
