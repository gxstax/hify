package com.hify.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Runtime record has no logical-delete or updated-at columns. */
@Getter
@Setter
@TableName("workflow_run")
public class WorkflowRun {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long workflowId;
  private String status;
  private String input;
  private String output;
  private String error;
  private Long elapsedMs;
  private LocalDateTime createdAt;
  private LocalDateTime finishedAt;
}
