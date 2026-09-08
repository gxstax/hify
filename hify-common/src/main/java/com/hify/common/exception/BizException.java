package com.hify.common.exception;

import lombok.Getter;

/**
 * Business exception carrying an {@link ErrorCode}.
 *
 * <p>Service code throws this instead of hard-coding error codes or messages.
 * The displayed message defaults to {@code ErrorCode.message} and can be
 * overridden with a custom one per occurrence (the original code message
 * stays available via {@link #getErrorCode()}).
 */
@Getter
public class BizException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;

  public BizException(ErrorCode errorCode) {
    this(errorCode, errorCode.getMessage());
  }

  /** Custom message overrides the default one carried by the error code. */
  public BizException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
