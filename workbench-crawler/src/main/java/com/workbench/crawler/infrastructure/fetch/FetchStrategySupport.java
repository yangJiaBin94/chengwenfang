package com.workbench.crawler.infrastructure.fetch;

import com.workbench.crawler.domain.ChannelFetchContext;
import com.workbench.crawler.facade.PublishTimeUtil;
import com.workbench.crawler.facade.dto.view.FetchedItemView;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 抓取策略公共工具：时间转换与时效过滤。
 */
public final class FetchStrategySupport {
  private FetchStrategySupport() {}

  /**
   * Rome {@link Date} 转本地时间。
   *
   * @param date 发布时间，可空
   * @return 本地时间；date 为 null 时返回 null
   */
  public static LocalDateTime toLocalDateTime(Date date) {
    return date == null ? null : new Timestamp(date.getTime()).toLocalDateTime();
  }

  /**
   * @see PublishTimeUtil#resolvePublishedAt(LocalDateTime, String)
   */
  public static LocalDateTime resolvePublishedAt(LocalDateTime published, String url) {
    return PublishTimeUtil.resolvePublishedAt(published, url);
  }

  /**
   * @see PublishTimeUtil#inferPublishedAtFromUrl(String)
   */
  public static LocalDateTime inferPublishedAtFromUrl(String url) {
    return PublishTimeUtil.inferPublishedAtFromUrl(url);
  }

  /**
   * @see PublishTimeUtil#isStale(LocalDateTime, Integer)
   */
  public static boolean isStale(LocalDateTime published, Integer hours) {
    return PublishTimeUtil.isStale(published, hours);
  }

  /**
   * 搜索类渠道时效过滤：仅丢弃过旧条目，无发布时间保留。
   *
   * @see PublishTimeUtil#isStaleOrUnknownForSearch(LocalDateTime, Integer)
   */
  public static boolean isStaleOrUnknownForSearch(LocalDateTime published, Integer hours) {
    return PublishTimeUtil.isStaleOrUnknownForSearch(published, hours);
  }

  /**
   * 解析超时毫秒。
   *
   * @param context 上下文
   * @return 超时毫秒
   */
  public static int timeoutMillis(ChannelFetchContext context) {
    return context.resolveTimeoutSeconds() * 1000;
  }

  /**
   * HTTP 失败时的简短说明（禁止塞整页 HTML/JSON，避免写入 job_events.message 爆列）。
   *
   * @param status HTTP 状态码
   * @param body   响应体，可空；仅取前缀作摘要
   * @return 短错误文案
   */
  public static String httpErrorMessage(int status, String body) {
    String snippet = body == null ? "" : body.replaceAll("\\s+", " ").trim();
    if (snippet.length() > 160) {
      snippet = snippet.substring(0, 160) + "…";
    }
    if (snippet.isBlank()) {
      return "HTTP " + status;
    }
    return "HTTP " + status + "：" + snippet;
  }

  /**
   * 构建统一条目。
   *
   * @param title       标题
   * @param url         链接
   * @param summary     摘要
   * @param author      作者
   * @param publishedAt 发布时间
   * @return 统一出站条目
   */
  public static FetchedItemView item(
      String title, String url, String summary, String author, LocalDateTime publishedAt) {
    return FetchedItemView.builder()
        .title(title)
        .url(url)
        .summary(summary)
        .author(author)
        .publishedAt(publishedAt)
        .build();
  }
}
