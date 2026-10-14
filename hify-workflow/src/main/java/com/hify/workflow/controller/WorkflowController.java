package com.hify.workflow.controller;

import com.hify.common.dto.PageResult;
import com.hify.common.dto.Result;
import com.hify.workflow.dto.WorkflowCreateReq;
import com.hify.workflow.dto.WorkflowDetailResponse;
import com.hify.workflow.dto.WorkflowListResponse;
import com.hify.workflow.dto.WorkflowUpdateReq;
import com.hify.workflow.service.WorkflowService;
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
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

  private final WorkflowService workflowService;

  @PostMapping
  public Result<WorkflowDetailResponse> create(
      @Valid @RequestBody WorkflowCreateReq request) {
    return Result.ok(workflowService.createWorkflow(request));
  }

  @GetMapping
  public Result<PageResult<WorkflowListResponse>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean enabled) {
    return Result.ok(workflowService.listWorkflows(page, pageSize, keyword, enabled));
  }

  @GetMapping("/{id}")
  public Result<WorkflowDetailResponse> detail(@PathVariable Long id) {
    return Result.ok(workflowService.getWorkflow(id));
  }

  @PutMapping("/{id}")
  public Result<WorkflowDetailResponse> update(@PathVariable Long id,
      @Valid @RequestBody WorkflowUpdateReq request) {
    return Result.ok(workflowService.updateWorkflow(id, request));
  }

  @DeleteMapping("/{id}")
  public Result<Void> delete(@PathVariable Long id) {
    workflowService.deleteWorkflow(id);
    return Result.ok();
  }
}
