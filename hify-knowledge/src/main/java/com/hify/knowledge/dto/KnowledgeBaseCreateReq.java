package com.hify.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Create request for a knowledge base. */
@Data
public class KnowledgeBaseCreateReq {

  @NotBlank(message = "知识库名称不能为空")
  @Size(max = 100, message = "知识库名称长度不能超过 100")
  private String name;

  @Size(max = 500, message = "知识库描述长度不能超过 500")
  private String description;
}
