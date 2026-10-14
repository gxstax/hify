package com.hify.workflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EndNodeConfig(
    @NotBlank(message = "END 节点 outputExpression 不能为空")
    @Size(max = 500, message = "outputExpression 长度不能超过 500")
    String outputExpression) implements NodeConfig {
}
