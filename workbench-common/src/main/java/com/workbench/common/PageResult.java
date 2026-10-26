package com.workbench.common;

import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一分页响应体。
 *
 * @param <T> 列表元素类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
  /** 当前页数据。 */
  @Builder.Default
  private List<T> items = Collections.emptyList();
  /** 总条数。 */
  private long total;
  /** 当前页码。 */
  private int page;
  /** 每页条数。 */
  private int pageSize;

  /** 由查询参数与列表构造。 */
  public static <T> PageResult<T> of(PageQuery query, List<T> items, long total) {
    PageQuery n = query == null ? new PageQuery() : query.normalized();
    return PageResult.<T>builder()
        .items(items == null ? List.of() : items)
        .total(total)
        .page(n.getPage())
        .pageSize(n.getPageSize())
        .build();
  }
}
