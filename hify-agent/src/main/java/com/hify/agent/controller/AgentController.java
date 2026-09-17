package com.hify.agent.controller;

import com.hify.agent.dto.AgentCreateReq;
import com.hify.agent.dto.AgentDetailResponse;
import com.hify.agent.dto.AgentListResponse;
import com.hify.agent.dto.AgentToolBindReq;
import com.hify.agent.dto.AgentUpdateReq;
import com.hify.agent.service.AgentService;
import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import jakarta.validation.Valid;
import java.util.List;
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

/**
 * Agent REST endpoints (CLAUDE.md conventions: /api/v1/{resource}, unified
 * Result envelope, page starts at 1 / pageSize default 20).
 */
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

  private final AgentService agentService;

  /** POST /api/v1/agents */
  @PostMapping
  public Result<AgentDetailResponse> create(@Valid @RequestBody AgentCreateReq req) {
    return Result.ok(agentService.createAgent(req));
  }

  /** GET /api/v1/agents?page=1&pageSize=20&keyword=xx&enabled=true */
  @GetMapping
  public Result<PageResult<AgentListResponse>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean enabled) {
    return Result.ok(agentService.listAgents(page, pageSize, keyword, enabled));
  }

  /** GET /api/v1/agents/{id} — full fields + model info + bound tool ids */
  @GetMapping("/{id}")
  public Result<AgentDetailResponse> detail(@PathVariable Long id) {
    return Result.ok(agentService.getAgent(id));
  }

  /** PUT /api/v1/agents/{id} — basic info only (tools have their own endpoint) */
  @PutMapping("/{id}")
  public Result<AgentDetailResponse> update(@PathVariable Long id,
      @Valid @RequestBody AgentUpdateReq req) {
    return Result.ok(agentService.updateAgent(id, req));
  }

  /** PUT /api/v1/agents/{id}/tools — replace the tool bindings wholesale */
  @PutMapping("/{id}/tools")
  public Result<List<Long>> updateTools(@PathVariable Long id,
      @Valid @RequestBody AgentToolBindReq req) {
    return Result.ok(agentService.updateAgentTools(id, req));
  }

  /** DELETE /api/v1/agents/{id} */
  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    agentService.deleteAgent(id);
    return Result.ok();
  }
}
