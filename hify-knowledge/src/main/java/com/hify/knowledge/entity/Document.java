package com.hify.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Uploaded document metadata and processing state, stored in MySQL. */
@Getter
@Setter
@TableName("document")
public class Document extends BaseEntity {

  private Long knowledgeBaseId;

  private String name;

  private String fileType;

  private Long fileSize;

  private String status;

  private String errorMessage;

  private Integer chunkCount;
}
