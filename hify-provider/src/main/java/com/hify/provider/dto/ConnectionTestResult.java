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
  /** Models newly inserted into model_config by a manual test. */
  private int syncedModelCount;
  private String errorMessage;

  public static ConnectionTestResult success(long latencyMs, int modelCount) {
    return success(latencyMs, modelCount, 0);
  }

  public static ConnectionTestResult success(long latencyMs, int modelCount,
      int syncedModelCount) {
    ConnectionTestResult r = new ConnectionTestResult();
    r.setSuccess(true);
    r.setLatencyMs(latencyMs);
    r.setModelCount(modelCount);
    r.setSyncedModelCount(syncedModelCount);
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
