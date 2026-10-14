package com.hify.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public abstract class WorkflowDefinitionReq {

  @NotBlank(message = "工作流名称不能为空")
  @Size(max = 100, message = "工作流名称长度不能超过 100")
  private String name;

  @Size(max = 500, message = "描述长度不能超过 500")
  private String description;

  @NotNull(message = "version 不能为空")
  @Min(value = 1, message = "version 必须大于 0")
  private Integer version;

  @NotNull(message = "enabled 不能为空")
  private Boolean enabled;

  @NotNull(message = "nodes 不能为空")
  @Size(min = 1, message = "工作流至少包含一个节点")
  @Valid
  private List<WorkflowNodeReq> nodes;

  @NotNull(message = "edges 不能为空")
  @Valid
  private List<WorkflowEdgeReq> edges;
}
