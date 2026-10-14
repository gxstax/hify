package com.hify.workflow.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record StartNodeConfig(
    @NotNull(message = "START 节点 inputs 不能为空")
    List<@Valid InputDefinition> inputs) implements NodeConfig {

  public StartNodeConfig {
    inputs = inputs == null ? null : List.copyOf(inputs);
  }
}
