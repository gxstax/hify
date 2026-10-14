package com.hify.workflow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record KnowledgeNodeConfig(
    @NotNull(message = "KNOWLEDGE 节点 knowledgeBaseId 不能为空")
    @Positive(message = "knowledgeBaseId 必须大于 0")
    Long knowledgeBaseId,
    @NotBlank(message = "KNOWLEDGE 节点 query 不能为空")
    @Size(max = 10000, message = "query 长度不能超过 10000")
    String query,
    @NotNull(message = "KNOWLEDGE 节点 topK 不能为空")
    @Min(value = 1, message = "topK 不能小于 1")
    @Max(value = 100, message = "topK 不能大于 100")
    Integer topK,
    @NotBlank(message = "KNOWLEDGE 节点 outputVariable 不能为空")
    @Size(max = 64, message = "outputVariable 长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
        message = "outputVariable 必须以字母开头，且只能包含字母、数字和下划线")
    String outputVariable) implements NodeConfig {
}
