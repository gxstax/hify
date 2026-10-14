package com.hify.workflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record HttpNodeConfig(
    @NotBlank(message = "HTTP 节点 url 不能为空")
    @Size(max = 2000, message = "url 长度不能超过 2000")
    String url,
    @NotNull(message = "HTTP 节点 method 不能为空")
    HttpMethod method,
    @NotBlank(message = "HTTP 节点 outputVariable 不能为空")
    @Size(max = 64, message = "outputVariable 长度不能超过 64")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$",
        message = "outputVariable 必须以字母开头，且只能包含字母、数字和下划线")
    String outputVariable) implements NodeConfig {
}
