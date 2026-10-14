package com.hify.workflow.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.constant.NodeType;
import jakarta.validation.ConstraintViolation;
import java.io.IOException;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeConfigParser {

  private static final int SUPPORTED_CONFIG_VERSION = 1;

  private final ObjectMapper objectMapper;
  private final Validator validator;

  public NodeConfig parse(NodeType nodeType, Integer configVersion, JsonNode config) {
    try {
      return parseInternal(nodeType, configVersion, config);
    } catch (BizException e) {
      throw e;
    } catch (IOException | IllegalArgumentException e) {
      throw new BizException(ErrorCode.PARAM_ERROR, "节点配置格式错误: " + safeMessage(e));
    }
  }

  public NodeConfig parseStored(NodeType nodeType, Integer configVersion, JsonNode config,
      Long nodeId) {
    try {
      return parseInternal(nodeType, configVersion, config);
    } catch (Exception e) {
      log.error("Invalid stored workflow node config: nodeId={}, nodeType={}",
          nodeId, nodeType, e);
      throw new BizException(ErrorCode.SYSTEM_ERROR, "工作流节点配置数据异常");
    }
  }

  public JsonNode serialize(NodeConfig config) {
    return objectMapper.valueToTree(config);
  }

  private NodeConfig parseInternal(NodeType nodeType, Integer configVersion, JsonNode config)
      throws IOException {
    if (nodeType == null) {
      throw new BizException(ErrorCode.PARAM_ERROR, "节点类型不能为空");
    }
    if (configVersion == null || configVersion != SUPPORTED_CONFIG_VERSION) {
      throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 configVersion=1");
    }
    if (config == null || !config.isObject()) {
      throw new BizException(ErrorCode.PARAM_ERROR, "节点 config 必须是 JSON 对象");
    }

    Class<? extends NodeConfig> configType = switch (nodeType) {
      case START -> StartNodeConfig.class;
      case END -> EndNodeConfig.class;
      case LLM -> LlmNodeConfig.class;
      case HTTP -> HttpNodeConfig.class;
      case CONDITION -> ConditionNodeConfig.class;
      case KNOWLEDGE -> KnowledgeNodeConfig.class;
      case TEXT -> TextNodeConfig.class;
    };
    NodeConfig parsed = objectMapper.readerFor(configType)
        .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .readValue(config);
    validate(parsed);
    return parsed;
  }

  private void validate(NodeConfig config) {
    Set<ConstraintViolation<NodeConfig>> violations = validator.validate(config);
    if (violations.isEmpty()) {
      return;
    }
    String message = violations.stream()
        .map(ConstraintViolation::getMessage)
        .sorted()
        .collect(Collectors.joining("；"));
    throw new BizException(ErrorCode.PARAM_ERROR, message);
  }

  private String safeMessage(Exception exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return "无法解析配置";
    }
    int lineBreak = message.indexOf('\n');
    return lineBreak < 0 ? message : message.substring(0, lineBreak);
  }
}
