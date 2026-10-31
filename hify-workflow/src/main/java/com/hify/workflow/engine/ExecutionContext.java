package com.hify.workflow.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable variable context for one workflow run. */
public class ExecutionContext {

  private static final String START_USER_MESSAGE_KEY = "start.userMessage";

  private final Long workflowRunId;
  private final LinkedHashMap<String, Object> variables = new LinkedHashMap<>();

  public ExecutionContext(Long workflowRunId, String userMessage) {
    this.workflowRunId = workflowRunId;
    variables.put(START_USER_MESSAGE_KEY, userMessage);
  }

  public Long getWorkflowRunId() {
    return workflowRunId;
  }

  public void set(String nodeKey, String varName, Object value) {
    variables.put(variableKey(nodeKey, varName), value);
  }

  public Object get(String nodeKey, String varName) {
    return variables.get(variableKey(nodeKey, varName));
  }

  public Object get(String variableKey) {
    return variables.get(variableKey);
  }

  public String resolve(String template) {
    String resolved = template;
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      String placeholder = "{{" + entry.getKey() + "}}";
      resolved = resolved.replace(placeholder, String.valueOf(entry.getValue()));
    }
    return resolved;
  }

  public Map<String, Object> snapshot() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(variables));
  }

  private String variableKey(String nodeKey, String varName) {
    return nodeKey + "." + varName;
  }
}
