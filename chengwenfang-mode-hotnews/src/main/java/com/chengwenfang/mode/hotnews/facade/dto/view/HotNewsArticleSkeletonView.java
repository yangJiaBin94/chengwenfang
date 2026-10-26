package com.chengwenfang.mode.hotnews.facade.dto.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 选题文章骨架视图（限定范围、核心与结构）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsArticleSkeletonView {
  /** 选题 ID */
  private String topicId;
  /** 建议标题 */
  private String workingTitle;
  /** 文章范围 / 边界 */
  private String scope;
  /** 核心内容 / 主论点 */
  private String coreThesis;
  /** 核心事件一句话 */
  private String coreEvent;
  /** 核心洞察点（非流水账事实） */
  private List<String> keyInsights;
  /** 影响面 */
  private List<String> impacts;
  /** 读者应带走的信号/判断 */
  private List<String> readerSignals;
  /** 叙事形态（故事切入 / 冲突推进 / 对照等） */
  private String narrativeShape;
  /** 段落节奏与开法提示；避免编号式大标题 */
  private List<String> sectionPlan;
  /** 插图风格（按选题自定，由骨架配图 Skill 约束） */
  private String illustrationStyle;
  /** 封面文案与构图要点 */
  private String coverBrief;
  /**
   * 生图硬性要求：可直接拼进每条插图提示词（由骨架配图 Skill 在拟定骨架时写入）。
   */
  private String imagePromptRequirements;
  /** 工作区默认字数档（1500-2500 / 2500-4000 / 4000-6000） */
  private String recommendedWordCountOption;
  /** 工作区默认正文插图档（不含封面；2-3 / 3-5 / 6-8，至少 2 张） */
  private String recommendedImageCountOption;
  /** 整体架构（自然小标题短语，勿编号章节） */
  private List<String> architecture;
  /** 叙述流程 */
  private List<String> flow;
  /** 模块明细 */
  private List<Map<String, Object>> modules;
  /** 从资料池提炼的可选借鉴点 */
  private List<String> referenceHints;
  /** 更新时间 */
  private LocalDateTime updatedAt;
  /** 原始 JSON（兼容扩展字段） */
  private Map<String, Object> raw;
}
