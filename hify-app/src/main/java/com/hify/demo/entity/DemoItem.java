package com.hify.demo.entity;

import com.hify.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Demo CRUD entity: only two business fields, the rest comes from BaseEntity. */
@Getter
@Setter
public class DemoItem extends BaseEntity {

  /** Item name. */
  private String name;

  /** Item status (demo field, arbitrary int semantics). */
  private Integer status;
}
