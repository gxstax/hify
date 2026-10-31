package com.hify.workflow.engine.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.knowledge.dto.KnowledgeChunk;
import com.hify.knowledge.service.KnowledgeService;
import com.hify.provider.service.LlmInvocationService;
import com.hify.workflow.config.ApiCallConfig;
import com.hify.workflow.config.ConditionNodeConfig;
import com.hify.workflow.config.HttpMethod;
import com.hify.workflow.config.KnowledgeConfig;
import com.hify.workflow.config.LlmNodeConfig;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NodeExecutorsTest {

  @Test
  void llmExecutorShouldResolvePromptAndWriteContent() {
    LlmInvocationService service = mock(LlmInvocationService.class);
    when(service.invoke(7L, "问题：退款")).thenReturn("请提交订单号");
    LlmNodeExecutor executor = new LlmNodeExecutor(service);
    ExecutionContext context = new ExecutionContext(1L, "退款");

    executor.execute(node("llm"),
        new LlmNodeConfig(7L, "问题：{{start.userMessage}}", "answer"), context);

    assertEquals("请提交订单号", context.get("llm", "answer"));
  }

  @Test
  void conditionExecutorShouldEvaluateComparisonAndBooleanLiteral() {
    ConditionNodeExecutor executor = new ConditionNodeExecutor();
    ExecutionContext context = new ExecutionContext(1L, "你好");
    context.set("classify", "intent", "售后");

    executor.execute(node("condition"),
        new ConditionNodeConfig("'{{classify.intent}}' == \"售后\"", "matched"), context);
    executor.execute(node("literal"),
        new ConditionNodeConfig("false", "matched"), context);

    assertEquals(true, context.get("condition", "matched"));
    assertEquals(false, context.get("literal", "matched"));
  }

  @Test
  void conditionExecutorShouldWrapInvalidExpression() {
    ConditionNodeExecutor executor = new ConditionNodeExecutor();

    BizException exception = assertThrows(BizException.class,
        () -> executor.execute(node("condition"),
            new ConditionNodeConfig("unsupported", "matched"),
            new ExecutionContext(1L, "你好")));

    assertEquals(ErrorCode.PARAM_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("节点 condition 执行失败"));
    assertTrue(exception.getCause() instanceof BizException);
  }

  @Test
  void apiCallExecutorShouldResolveUrlAndHeaders() {
    LlmHttpClient client = mock(LlmHttpClient.class);
    when(client.request(eq("GET"), eq("https://example.com/orders/42"), any(),
        eq(null), eq(Duration.ofSeconds(60)))).thenReturn("ok");
    ApiCallNodeExecutor executor = new ApiCallNodeExecutor(client);
    ExecutionContext context = new ExecutionContext(1L, "ignored");
    context.set("input", "orderId", 42);
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-Order", "{{input.orderId}}");

    executor.execute(node("api"), new ApiCallConfig(
        "https://example.com/orders/{{input.orderId}}", HttpMethod.GET, headers, "body"), context);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> headerCaptor = ArgumentCaptor.forClass(Map.class);
    verify(client).request(eq("GET"), eq("https://example.com/orders/42"),
        headerCaptor.capture(), eq(null), eq(Duration.ofSeconds(60)));
    assertEquals(Map.of("X-Order", "42"), headerCaptor.getValue());
    assertEquals("ok", context.get("api", "body"));
  }

  @Test
  void knowledgeExecutorShouldRenderChunksInOrder() {
    KnowledgeService service = mock(KnowledgeService.class);
    when(service.searchChunks(3L, "退款", 2)).thenReturn(List.of(
        new KnowledgeChunk(1, 3, 10, "FAQ", 0, "第一段", 5, 0.9),
        new KnowledgeChunk(2, 3, 11, "手册", 2, "第二段", 5, 0.8)));
    KnowledgeNodeExecutor executor = new KnowledgeNodeExecutor(service);
    ExecutionContext context = new ExecutionContext(1L, "退款");

    executor.execute(node("knowledge"),
        new KnowledgeConfig(3L, "{{start.userMessage}}", 2, "references"), context);

    assertEquals("[来源 1 | 文档: FAQ | 分块: 1]\n第一段\n\n"
        + "[来源 2 | 文档: 手册 | 分块: 3]\n第二段",
        context.get("knowledge", "references"));
  }

  @Test
  void registryShouldResolveAliasAndRejectUnknownType() {
    NodeExecutor apiExecutor = mock(NodeExecutor.class);
    when(apiExecutor.nodeType()).thenReturn("API_CALL");
    NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(apiExecutor));

    assertSame(apiExecutor, registry.get("HTTP"));
    BizException exception = assertThrows(BizException.class,
        () -> registry.get("UNKNOWN"));
    assertEquals(ErrorCode.SYSTEM_ERROR, exception.getErrorCode());
  }

  @Test
  void registryShouldRejectDuplicateTypes() {
    NodeExecutor first = mock(NodeExecutor.class);
    NodeExecutor second = mock(NodeExecutor.class);
    when(first.nodeType()).thenReturn("LLM");
    when(second.nodeType()).thenReturn("LLM");

    assertThrows(IllegalStateException.class,
        () -> new NodeExecutorRegistry(List.of(first, second)));
  }

  private WorkflowNode node(String nodeKey) {
    WorkflowNode node = new WorkflowNode();
    node.setNodeKey(nodeKey);
    return node;
  }
}
