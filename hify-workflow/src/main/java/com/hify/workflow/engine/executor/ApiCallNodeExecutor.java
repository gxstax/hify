package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.workflow.config.ApiCallConfig;
import com.hify.workflow.config.NodeConfigDef;
import com.hify.workflow.constant.NodeType;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.entity.WorkflowNode;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApiCallNodeExecutor implements NodeExecutor {

  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  private final LlmHttpClient httpClient;

  @Override
  public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
    try {
      ApiCallConfig apiConfig = ExecutorSupport.requireConfig(
          config, ApiCallConfig.class, nodeType());
      String url = ctx.resolve(apiConfig.url());
      validateUrl(url);
      Map<String, String> headers = resolveHeaders(apiConfig.headers(), ctx);
      String response = httpClient.request(
          apiConfig.method().name(), url, headers, null, READ_TIMEOUT);
      ctx.set(node.getNodeKey(), apiConfig.outputVariable(), response == null ? "" : response);
    } catch (Exception e) {
      throw ExecutorSupport.wrap(node.getNodeKey(), e);
    }
  }

  @Override
  public String nodeType() {
    return NodeType.API_CALL.name();
  }

  private Map<String, String> resolveHeaders(Map<String, String> headers, ExecutionContext ctx) {
    Map<String, String> resolved = new LinkedHashMap<>();
    headers.forEach((name, value) -> resolved.put(name, ctx.resolve(value)));
    return resolved;
  }

  private void validateUrl(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new BizException(ErrorCode.PARAM_ERROR, "API_CALL 节点 url 格式错误", e);
    }
    String scheme = uri.getScheme();
    if (scheme == null || uri.getHost() == null
        || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
      throw new BizException(ErrorCode.PARAM_ERROR,
          "API_CALL 节点 url 必须是有效的 HTTP/HTTPS 地址");
    }
  }
}
