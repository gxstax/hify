package com.hify.provider.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * A model-provider access instance: one protocol template + one credential
 * set. Multiple rows of the same {@code type} are allowed (separate keys /
 * gateways), each owning its own {@link ModelConfig} list.
 *
 * <p>{@code autoResultMap = true} makes MyBatis-Plus build a result map that
 * carries the JSON type handler, so the column deserializes on reads too.
 */
@Getter
@Setter
@TableName(value = "provider", autoResultMap = true)
public class Provider extends BaseEntity {

  /** Unique access-instance name, e.g. "OpenAI 主 Key". */
  private String name;

  /** Protocol template: OPENAI / ANTHROPIC / OLLAMA / OPENAI_COMPATIBLE. */
  private String type;

  /** API base URL. */
  private String baseUrl;

  /** Credential payload; null for auth-free providers (OLLAMA). */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> authConfig;

    /** 备注 */
    private String description;

  /** 1 = enabled, 0 = disabled. */
  private Boolean enabled;
}
