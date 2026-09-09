package com.hify.provider.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * A model exposed by one provider access instance (provider 1:N model_config).
 * Credentials live on the provider row; per-model values here are API-level
 * (model id, context window) plus free-form {@code extra_params}.
 */
@Getter
@Setter
@TableName(value = "model_config", autoResultMap = true)
public class ModelConfig extends BaseEntity {

  /** Owning provider access instance, provider.id (app-layer FK). */
  private Long providerId;

  /** Display name, e.g. "GPT-4o". */
  private String name;

  /** Value passed to the API as the model identifier. */
  private String modelId;

  /** Context window size in tokens, when advertised. */
  private Integer contextSize;

  /** Model-level free-form extension params. */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> extraParams;

  /** 1 = enabled, 0 = disabled. */
  private Boolean enabled;
}
