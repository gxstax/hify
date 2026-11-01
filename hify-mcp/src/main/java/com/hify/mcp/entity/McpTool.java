package com.hify.mcp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(value = "mcp_tool", autoResultMap = true)
public class McpTool extends BaseEntity {

  private Long serverId;
  private String name;
  private String description;

  @TableField(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> inputSchema;
}
