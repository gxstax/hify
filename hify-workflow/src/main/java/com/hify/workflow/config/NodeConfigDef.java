package com.hify.workflow.config;

public sealed interface NodeConfigDef permits StartNodeConfig, EndNodeConfig,
    LlmNodeConfig, ApiCallConfig, ConditionNodeConfig, KnowledgeConfig,
    TextNodeConfig {
}
