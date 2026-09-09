package com.hify.provider.dto;

import lombok.Data;

/**
 * Outcome of a connectivity probe against one provider.
 * On failure {@code errorMessage} carries a classified, credential-free reason
 * (e.g. "TIMEOUT: llm io timeout: url=..." / "AUTH_FAILED: ... 401 ...").
 */
@Data
public class ConnectionTestResult {

  private boolean success;
  private long latencyMs;
  /** Models advertised by the probe endpoint; 0 on failure. */
  private int modelCount;
  private String errorMessage;

  public static ConnectionTestResult success(long latencyMs, int modelCount) {
    ConnectionTestResult r = new ConnectionTestResult();
    r.setSuccess(true);
    r.setLatencyMs(latencyMs);
    r.setModelCount(modelCount);
    return r;
  }

  public static ConnectionTestResult failure(long latencyMs, String errorMessage) {
    ConnectionTestResult r = new ConnectionTestResult();
    r.setSuccess(false);
    r.setLatencyMs(latencyMs);
    r.setErrorMessage(errorMessage);
    return r;
  }
}
