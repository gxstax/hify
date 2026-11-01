package com.hify.common.exception;

/**
 * Business error codes. Ranges are assigned per CLAUDE.md:
 * <ul>
 *   <li>1000-1999 common (parameter, auth, system, ...)</li>
 *   <li>2000-2999 provider, 3000-3999 agent, 4000-4999 chat, ...</li>
 * </ul>
 * Module-specific codes (2000+) are defined in each module's own exception package.
 *
 * <p>Lombok does not handle enums well (no constructor/getter generation),
 * so this enum is written without it.
 */
public enum ErrorCode {

  /** Request parameter is missing or malformed. */
  PARAM_ERROR(1000, "参数错误"),

  /** User is not authenticated. */
  UNAUTHORIZED(1001, "未授权"),

  /** Requested resource does not exist. */
  NOT_FOUND(1002, "资源不存在"),

  /** Unexpected server-side failure. */
  SYSTEM_ERROR(1999, "系统内部错误"),

  MCP_SERVER_NOT_FOUND(5000, "MCP Server 不存在"),
  MCP_SERVER_NAME_DUPLICATE(5001, "MCP Server 名称已存在"),
  MCP_ENDPOINT_INVALID(5002, "MCP endpoint 不合法"),
  MCP_SERVER_DISABLED(5003, "MCP Server 已禁用"),
  MCP_SERVER_CONNECTION_FAILED(5004, "MCP Server 连接失败"),
  MCP_TOOL_NOT_FOUND(5005, "MCP 工具不存在"),
  MCP_TOOL_CALL_FAILED(5006, "MCP 工具调用失败"),
  MCP_SERVER_IN_USE(5007, "MCP Server 正被 Agent 使用");

  private final int code;

  /** Default human-readable message, may be overridden in BizException. */
  private final String message;

  ErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
