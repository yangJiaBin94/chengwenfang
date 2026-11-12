package com.chengwenfang.content.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主轨产出的知识索引快照（供知识库连接器读取）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOutputKnowledgeSnapshot {
  /** 产出 ID。 */
  private String outputId;
  /** 作品 ID。 */
  private String workId;
  /** 模式 code。 */
  private String modeCode;
  /** 主题 ID。 */
  private String themeId;
  /** 标题。 */
  private String title;
  /** 渠道 code。 */
  private String channelCode;
  /** 当前版本号。 */
  private Integer currentVersionNo;
  /** 正文哈希。 */
  private String contentHash;
  /** 当前版本正文。 */
  private String content;
  /** 前端深链。 */
  private String deepLink;
}
