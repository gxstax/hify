package com.hify.provider.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.provider.entity.Provider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * OpenAI protocol: {@code /v1/chat/completions} with Bearer auth.
 *
 * <p>Handles both one-shot and streaming completions, including incremental
 * tool-call assembly (OpenAI streams the arguments as string fragments keyed
 * by index). Serves as the base for OpenAI-compatible providers — subclasses
 * only override {@link #supportedTypes()} (see {@link OpenAiCompatibleAdapter}).
 */
@Component
public class OpenAiAdapter extends AbstractProviderAdapter {

  private static final Set<String> TYPES = Set.of("OPENAI");

  public OpenAiAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
    super(llmHttpClient, objectMapper);
  }

  @Override
  public Set<String> supportedTypes() {
    return TYPES;
  }

  @Override
  public List<String> listModels(Provider provider) {
    String body = llmHttpClient.get(
        v1ModelsUrl(provider.getBaseUrl()),
        bearerHeaders(provider),
        PROBE_TIMEOUT);
    return parseModelIds(body, "data", "id");
  }

  @Override
  public ChatResponse chat(Provider provider, ChatRequest request) {
    String body = llmHttpClient.post(
        chatCompletionsUrl(provider),
        bearerHeaders(provider),
        toJson(payload(request, false)));
    return parseCompletion(body);
  }

  @Override
  public void streamChat(Provider provider, ChatRequest request, ChatStreamCallback callback) {
    StreamAccumulator accumulator = new StreamAccumulator();
    llmHttpClient.stream(
        chatCompletionsUrl(provider),
        bearerHeaders(provider),
        toJson(payload(request, true)),
        line -> accumulator.accept(line, callback));
    callback.onComplete(accumulator.toResponse());
  }

  // ------------------------------------------------------------------
  // Wire format (OpenAI)
  // ------------------------------------------------------------------

  private String chatCompletionsUrl(Provider provider) {
    return v1Url(provider.getBaseUrl(), "/chat/completions");
  }

  private Map<String, Object> payload(ChatRequest req, boolean stream) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", req.getModel());
    body.put("stream", stream);
    body.put("messages", toOpenAiMessages(req.getMessages()));
    if (req.getTemperature() != null) {
      body.put("temperature", req.getTemperature());
    }
    if (req.getMaxTokens() != null) {
      body.put("max_tokens", req.getMaxTokens());
    }
    // Long-tail params merge last; model/stream/messages can not be overridden
    if (req.getExtraParams() != null && !req.getExtraParams().isEmpty()) {
      body.putAll(req.getExtraParams());
    }
    if (req.getTools() != null && !req.getTools().isEmpty()) {
      body.put("tools", toOpenAiTools(req.getTools()));
    }
    return body;
  }

  private List<Map<String, Object>> toOpenAiMessages(List<ChatMessage> messages) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (ChatMessage m : messages) {
      Map<String, Object> msg = new LinkedHashMap<>();
      msg.put("role", m.getRole());
      msg.put("content", m.getContent() == null ? "" : m.getContent());
      if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
        msg.put("tool_calls", m.getToolCalls().stream().map(this::toOpenAiToolCall).toList());
      }
      if (m.getToolCallId() != null) {
        msg.put("tool_call_id", m.getToolCallId());
      }
      out.add(msg);
    }
    return out;
  }

  private Map<String, Object> toOpenAiToolCall(ToolCall call) {
    Map<String, Object> function = new LinkedHashMap<>();
    function.put("name", call.getName());
    function.put("arguments", call.getArguments() == null ? "{}" : call.getArguments());
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("id", call.getId());
    node.put("type", "function");
    node.put("function", function);
    return node;
  }

  private List<Map<String, Object>> toOpenAiTools(List<ToolSpec> tools) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (ToolSpec tool : tools) {
      Map<String, Object> function = new LinkedHashMap<>();
      function.put("name", tool.getName());
      function.put("description", tool.getDescription());
      function.put("parameters", tool.getParameters() == null
          ? Map.of("type", "object", "properties", Map.of())
          : tool.getParameters());
      Map<String, Object> node = new LinkedHashMap<>();
      node.put("type", "function");
      node.put("function", function);
      out.add(node);
    }
    return out;
  }

  private ChatResponse parseCompletion(String body) {
    JsonNode root = readTree(body);
    JsonNode choice = root.path("choices").path(0);
    JsonNode message = choice.path("message");
    ChatResponse resp = new ChatResponse();
    resp.setContent(message.path("content").isTextual() ? message.path("content").asText() : null);
    resp.setToolCalls(parseToolCalls(message.path("tool_calls")));
    resp.setFinishReason(choice.path("finish_reason").isTextual()
        ? choice.path("finish_reason").asText() : null);
    JsonNode usage = root.path("usage");
    if (usage.isObject()) {
      resp.setPromptTokens(intOrNull(usage.path("prompt_tokens")));
      resp.setCompletionTokens(intOrNull(usage.path("completion_tokens")));
    }
    return resp;
  }

  private List<ToolCall> parseToolCalls(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }
    List<ToolCall> calls = new ArrayList<>();
    for (JsonNode tc : node) {
      ToolCall call = new ToolCall();
      call.setId(tc.path("id").asText(null));
      call.setName(tc.path("function").path("name").asText(null));
      call.setArguments(tc.path("function").path("arguments").asText(null));
      calls.add(call);
    }
    return calls;
  }

  private Integer intOrNull(JsonNode node) {
    return node.isNumber() ? node.asInt() : null;
  }

  /**
   * Aggregates SSE chunks into one {@link ChatResponse}: text deltas stream
   * through immediately; tool calls arrive as fragments that are concatenated
   * per index; usage/finish_reason are captured when present.
   */
  private final class StreamAccumulator {

    private final StringBuilder content = new StringBuilder();
    private final Map<Integer, ToolCallBuilder> toolCalls = new LinkedHashMap<>();
    private String finishReason;
    private Integer promptTokens;
    private Integer completionTokens;

    void accept(String line, ChatStreamCallback callback) {
      if (!line.startsWith("data:")) {
        return; // SSE comment/keep-alive or non-payload field
      }
      String data = line.substring("data:".length()).trim();
      if (data.isEmpty() || "[DONE]".equals(data)) {
        return;
      }
      JsonNode root = readTree(data);
      JsonNode choice = root.path("choices").path(0);
      JsonNode delta = choice.path("delta");

      JsonNode contentNode = delta.path("content");
      if (contentNode.isTextual() && !contentNode.asText().isEmpty()) {
        content.append(contentNode.asText());
        callback.onDelta(contentNode.asText());
      }

      JsonNode toolCallsNode = delta.path("tool_calls");
      if (toolCallsNode.isArray()) {
        for (JsonNode tc : toolCallsNode) {
          int index = tc.path("index").asInt(0);
          ToolCallBuilder builder = toolCalls.computeIfAbsent(index, i -> new ToolCallBuilder());
          if (tc.path("id").isTextual()) {
            builder.id = tc.path("id").asText();
          }
          JsonNode function = tc.path("function");
          if (function.path("name").isTextual()) {
            builder.name = function.path("name").asText();
          }
          if (function.path("arguments").isTextual()) {
            builder.arguments.append(function.path("arguments").asText());
          }
        }
      }

      if (choice.path("finish_reason").isTextual()) {
        finishReason = choice.path("finish_reason").asText();
      }
      JsonNode usage = root.path("usage");
      if (usage.isObject()) {
        promptTokens = intOrNull(usage.path("prompt_tokens"));
        completionTokens = intOrNull(usage.path("completion_tokens"));
      }
    }

    ChatResponse toResponse() {
      ChatResponse resp = new ChatResponse();
      resp.setContent(content.isEmpty() ? null : content.toString());
      resp.setToolCalls(toolCalls.values().stream().map(ToolCallBuilder::build).toList());
      resp.setFinishReason(finishReason);
      resp.setPromptTokens(promptTokens);
      resp.setCompletionTokens(completionTokens);
      return resp;
    }
  }

  /** Mutable accumulator for one streamed tool call. */
  private static final class ToolCallBuilder {
    private String id;
    private String name;
    private final StringBuilder arguments = new StringBuilder();

    ToolCall build() {
      ToolCall call = new ToolCall();
      call.setId(id);
      call.setName(name);
      call.setArguments(arguments.toString());
      return call;
    }
  }
}
