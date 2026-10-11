package com.hify.controller;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports the availability of the two databases and Redis. */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

  private static final Logger log = LoggerFactory.getLogger(HealthController.class);

  private static final String UP = "UP";
  private static final String DOWN = "DOWN";
  private static final String SKIPPED = "skipped";

  private final DataSource mysqlDataSource;
  private final DataSource pgvectorDataSource;
  private final Optional<RedisTemplate<String, Object>> redisTemplate;

  public HealthController(
      @Qualifier("mysqlDataSource") DataSource mysqlDataSource,
      @Qualifier("pgvectorDataSource") DataSource pgvectorDataSource,
      Optional<RedisTemplate<String, Object>> redisTemplate) {
    this.mysqlDataSource = mysqlDataSource;
    this.pgvectorDataSource = pgvectorDataSource;
    this.redisTemplate = redisTemplate;
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> health() {
    Map<String, Object> components = new LinkedHashMap<>();
    components.put("mysql", checkDatabase(mysqlDataSource, "mysql"));
    components.put("redis", checkRedis());
    components.put("pgvector", checkPgvector());

    boolean allUp = components.values().stream()
        .allMatch(value -> UP.equals(value) || SKIPPED.equals(value));

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", allUp ? UP : DOWN);
    body.put("components", components);

    HttpStatus status = allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status).body(body);
  }

  private Object checkDatabase(DataSource dataSource, String component) {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
      statement.executeQuery("SELECT 1");
      return UP;
    } catch (Exception e) {
      log.warn("action=health_check component={} error={}", component, e.getMessage());
      return Map.of("status", DOWN, "error", e.getMessage());
    }
  }

  private Object checkRedis() {
    if (redisTemplate.isEmpty()) {
      return SKIPPED;
    }
    try {
      String pong = redisTemplate.get().getConnectionFactory().getConnection().ping();
      return "PONG".equalsIgnoreCase(pong) ? UP : DOWN;
    } catch (Exception e) {
      log.warn("action=health_check component=redis error={}", e.getMessage());
      return Map.of("status", DOWN, "error", e.getMessage());
    }
  }

  private Object checkPgvector() {
    try (Connection connection = pgvectorDataSource.getConnection();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(
             "SELECT 1 FROM pg_extension WHERE extname = 'vector'")) {
      if (resultSet.next()) {
        return UP;
      }
      return Map.of("status", DOWN, "error", "pgvector extension is not installed");
    } catch (Exception e) {
      log.warn("action=health_check component=pgvector error={}", e.getMessage());
      return Map.of("status", DOWN, "error", e.getMessage());
    }
  }
}
