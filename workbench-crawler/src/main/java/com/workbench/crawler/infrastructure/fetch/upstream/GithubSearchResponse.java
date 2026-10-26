package com.workbench.crawler.infrastructure.fetch.upstream;

import cn.hutool.core.annotation.Alias;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GitHub Search Repositories API 响应（仅解析所需字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubSearchResponse {
  /** 仓库列表。 */
  @Builder.Default
  private List<GithubRepoItem> items = new ArrayList<>();

  /** 单条仓库。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GithubRepoItem {
    /** 完整名 owner/repo。 */
    @Alias("full_name")
    private String fullName;
    /** 仓库短名。 */
    private String name;
    /** 页面 URL。 */
    @Alias("html_url")
    private String htmlUrl;
    /** 描述。 */
    private String description;
    /** 创建时间（ISO-8601）。 */
    @Alias("created_at")
    private String createdAt;
    /** 更新时间（ISO-8601）。 */
    @Alias("updated_at")
    private String updatedAt;
  }
}
