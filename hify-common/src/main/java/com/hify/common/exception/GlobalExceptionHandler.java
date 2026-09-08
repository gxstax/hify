package com.hify.common.exception;

import com.hify.common.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions raised in controllers/services into the unified
 * {@link Result} shape. Every response goes through {@link Result#fail}
 * with an {@link ErrorCode}; the HTTP status stays 200 and the business
 * code inside Result carries the error.
 *
 * <p>This advice is effective only if the Spring Boot app scans the
 * {@code com.hify} package tree (e.g. main class in {@code com.hify} or
 * {@code @SpringBootApplication(scanBasePackages = "com.hify")}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Expected business error: answer with the code carried by the exception. */
  @ExceptionHandler(BizException.class)
  public Result<Void> handleBizException(BizException e) {
    return Result.fail(e.getErrorCode(), e.getMessage());
  }

  /** {@code @Valid} body validation failed: answer as a parameter error. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(FieldError::getDefaultMessage)
        .orElse(ErrorCode.PARAM_ERROR.getMessage());
    return Result.fail(ErrorCode.PARAM_ERROR, message);
  }

  /** Unexpected error: log the stack trace, never leak details to the client. */
  @ExceptionHandler(Exception.class)
  public Result<Void> handleException(Exception e) {
    log.error("Unhandled exception", e);
    return Result.fail(ErrorCode.SYSTEM_ERROR);
  }
}
