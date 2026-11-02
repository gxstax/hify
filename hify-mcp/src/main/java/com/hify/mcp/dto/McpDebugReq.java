package com.hify.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

@Data
public class McpDebugReq {

  @NotBlank(message = "toolName 不能为空")
  @Size(max = 255, message = "toolName 长度不能超过 255")
  private String toolName;

  private Map<String, Object> arguments;
}
