package com.chengwenfang.mode.hotnews.application.model;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.domain.ResearchItemScoreDetails;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchItemKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调研完成流程中的候选材料（汇集来源 + 网页检索），入库前内存结构。
 * <p>不对外暴露；落库对应 {@code TopicResearchItemEntity}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchCandidate {
  /** 标题。 */
  private String title;
  /** 链接；可为空（仅标题去重）。 */
  private String url;
  /** 摘要。 */
  private String summary;
  /** 评分。 */
  private int score;
  /** 分维评分明细；可空（AI 失败回退时无明细）。 */
  private ResearchItemScoreDetails scoreDetails;
  /** 来源类型。 */
  private ResearchItemKind kind;
  /** 视频时长（秒）；非视频或未探测到时为 null。 */
  private Integer videoDurationSeconds;

  /**
   * 去重键：优先 URL，无 URL 时用标题小写。
   *
   * @return {@code url:...} 或 {@code title:...}
   */
  public String dedupeKey() {
    String normalizedUrl = url == null ? "" : url.trim();
    if (StrUtil.isNotBlank(normalizedUrl)) {
      return "url:" + normalizedUrl;
    }
    String normalizedTitle = title == null ? "" : title.trim().toLowerCase();
    return "title:" + normalizedTitle;
  }

  /**
   * 规范化标题（小写 trim），用于同标题冲突聚合。
   *
   * @return 规范化标题；空标题返回空串
   */
  public String normalizedTitle() {
    return title == null ? "" : title.trim().toLowerCase();
  }
}
