package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.constant.NodeType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NodeExecutorRegistry {

  private final Map<String, NodeExecutor> executors;

  public NodeExecutorRegistry(List<NodeExecutor> executorList) {
    Map<String, NodeExecutor> registered = new LinkedHashMap<>();
    for (NodeExecutor executor : executorList) {
      String type = normalizeRegisteredType(executor.nodeType());
      NodeExecutor existing = registered.putIfAbsent(type, executor);
      if (existing != null) {
        throw new IllegalStateException("重复的节点执行器: " + type);
      }
    }
    this.executors = Collections.unmodifiableMap(registered);
  }

  public NodeExecutor get(String type) {
    final String normalized;
    try {
      normalized = NodeType.fromExternalValue(type).name();
    } catch (IllegalArgumentException e) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, e.getMessage(), e);
    }
    NodeExecutor executor = executors.get(normalized);
    if (executor == null) {
      throw new BizException(ErrorCode.SYSTEM_ERROR, "未注册节点执行器: " + normalized);
    }
    return executor;
  }

  private String normalizeRegisteredType(String type) {
    if (type == null || type.isBlank()) {
      throw new IllegalStateException("节点执行器类型不能为空");
    }
    try {
      return NodeType.fromExternalValue(type.toUpperCase(Locale.ROOT)).name();
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("节点执行器类型无效: " + type, e);
    }
  }
}
