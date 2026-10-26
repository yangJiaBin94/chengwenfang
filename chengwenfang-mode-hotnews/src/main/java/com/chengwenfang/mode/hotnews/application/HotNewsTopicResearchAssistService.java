package com.chengwenfang.mode.hotnews.application;

import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.application.research.ResearchKeywordPlan;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 选题资料补充的 AI 辅助门面：关键词、相关性、翻译与文章骨架。
 * <p>对外保持原公开方法签名，内部委派协作类。
 */
@Service
@RequiredArgsConstructor
public class HotNewsTopicResearchAssistService {

  /** 检索关键词提取。 */
  private final ResearchKeywordExtractor keywords;
  /** 相关性核验。 */
  private final ResearchRelevanceFilter relevance;
  /** 非中文翻译。 */
  private final ResearchTranslator translator;
  /** 文章骨架拟定。 */
  private final ArticleSkeletonBuilder skeletons;

  /**
   * 选取可用于骨架/成文的参考资料：未解析成功的视频跳过。
   *
   * @param items 资料池条目
   * @return 过滤后的参考列表
   */
  public static List<HotNewsResearchItemView> filterArticleReferences(
      List<HotNewsResearchItemView> items) {
    return ResearchRelevanceFilter.filterArticleReferences(items);
  }

  /**
   * 根据选题与参考资料拟定文章骨架；AI 失败时回退启发式骨架。
   *
   * @param topicId       选题 ID
   * @param topicTitle    选题标题
   * @param topicSummary  选题摘要
   * @param researchItems 资料池条目
   * @param themeContext  主题读者/边界等上下文；可空
   * @return 文章骨架视图
   */
  public HotNewsArticleSkeletonView buildArticleSkeleton(
      String topicId,
      String topicTitle,
      String topicSummary,
      List<HotNewsResearchItemView> researchItems,
      String themeContext) {
    return skeletons.buildArticleSkeleton(
        topicId, topicTitle, topicSummary, researchItems, themeContext);
  }

  /**
   * AI 提取检索主体与关键词；失败时回退规则提取。
   *
   * @param title   选题标题
   * @param summary 选题摘要，可空
   * @return 检索计划
   */
  public ResearchKeywordPlan extractKeywords(String title, String summary) {
    return keywords.extractKeywords(title, summary);
  }

  /**
   * 由检索计划展开为实际搜索查询（主体词 + 官网/声明等角度）。
   *
   * @param plan 关键词计划
   * @return 去重后的查询列表
   */
  public List<String> buildSearchQueries(ResearchKeywordPlan plan) {
    return keywords.buildSearchQueries(plan);
  }

  /**
   * AI 核验候选资料是否与选题同一话题；不相关则剔除。
   *
   * @param title      选题标题
   * @param summary    选题摘要
   * @param plan       检索计划（含消歧）
   * @param candidates 候选资料
   * @return 相关资料子集（保持原顺序）
   */
  public List<ResearchCandidate> filterRelevant(
      String title,
      String summary,
      ResearchKeywordPlan plan,
      List<ResearchCandidate> candidates) {
    return relevance.filterRelevant(title, summary, plan, candidates);
  }

  /**
   * 将非中文标题/摘要译为简体中文；失败则保留原文。
   *
   * @param candidates 候选资料
   * @return 就地更新后的列表（同一实例）
   */
  public List<ResearchCandidate> translateNonChinese(List<ResearchCandidate> candidates) {
    return translator.translateNonChinese(candidates);
  }

  /**
   * 解析关键词计划 JSON。
   *
   * @param raw AI 原文
   * @return 计划；解析失败 null
   */
  static ResearchKeywordPlan parseKeywordPlan(String raw) {
    return ResearchKeywordExtractor.parseKeywordPlan(raw);
  }

  /**
   * 解析相关性判断 JSON。
   *
   * @param raw AI 原文
   * @return id → relevant
   */
  static Map<String, Boolean> parseRelevance(String raw) {
    return ResearchRelevanceFilter.parseRelevance(raw);
  }

  /**
   * 解析翻译结果 JSON。
   *
   * @param raw AI 原文
   * @return id → 译后文本
   */
  static Map<String, ResearchTranslator.TranslatedText> parseTranslations(String raw) {
    return ResearchTranslator.parseTranslations(raw);
  }

  /**
   * 解析骨架 JSON。
   *
   * @param topicId 选题 ID
   * @param raw     AI 原文
   * @return 骨架视图；解析失败 null
   */
  static HotNewsArticleSkeletonView parseSkeleton(String topicId, String raw) {
    return ArticleSkeletonBuilder.parseSkeleton(topicId, raw);
  }
}
