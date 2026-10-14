package com.hify.workflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextNodeConfig(
    @NotBlank(message = "TEXT 节点 template 不能为空")
    @Size(max = 10000, message = "template 长度不能超过 10000")
    String template) implements NodeConfig {
}
