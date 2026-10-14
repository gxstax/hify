package com.hify.workflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InputDefinition(
    @NotBlank(message = "输入变量名称不能为空")
    @Size(max = 64, message = "输入变量名称长度不能超过 64")
    String name,
    @NotNull(message = "输入变量类型不能为空")
    VariableType type,
    boolean required) {
}
