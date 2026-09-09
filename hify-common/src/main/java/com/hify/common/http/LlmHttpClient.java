package com.hify.common.http;

import com.hify.common.exception.LlmApiException;
import com.hify.common.exception.LlmApiException.LlmErrorType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for LLM provider APIs.
 *
 * <ul>
 *   <li>{@link #post}: plain request/response (connect 5s / read 60s), returns
 *       the raw response body as String.</li>
 *   <li>{@link #get}: same semantics for GET, with a caller-controlled read
 *       timeout — used by connectivity probes (10s).</li>
 *   <li>{@link #stream}: SSE-style streaming via OkHttp (connect 5s /
 *       read 120s), delivering each non-empty line to the callback.
 *       Blocking call — run it on the {@code llmExecutor} pool and let
 *       Resilience4j/TimeLimiter own the outer timeout.</li>
 * </ul>
 *
 * <p>Every call is logged with URL, duration and status. All failures are
 * translated to {@link LlmApiException} classified by {@link LlmErrorType}
 * (see CLAUDE.md retry rules). Header values never appear in logs.
 */
@Component
public class LlmHttpClient {

  private static final Logger log = LoggerFactory.getLogger(LlmHttpClient.class);

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT_POST = Duration.ofSeconds(60);
  private static final Duration READ_TIMEOUT_STREAM = Duration.ofSeconds(120);

  private final OkHttpClient okHttpClient;

  public LlmHttpClient() {
    this.okHttpClient = new OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT)
        .readTimeout(READ_TIMEOUT_STREAM)
        .build();
  }

  /**
   * POST {@code body} (usually JSON) to {@code url}, return the raw body.
   *
   * @throws LlmApiException classified per {@link LlmErrorType}
   */
  public String post(String url, Map<String, String> headers, String body) {
    return execute("POST", url, headers, body, READ_TIMEOUT_POST);
  }

  /**
   * GET {@code url} with a caller-controlled read timeout (connect timeout is
   * always 5s). Connectivity probes pass e.g. 10s here.
   *
   * @throws LlmApiException classified per {@link LlmErrorType}
   */
  public String get(String url, Map<String, String> headers, Duration readTimeout) {
    return execute("GET", url, headers, null, readTimeout);
  }

  /**
   * Stream a POST response line by line. Blocks until the stream ends, the
   * provider closes it, or an error occurs (thrown as {@link LlmApiException}).
   */
  public void stream(String url, Map<String, String> headers, String body,
      LlmStreamCallback callback) {
    long start = System.currentTimeMillis();
    log.info("llm-http method=stream url={}", url);

    MediaType mediaType = MediaType.parse(headers.getOrDefault("Content-Type", "application/json"));
    Request.Builder requestBuilder = new Request.Builder()
        .url(url)
        .post(RequestBody.create(body, mediaType));
    headers.forEach((name, value) -> {
      if (!"Content-Type".equalsIgnoreCase(name)) {
        requestBuilder.header(name, value);
      }
    });

    try (Response response = okHttpClient.newCall(requestBuilder.build()).execute()) {
      int status = response.code();
      if (!response.isSuccessful()) {
        String errorBody = response.body() == null ? "" : response.body().string();
        log.warn("llm-http method=stream url={} cost_ms={} status={} body={}",
            url, System.currentTimeMillis() - start, status, truncate(errorBody));
        throw toHttpException(url, status, errorBody, null);
      }

      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(
              java.util.Objects.requireNonNull(response.body()).byteStream(),
              StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.isEmpty()) {
            continue; // SSE separator blank lines carry no data
          }
          callback.onLine(line);
        }
      }
      log.info("llm-http method=stream url={} cost_ms={} status={}",
          url, System.currentTimeMillis() - start, status);
    } catch (SocketTimeoutException e) {
      log.warn("llm-http method=stream url={} cost_ms={} timeout",
          url, System.currentTimeMillis() - start);
      throw new LlmApiException(LlmErrorType.TIMEOUT,
          "llm stream timeout: " + url, e);
    } catch (IOException e) {
      log.warn("llm-http method=stream url={} cost_ms={} error={}",
          url, System.currentTimeMillis() - start, e.getMessage());
      throw toIoException(url, e);
    }
  }

  /**
   * Shared request/response execution with logging and error classification.
   * A RestTemplate with the requested timeout is created per call — the
   * timeout is a semantic parameter (60s completions vs 10s probes).
   */
  private String execute(String method, String url, Map<String, String> headers, String body,
      Duration readTimeout) {
    long start = System.currentTimeMillis();
    log.info("llm-http method={} url={}", method.toLowerCase(), url);

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
    factory.setReadTimeout((int) readTimeout.toMillis());
    RestTemplate restTemplate = new RestTemplate(factory);

    HttpHeaders httpHeaders = new HttpHeaders();
    headers.forEach(httpHeaders::set);
    HttpEntity<String> entity = body == null
        ? new HttpEntity<>(httpHeaders)
        : new HttpEntity<>(body, httpHeaders);

    try {
      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
      log.info("llm-http method={} url={} cost_ms={} status={}",
          method.toLowerCase(), url, System.currentTimeMillis() - start,
          response.getStatusCode().value());
      return response.getBody();
    } catch (RestClientResponseException e) {
      // Non-2xx: RestTemplate's default error handler throws with status + body
      log.warn("llm-http method={} url={} cost_ms={} status={} body={}",
          method.toLowerCase(), url, System.currentTimeMillis() - start,
          e.getStatusCode().value(), truncate(e.getResponseBodyAsString()));
      throw toHttpException(url, e.getStatusCode().value(), e.getResponseBodyAsString(), e);
    } catch (ResourceAccessException e) {
      // Connect/read failures and timeouts
      log.warn("llm-http method={} url={} cost_ms={} error={}",
          method.toLowerCase(), url, System.currentTimeMillis() - start, e.getMessage());
      throw toIoException(url, e);
    }
  }

  /** Map a non-2xx HTTP status to the matching error type. */
  private LlmApiException toHttpException(String url, int status, String body, Throwable cause) {
    LlmErrorType type = switch (status) {
      case 401, 403 -> LlmErrorType.AUTH_FAILED;
      case 429 -> LlmErrorType.RATE_LIMITED;
      case 408 -> LlmErrorType.TIMEOUT;
      case 400, 404, 422 -> LlmErrorType.INVALID_REQUEST;
      default -> status >= 500 ? LlmErrorType.SERVER_ERROR : LlmErrorType.INVALID_REQUEST;
    };
    String message = "llm api failed: url=" + url + " status=" + status
        + " body=" + truncate(body);
    return cause == null ? new LlmApiException(type, message)
        : new LlmApiException(type, message, cause);
  }

  /** Map a connection-level failure: timeout vs other network errors. */
  private LlmApiException toIoException(String url, Throwable cause) {
    boolean timeout = containsCause(cause, SocketTimeoutException.class);
    LlmErrorType type = timeout ? LlmErrorType.TIMEOUT : LlmErrorType.NETWORK_ERROR;
    String message = "llm io " + (timeout ? "timeout" : "error") + ": url=" + url;
    return new LlmApiException(type, message, cause);
  }

  private boolean containsCause(Throwable t, Class<? extends Throwable> target) {
    for (Throwable cur = t; cur != null; cur = cur.getCause()) {
      if (target.isInstance(cur)) {
        return true;
      }
    }
    return false;
  }

  /** Cap bodies in logs; never log header values. */
  private String truncate(String s) {
    if (s == null || s.isEmpty()) {
      return "";
    }
    return s.length() <= 500 ? s : s.substring(0, 500) + "...";
  }
}
