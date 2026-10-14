package com.hify.workflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConditionNodeConfig(
    @NotBlank(message = "CONDITION 节点 expression 不能为空")
    @Size(max = 500, message = "expression 长度不能超过 500")
    String expression,
    @NotBlank(message = "CONDITION 节点 outputVariable 不能为空")
    @Size(max = 64, message = "outputVariable 长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
        message = "outputVariable 必须以字母开头，且只能包含字母、数字和下划线")
    String outputVariable) implements NodeConfig {
}
