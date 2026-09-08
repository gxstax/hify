package com.hify.common.dto;

import com.hify.common.exception.ErrorCode;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified API response wrapper.
 *
 * <p>Every REST endpoint returns this shape:
 * <pre>{@code
 * { "code": 200, "message": "success", "data": {...} }
 * }</pre>
 *
 * @param <T> type of the payload in {@code data}
 */
@Data
@NoArgsConstructor
public class Result<T> {

  /** Successful response code (HTTP OK). */
  public static final int CODE_SUCCESS = 200;

  /** Default success message. */
  public static final String MESSAGE_SUCCESS = "success";

  /** Response code, see CLAUDE.md error-code ranges (1000-1999 etc.). */
  private int code;

  /** Human readable message, never null. */
  private String message;

  /** Payload; null when the object does not exist or on failure. */
  private T data;

  private Result(int code, String message, T data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  /** Success with no payload. */
  public static <T> Result<T> ok() {
    return ok(null);
  }

  /** Success with a payload. */
  public static <T> Result<T> ok(T data) {
    return new Result<>(CODE_SUCCESS, MESSAGE_SUCCESS, data);
  }

  /** Failure with the default message of the error code. */
  public static <T> Result<T> fail(ErrorCode errorCode) {
    return fail(errorCode, errorCode.getMessage());
  }

  /** Failure with a custom message overriding the error code default. */
  public static <T> Result<T> fail(ErrorCode errorCode, String message) {
    return new Result<>(errorCode.getCode(), message, null);
  }
}
