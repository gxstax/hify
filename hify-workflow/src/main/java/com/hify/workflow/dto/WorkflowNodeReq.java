package com.hify.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowNodeReq {

  @NotBlank(message = "nodeKey 不能为空")
  @Size(max = 64, message = "nodeKey 长度不能超过 64")
  @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$",
      message = "nodeKey 必须以字母开头，且只能包含字母、数字、下划线和连字符")
  private String nodeKey;

  @NotBlank(message = "节点名称不能为空")
  @Size(max = 100, message = "节点名称长度不能超过 100")
  private String name;

  @NotBlank(message = "nodeType 不能为空")
  @Pattern(regexp = "START|END|LLM|HTTP|CONDITION|KNOWLEDGE|TEXT",
      message = "nodeType 只支持 START、END、LLM、HTTP、CONDITION、KNOWLEDGE、TEXT")
  private String nodeType;

  @NotNull(message = "config 不能为空")
  private JsonNode config;

  @NotNull(message = "configVersion 不能为空")
  @Min(value = 1, message = "configVersion 必须大于 0")
  private Integer configVersion;

  @NotNull(message = "sortOrder 不能为空")
  @Min(value = 0, message = "sortOrder 不能小于 0")
  private Integer sortOrder;
}
