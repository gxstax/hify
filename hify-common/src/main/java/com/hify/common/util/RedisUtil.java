package com.hify.common.util;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Thin convenience wrapper over {@link RedisTemplate} (String keys, JSON
 * values, see {@link com.hify.common.config.RedisConfig}).
 *
 * <p>Business modules inject this instead of touching RedisTemplate directly.
 * {@code get} returns the object with its original type (stored as {@code @class}
 * metadata); cast to the stored type when using it.
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

  private final RedisTemplate<String, Object> redisTemplate;

  /** Store without expiration. */
  public void set(String key, Object value) {
    redisTemplate.opsForValue().set(key, value);
  }

  /** Store with a TTL. */
  public void set(String key, Object value, Duration timeout) {
    redisTemplate.opsForValue().set(key, value, timeout);
  }

  /** Read the value stored under {@code key}, null when absent. */
  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T) redisTemplate.opsForValue().get(key);
  }

  /** Delete one key, true when the key existed. */
  public Boolean delete(String key) {
    return redisTemplate.delete(key);
  }

  /** (Re)set the TTL of an existing key, true when applied. */
  public Boolean expire(String key, Duration timeout) {
    return redisTemplate.expire(key, timeout);
  }
}
