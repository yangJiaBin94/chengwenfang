package com.chengwenfang.mode.hotnews.application.websearch;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.dto.view.WebSearchCitation;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 联网搜索 citation URL 闸门：规范化键 → 规范 URL / 标题。
 */
public final class WebSearchCitationIndex {

  /** 规范化 URL → citation 原始 URL。 */
  private final Map<String, String> canonicalByNormalized;
  /** 规范化 URL → citation 标题。 */
  private final Map<String, String> titleByNormalized;

  private WebSearchCitationIndex(
      Map<String, String> canonicalByNormalized,
      Map<String, String> titleByNormalized) {
    this.canonicalByNormalized = canonicalByNormalized;
    this.titleByNormalized = titleByNormalized;
  }

  /**
   * 由引用列表构建索引（同键保留先到）。
   *
   * @param citations 引用；可空
   * @return 索引；永不为 null
   */
  public static WebSearchCitationIndex from(List<WebSearchCitation> citations) {
    Map<String, String> canonicalByNormalized = new LinkedHashMap<>();
    Map<String, String> titleByNormalized = new LinkedHashMap<>();
    if (citations != null) {
      for (WebSearchCitation citation : citations) {
        if (citation == null || StrUtil.isBlank(citation.getUrl())) {
          continue;
        }
        String url = citation.getUrl().trim();
        String key = TopicResearchPlanner.normalizeUrl(url);
        if (StrUtil.isBlank(key)) {
          continue;
        }
        canonicalByNormalized.putIfAbsent(key, url);
        titleByNormalized.putIfAbsent(key, StrUtil.nullToEmpty(citation.getTitle()));
      }
    }
    return new WebSearchCitationIndex(canonicalByNormalized, titleByNormalized);
  }

  /**
   * @return 索引内条目数
   */
  public int size() {
    return canonicalByNormalized.size();
  }

  /**
   * 将候选 URL 解析为 citation 中的规范 URL 与标题；不在集合内则空。
   *
   * @param url 候选 URL
   * @return 解析结果
   */
  public Optional<ResolvedCitation> resolve(String url) {
    if (StrUtil.isBlank(url)) {
      return Optional.empty();
    }
    String key = TopicResearchPlanner.normalizeUrl(url);
    if (StrUtil.isBlank(key)) {
      return Optional.empty();
    }
    String canonical = canonicalByNormalized.get(key);
    if (StrUtil.isBlank(canonical)) {
      return Optional.empty();
    }
    return Optional.of(new ResolvedCitation(canonical, StrUtil.nullToEmpty(titleByNormalized.get(key))));
  }

  /**
   * 规范化 URL 对应的 citation 标题；无则空串。
   *
   * @param url 任意形态 URL
   * @return 标题
   */
  public String titleOf(String url) {
    return resolve(url).map(ResolvedCitation::title).orElse("");
  }

  /**
   * citation 闸门解析结果。
   *
   * @param url   citation 规范 URL
   * @param title citation 标题；可空串
   */
  public record ResolvedCitation(String url, String title) {}
}
