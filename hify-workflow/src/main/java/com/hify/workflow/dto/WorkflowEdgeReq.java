package com.hify.workflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowEdgeReq {

  @NotBlank(message = "sourceNodeKey 不能为空")
  @Size(max = 64, message = "sourceNodeKey 长度不能超过 64")
  private String sourceNodeKey;

  @NotBlank(message = "targetNodeKey 不能为空")
  @Size(max = 64, message = "targetNodeKey 长度不能超过 64")
  private String targetNodeKey;

  @Size(max = 32, message = "sourcePort 长度不能超过 32")
  private String sourcePort;

  @NotNull(message = "priority 不能为空")
  @Min(value = 0, message = "priority 不能小于 0")
  private Integer priority;
}
