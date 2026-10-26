package com.workbench.mode.hotnews.facade.dto.request;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 雷达采集幸存者落库请求（事实速览已就绪，不含原文）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarSurvivorPersistRequest {
  /** 渠道 ID。 */
  private String channelId;
  /** URL。 */
  private String url;
  /** 标题。 */
  private String title;
  /** 作者。 */
  private String author;
  /** 发布时间。 */
  private LocalDateTime publishedAt;
  /** 事实速览（写入 summary）。 */
  private String factBrief;
  /** 媒体类型。 */
  private String mediaType;
  /** 来源站标签。 */
  private String siteLabel;
  /** 候选临时 ID（用于回写）。 */
  private String tempId;
}
