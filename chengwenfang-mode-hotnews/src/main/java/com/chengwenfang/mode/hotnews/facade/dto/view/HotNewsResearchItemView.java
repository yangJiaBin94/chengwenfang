package com.chengwenfang.mode.hotnews.facade.dto.view;

import com.chengwenfang.mode.hotnews.domain.ResearchItemScoreDetails;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 调研材料条目视图。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsResearchItemView {
  /** 条目 ID */
  private String id;
  /** 运行 ID */
  private String runId;
  /** 调研运行 ID（兼容） */
  private String researchRunId;
  /** 选题 ID */
  private String topicId;
  /** 标题 */
  private String title;
  /** 链接 */
  private String url;
  /** 摘要 */
  private String summary;
  /** 摘要（前端兼容字段） */
  private String contentSummary;
  /** 评分 */
  private Integer score;
  /** 综合评分（兼容） */
  private Integer overallScore;
  /** 五维评分明细 */
  private ResearchItemScoreDetails scoreDetails;
  /** 类型 */
  private String kind;
  /** 来源渠道标签（如网页检索、热点原始来源） */
  private String sourceLabel;
  /** 站点来源（如 B站、YouTube、知乎） */
  private String siteLabel;
  /** 内容形态标签：文章 / 视频 */
  private String mediaLabel;
  /** 视频解析状态 */
  private String videoParseStatus;
  /** 当前或失败步骤 key */
  private String videoParseStep;
  /** 步骤中文名（展示用） */
  private String videoParseStepLabel;
  /** 失败原因 */
  private String videoParseError;
  /** 字幕/ASR 原文（查看结果时可选展示；visual 类型为空） */
  private String videoRawText;
  /** 解析成功时间 */
  private LocalDateTime videoParsedAt;
  /** 视频内容类型：speech / visual */
  private String videoContentKind;
  /** 视频时长（秒）；未探测到时为 null */
  private Integer videoDurationSeconds;
  /** 创建时间 */
  private LocalDateTime createdAt;
  /** 采集时间（前端兼容） */
  private LocalDateTime fetchedAt;
}
