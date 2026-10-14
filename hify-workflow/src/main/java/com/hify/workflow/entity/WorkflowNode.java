package com.hify.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName(value = "workflow_node", autoResultMap = true)
public class WorkflowNode extends BaseEntity {

  private Long workflowId;
  private String nodeKey;
  private String name;
  private String nodeType;

  @TableField(typeHandler = JacksonTypeHandler.class)
  private JsonNode config;

  private Integer configVersion;
  private Integer sortOrder;
}
