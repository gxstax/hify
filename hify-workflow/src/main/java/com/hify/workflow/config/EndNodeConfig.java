package com.hify.workflow.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EndNodeConfig(
    @JsonAlias("outputExpression")
    @NotBlank(message = "END 节点 outputVariable 不能为空")
    @Size(max = 500, message = "outputVariable 长度不能超过 500")
    String outputVariable) implements NodeConfigDef {
}
