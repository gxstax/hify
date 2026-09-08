package com.hify.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Common columns shared by every table (CLAUDE.md database conventions):
 * bigint auto-increment {@code id}, {@code created_at}/{@code updated_at}
 * auto-filled by {@link com.hify.common.config.MyMetaObjectHandler}, and
 * {@code deleted} tinyint logical delete (1 = deleted, 0 = normal).
 *
 * <p>Every module entity extends this class instead of repeating the fields.
 */
@Getter
@Setter
public abstract class BaseEntity {

  /** Primary key, bigint auto-increment per convention. */
  @TableId(type = IdType.AUTO)
  private Long id;

  /** Creation time, filled on insert only. */
  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createdAt;

  /** Last update time, filled on insert and update. */
  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updatedAt;

  /** Logical delete flag; 0 = normal, 1 = deleted. Initialized so that new
   *  entities insert 0 even without the DB default. */
  @TableLogic
  private Integer deleted = 0;
}
