package com.hify.workflow.config;

public sealed interface NodeConfig permits StartNodeConfig, EndNodeConfig,
    LlmNodeConfig, HttpNodeConfig, ConditionNodeConfig, KnowledgeNodeConfig,
    TextNodeConfig {
}
