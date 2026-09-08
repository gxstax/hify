package com.hify.common.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Paginated data payload, nested inside {@link Result} as its {@code data}.
 *
 * <pre>{@code
 * { "code": 200, "message": "success",
 *   "data": { "list": [...], "total": 120, "page": 1, "pageSize": 20 } }
 * }</pre>
 *
 * @param <T> item type of the page
 */
@Data
@NoArgsConstructor
public class PageResult<T> {

  /** Page items; empty list instead of null when there is nothing. */
  private List<T> list = new ArrayList<>();

  /** Total matching records across all pages. */
  private long total;

  /** Current page number, starting from 1. */
  private int page;

  /** Page size, default 20, max 100. */
  private int pageSize;

  public PageResult(List<T> list, long total, int page, int pageSize) {
    this.list = list != null ? list : new ArrayList<>();
    this.total = total;
    this.page = page;
    this.pageSize = pageSize;
  }
}
