package com.workbench.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 统一分页查询参数（page 从 1 开始）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageQuery {
  /** 页码，从 1 开始。 */
  @Builder.Default
  private int page = 1;
  /** 每页条数，默认 20，上限 200。 */
  @Builder.Default
  private int pageSize = 20;
  /** 可选关键字。 */
  private String keyword;

  /** 规范化页码与页大小。 */
  public PageQuery normalized() {
    int safePage = Math.max(1, page);
    int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, 200);
    return PageQuery.builder().page(safePage).pageSize(safeSize).keyword(keyword).build();
  }

  /** SQL/MyBatis 用的 offset。 */
  public int offset() {
    PageQuery n = normalized();
    return (n.getPage() - 1) * n.getPageSize();
  }
}
