package com.hify.workflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.dto.WorkflowNodeReq;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NodeConfigCompatibilityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private final NodeConfigParser parser = new NodeConfigParser(objectMapper, validator);

  @Test
  void shouldMapLegacyHttpValueToApiCall() {
    assertEquals(NodeType.API_CALL, NodeType.fromExternalValue("HTTP"));
    assertEquals(NodeType.API_CALL, NodeType.fromExternalValue("API_CALL"));
  }

  @Test
  void shouldAcceptLegacyHttpInRequestValidation() throws Exception {
    WorkflowNodeReq request = new WorkflowNodeReq();
    request.setNodeKey("api");
    request.setName("API call");
    request.setNodeType("HTTP");
    request.setConfig(objectMapper.readTree("{}"));
    request.setConfigVersion(1);
    request.setSortOrder(0);

    assertTrue(validator.validate(request).isEmpty());
  }

  @Test
  void shouldParseApiCallConfigAndNormalizeNullHeaders() throws Exception {
    JsonNode config = objectMapper.readTree("""
        {
          "url": "https://example.com/api",
          "method": "GET",
          "outputVariable": "result"
        }
        """);

    ApiCallConfig parsed = (ApiCallConfig) parser.parse(NodeType.API_CALL, 1, config);

    assertTrue(parsed.headers().isEmpty());
    assertThrows(UnsupportedOperationException.class,
        () -> parsed.headers().put("Authorization", "token"));
  }

  @Test
  void shouldPreserveHeaderOrderInReadOnlyCopy() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-First", "1");
    headers.put("X-Second", "2");

    ApiCallConfig config = new ApiCallConfig(
        "https://example.com/api", HttpMethod.POST, headers, "result");
    headers.put("X-Third", "3");

    assertEquals(java.util.List.of("X-First", "X-Second"),
        java.util.List.copyOf(config.headers().keySet()));
    assertThrows(UnsupportedOperationException.class,
        () -> config.headers().put("X-Fourth", "4"));
  }

  @Test
  void shouldParseLegacyEndOutputExpressionAsOutputVariable() throws Exception {
    JsonNode config = objectMapper.readTree("""
        {"outputExpression": "text.output"}
        """);

    EndNodeConfig parsed = (EndNodeConfig) parser.parse(NodeType.END, 1, config);

    assertEquals("text.output", parsed.outputVariable());
    assertEquals("text.output", parser.serialize(parsed).get("outputVariable").asText());
  }

  @Test
  void shouldSerializeCanonicalApiCallType() throws Exception {
    String json = objectMapper.writeValueAsString(NodeType.fromExternalValue("HTTP"));

    assertEquals("\"API_CALL\"", json);
  }
}
