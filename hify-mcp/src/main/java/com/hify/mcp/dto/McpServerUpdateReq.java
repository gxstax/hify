package com.hify.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class McpServerUpdateReq {

  @NotBlank(message = "MCP Server 名称不能为空")
  @Size(max = 100, message = "MCP Server 名称长度不能超过 100")
  private String name;

  @NotBlank(message = "endpoint 不能为空")
  @Size(max = 500, message = "endpoint 长度不能超过 500")
  private String endpoint;

  @Size(max = 500, message = "描述长度不能超过 500")
  private String description;

  @NotNull(message = "enabled 不能为空")
  private Boolean enabled;
}
