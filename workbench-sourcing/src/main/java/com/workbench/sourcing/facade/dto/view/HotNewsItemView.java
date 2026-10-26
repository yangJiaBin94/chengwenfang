package com.workbench.sourcing.facade.dto.view;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 已抓取信源条目视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsItemView {
  /** 条目 ID */
  private String id;
  /** 主题 ID */
  private String themeId;
  /** 频道 ID */
  private String channelId;
  /** 外部 ID */
  private String externalId;
  /** 标题 */
  private String title;
  /** 链接 */
  private String url;
  /** 摘要 */
  private String summary;
  /** 作者 */
  private String author;
  /** 发布时间 */
  private LocalDateTime publishedAt;
  /** 内容哈希 */
  private String contentHash;
  /** 媒体类型 article/video */
  private String mediaType;
  /** 生命周期 ready/pending_parse/discarded */
  private String lifecycleStatus;
  /** 弃用原因；未弃用时为空 */
  private String discardReason;
  /** 内容意图 knowledge/marketing */
  private String contentIntent;
}
