package com.hify.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Runtime record has no logical-delete or updated-at columns. */
@Getter
@Setter
@TableName(value = "workflow_node_run", autoResultMap = true)
public class WorkflowNodeRun {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long workflowRunId;
  private String nodeKey;
  private String nodeType;
  private String status;

  @TableField(typeHandler = JacksonTypeHandler.class)
  private JsonNode outputs;

  private String error;
  private Long elapsedMs;
  private LocalDateTime createdAt;
  private LocalDateTime finishedAt;
}
