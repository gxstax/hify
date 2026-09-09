package com.hify.provider.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Health probe result, one row per provider (upsert semantics).
 *
 * <p>Deliberately NOT extending {@link com.hify.common.entity.BaseEntity}: the
 * table has no created_at / logical-delete columns — probes are high-frequency
 * writes kept out of the provider row so they never invalidate the provider
 * cache. {@code updated_at} is maintained by the business layer (or falls back
 * to the DB default); deletes are physical.
 *
 * <p>{@code status} values: UP / DOWN / DEGRADED / UNKNOWN.
 */
@Getter
@Setter
@TableName("provider_health")
public class ProviderHealth {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** provider.id (app-layer FK), unique per table constraint. */
  private Long providerId;

  /** UP / DOWN / DEGRADED / UNKNOWN. */
  private String status;

  /** When the last probe ran. */
  private LocalDateTime lastCheckAt;

  /** When the last successful probe ran. */
  private LocalDateTime lastSuccessAt;

  /** Consecutive failure count. */
  private Integer failCount;

  /** Latency of the latest probe, ms. */
  private Integer latencyMs;

  /** Reason of the most recent failure. */
  private String errorMessage;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
