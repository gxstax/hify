package com.hify.common.log;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Per-request logging:
 * <ul>
 *   <li>generates a {@code traceId} on request entry and puts it into the MDC,
 *       so every log line of the request carries it (see the
 *       {@code %X{traceId}} pattern in logback-spring.xml);</li>
 *   <li>logs method / path / status / duration on completion, at WARN level
 *       when the request was slow (&gt; 1s).</li>
 * </ul>
 *
 * <p>The MDC key is removed in a finally block so pooled threads never leak
 * a stale trace id into the next request.
 */
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

  /** MDC key consumed by the logback pattern and the logstash encoder. */
  public static final String TRACE_ID_KEY = "traceId";

  private static final Logger log = LoggerFactory.getLogger(RequestLogInterceptor.class);

  private static final long SLOW_THRESHOLD_MS = 1000;

  /** Request attribute holding the start timestamp (per-request, not thread-local). */
  private static final String ATTR_START_TIME =
      RequestLogInterceptor.class.getName() + ".startTime";

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
    MDC.put(TRACE_ID_KEY, UUID.randomUUID().toString().replace("-", ""));
    request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, @Nullable Exception ex) {
    try {
      Object startAttr = request.getAttribute(ATTR_START_TIME);
      long costMs = startAttr instanceof Long start
          ? System.currentTimeMillis() - start
          : -1;
      String line = String.format("http method=%s path=%s status=%d cost_ms=%d",
          request.getMethod(), request.getRequestURI(), response.getStatus(), costMs);

      if (costMs >= SLOW_THRESHOLD_MS) {
        log.warn("{} (slow)", line);
      } else {
        log.info(line);
      }
    } finally {
      MDC.remove(TRACE_ID_KEY);
    }
  }
}
