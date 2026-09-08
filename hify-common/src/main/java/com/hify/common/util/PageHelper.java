package com.hify.common.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.dto.PageResult;

/**
 * Conversion helpers between frontend pagination params, MyBatis-Plus
 * {@link Page} and the {@link PageResult} envelope (CLAUDE.md conventions:
 * page starts at 1, pageSize defaults to 20 and caps at 100).
 *
 * <p>Plain static utils, not a Spring bean. Unrelated to the third-party
 * "PageHelper" pagination library.
 */
public final class PageHelper {

  /** Page size used when the caller passes none or an invalid value. */
  public static final int DEFAULT_PAGE_SIZE = 20;

  /** Hard cap for page size. */
  public static final int MAX_PAGE_SIZE = 100;

  private PageHelper() {
  }

  /**
   * Convert frontend pagination params to a MyBatis-Plus {@link Page}.
   * page &lt;= 0 is normalized to 1; pageSize &lt;= 0 becomes the default
   * and anything above the cap is clamped down.
   */
  public static <T> Page<T> toPage(int page, int pageSize) {
    long current = Math.max(page, 1);
    long size = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    return new Page<>(current, size);
  }

  /** Wrap a MyBatis-Plus pagination result into the API {@link PageResult}. */
  public static <T> PageResult<T> toPageResult(IPage<T> page) {
    return new PageResult<>(
        page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
  }
}
