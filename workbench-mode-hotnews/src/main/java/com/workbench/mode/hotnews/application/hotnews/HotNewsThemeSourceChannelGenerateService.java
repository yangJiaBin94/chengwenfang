package com.workbench.mode.hotnews.application.hotnews;

import com.workbench.mode.hotnews.facade.dto.view.HotNewsSourceChannelGenerateView;
import com.workbench.sourcing.facade.constant.HotNewsChannelLimits;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 来源管理：按主题用文本 AI 推荐渠道，连通性校验通过后落库。
 * <p>薄门面：对外保持原公开方法签名，内部委派提示词/解析/校验/落库协作类。
 */
@Service
@RequiredArgsConstructor
public class HotNewsThemeSourceChannelGenerateService {
  /** 单次 AI 推荐最多采纳的候选渠道数（不超过主题总上限）。 */
  static final int MAX_CHANNELS = Math.min(5, HotNewsChannelLimits.MAX_CHANNELS_PER_THEME);
  /** 单条网页渠道最多保留的 URL 数。 */
  static final int MAX_WEB_URLS = HotNewsChannelLimits.MAX_WEB_URLS_PER_CHANNEL;

  /** 落库编排。 */
  private final ChannelSuggestionPersister persister;
  /** 候选校验。 */
  private final ChannelSuggestionValidator validator;

  /**
   * 为指定主题 AI 生成并落库可用来源渠道。
   *
   * @param themeId 主题主键
   * @return 新建与跳过摘要
   */
  public HotNewsSourceChannelGenerateView generate(String themeId) {
    return persister.generate(themeId);
  }

  /**
   * 校验、去重并尝试创建单个候选。
   *
   * @param themeId       主题 id
   * @param candidate     AI 候选
   * @param existingIndex 已有渠道索引
   * @param acceptedKeys  本轮已占用的去重键
   * @param searchQueries 主题搜索词（SearXNG 回退）
   * @param githubQuery   主题 GitHub 查询（回退）
   * @return 处理结果
   */
  ChannelSuggestionProcessResult processCandidate(
      String themeId,
      ChannelSuggestionParser.AiChannelCandidate candidate,
      ChannelSuggestionExistingIndex existingIndex,
      Set<String> acceptedKeys,
      List<String> searchQueries,
      String githubQuery) {
    return validator.processCandidate(
        themeId, candidate, existingIndex, acceptedKeys, searchQueries, githubQuery);
  }

  /**
   * 解析 AI 候选列表。
   *
   * @param text 模型原文
   * @return 候选
   */
  static List<ChannelSuggestionParser.AiChannelCandidate> parseCandidates(String text) {
    return ChannelSuggestionParser.parseCandidates(text);
  }

  /**
   * 网页 URL 列表内去重并截断。
   *
   * @param urls 原始 URL
   * @return 去重截断列表
   */
  static List<String> dedupeAndCapWebUrls(List<String> urls) {
    return ChannelSuggestionValidator.dedupeAndCapWebUrls(urls);
  }

  /** 兼容旧引用的候选类型别名。 */
  public static final class AiChannelCandidate extends ChannelSuggestionParser.AiChannelCandidate {
    /**
     * @param name    名称
     * @param type    类型
     * @param baseUrl 地址
     * @param config  抓取配置
     */
    public AiChannelCandidate(
        String name,
        String type,
        String baseUrl,
        com.workbench.crawler.facade.dto.request.ChannelFetchConfig config) {
      super(name, type, baseUrl, config);
    }
  }

  /** 兼容旧引用的已有索引别名。 */
  public static final class ExistingIndex {
    /**
     * 从已有渠道视图构建索引。
     *
     * @param channels 已有渠道
     * @return 索引
     */
    public static ChannelSuggestionExistingIndex from(
        List<com.workbench.sourcing.facade.dto.view.HotNewsChannelView> channels) {
      return ChannelSuggestionExistingIndex.from(channels);
    }
  }
}
