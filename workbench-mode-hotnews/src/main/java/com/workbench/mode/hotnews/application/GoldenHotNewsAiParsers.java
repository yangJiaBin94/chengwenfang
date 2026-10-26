package com.workbench.mode.hotnews.application;

import com.workbench.mode.hotnews.application.research.ResearchKeywordPlan;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import java.util.Map;

/**
 * 黄金 fixture 解析桥接：暴露同包 package-private 解析方法给 golden 测试。
 */
public final class GoldenHotNewsAiParsers {
  private GoldenHotNewsAiParsers() {}

  /**
   * @param raw 助手文本
   * @return 关键词计划
   */
  public static ResearchKeywordPlan parseKeywordPlan(String raw) {
    return HotNewsTopicResearchAssistService.parseKeywordPlan(raw);
  }

  /**
   * @param raw 助手文本
   * @return id → 是否相关
   */
  public static Map<String, Boolean> parseRelevance(String raw) {
    return HotNewsTopicResearchAssistService.parseRelevance(raw);
  }

  /**
   * @param raw 助手文本
   * @return id → 译文
   */
  public static Map<String, ResearchTranslator.TranslatedText> parseTranslations(String raw) {
    return HotNewsTopicResearchAssistService.parseTranslations(raw);
  }

  /**
   * @param topicId 选题 ID
   * @param raw     助手文本
   * @return 骨架
   */
  public static HotNewsArticleSkeletonView parseSkeleton(String topicId, String raw) {
    return HotNewsTopicResearchAssistService.parseSkeleton(topicId, raw);
  }
}
