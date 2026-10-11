package com.hify.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Full-field update request for a knowledge base. */
@Data
public class KnowledgeBaseUpdateReq {

  @NotBlank(message = "知识库名称不能为空")
  @Size(max = 100, message = "知识库名称长度不能超过 100")
  private String name;

  @Size(max = 500, message = "知识库描述长度不能超过 500")
  private String description;

  @NotNull(message = "知识库启用状态不能为空")
  private Boolean enabled;
}
