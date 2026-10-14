package com.hify.workflow.service;

import com.hify.common.dto.PageResult;
import com.hify.workflow.dto.WorkflowCreateReq;
import com.hify.workflow.dto.WorkflowDetailResponse;
import com.hify.workflow.dto.WorkflowListResponse;
import com.hify.workflow.dto.WorkflowUpdateReq;

public interface WorkflowService {

  WorkflowDetailResponse createWorkflow(WorkflowCreateReq request);

  PageResult<WorkflowListResponse> listWorkflows(int page, int pageSize, String keyword,
      Boolean enabled);

  WorkflowDetailResponse getWorkflow(Long id);

  WorkflowDetailResponse updateWorkflow(Long id, WorkflowUpdateReq request);

  void deleteWorkflow(Long id);
}
