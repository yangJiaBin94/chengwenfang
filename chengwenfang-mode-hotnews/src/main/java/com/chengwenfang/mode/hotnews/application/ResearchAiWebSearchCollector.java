package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.ai.facade.dto.view.WebSearchCitation;
import com.chengwenfang.ai.facade.enums.WebSearchMode;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.crawler.facade.MarketingPageFilter;
import com.chengwenfang.crawler.facade.dto.view.SearchHitView;
import com.chengwenfang.mode.hotnews.application.prompt.ResearchAiWebSearchPrompts;
import com.chengwenfang.mode.hotnews.application.research.ResearchAiWebSearchCollectResult;
import com.chengwenfang.mode.hotnews.application.research.ResearchAiWebSearchItem;
import com.chengwenfang.mode.hotnews.application.research.ResearchSourcePolicy;
import com.chengwenfang.mode.hotnews.application.websearch.WebSearchCitationIndex;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsResearchAgentLimits;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 资料补充：具备 WebSearch 时联网搜佐证资料；citations 定 URL，二次结构化后产出命中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchAiWebSearchCollector {

  /** 多模态门面。 */
  private final AiVisionFacade ai;

  /**
   * 按选题联网搜资料；不具备能力、无 citation 或失败时返回空命中并带 skipReason。
   *
   * @param topicTitle         选题标题
   * @param topicSummary       选题摘要
   * @param occupiedUrlKeys    已占用规范化 URL（原始来源等）
   * @param sourcePolicy       来源策略；可空
   * @return 采集结果；永不为 null
   */
  public ResearchAiWebSearchCollectResult collect(
      String topicTitle,
      String topicSummary,
      Set<String> occupiedUrlKeys,
      ResearchSourcePolicy sourcePolicy) {
    String title = StrUtil.nullToEmpty(topicTitle).trim();
    String summary = StrUtil.nullToEmpty(topicSummary).trim();
    if (StrUtil.isBlank(title) && StrUtil.isBlank(summary)) {
      return ResearchAiWebSearchCollectResult.builder()
          .used(false)
          .skipReason("选题标题与摘要皆空")
          .build();
    }
    if (!ai.supportsWebSearch()) {
      return ResearchAiWebSearchCollectResult.builder()
          .used(false)
          .skipReason("当前多模态配置不具备 WebSearch 能力")
          .build();
    }
    ResearchSourcePolicy policy = sourcePolicy == null
        ? ResearchSourcePolicy.builder().build()
        : sourcePolicy;
    try {
      VisionCompleteResult webResult = ai.complete(VisionCompleteRequest.builder()
          .systemPrompt(ResearchAiWebSearchPrompts.WEB_SEARCH_SYSTEM)
          .prompt(buildUserPrompt(title, summary, policy))
          .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_AI_WEB_SEARCH)
          .webSearchMode(WebSearchMode.ON)
          .timeoutSeconds(180)
          .build());
      List<WebSearchCitation> citations =
          webResult.getCitations() == null ? List.of() : webResult.getCitations();
      if (citations.isEmpty()) {
        log.info("资料补充 AI 联网未返回引用 title={}", title);
        return ResearchAiWebSearchCollectResult.builder()
            .used(true)
            .skipReason("未返回引用链接")
            .citationCount(0)
            .build();
      }
      List<ResearchAiWebSearchItem> items = structureItems(webResult.getText(), citations);
      List<SearchHitView> hits = toHits(items, occupiedUrlKeys, policy);
      log.info(
          "资料补充 AI 联网 used=true citations={} structured={} hits={} title={}",
          citations.size(),
          items.size(),
          hits.size(),
          title);
      return ResearchAiWebSearchCollectResult.builder()
          .used(true)
          .citationCount(citations.size())
          .hits(hits)
          .build();
    } catch (Exception exception) {
      String reason = "AI 联网搜资料失败：" + StrUtil.blankToDefault(exception.getMessage(), "unknown");
      log.warn("资料补充 AI 联网失败 title={} err={}", title, exception.getMessage());
      return ResearchAiWebSearchCollectResult.builder()
          .used(false)
          .skipReason(reason)
          .build();
    }
  }

  /**
   * 二次结构化并经 citation 闸门回填规范 URL。
   *
   * @param draftText 联网草稿
   * @param citations 引用列表
   * @return 闸门通过的条目
   */
  List<ResearchAiWebSearchItem> structureItems(String draftText, List<WebSearchCitation> citations) {
    WebSearchCitationIndex index = WebSearchCitationIndex.from(citations);
    if (index.size() == 0) {
      return List.of();
    }
    StringBuilder citationBlock = new StringBuilder();
    for (WebSearchCitation citation : citations) {
      if (citation == null || StrUtil.isBlank(citation.getUrl())) {
        continue;
      }
      citationBlock
          .append("- ")
          .append(StrUtil.blankToDefault(citation.getTitle(), "(无标题)"))
          .append(" | ")
          .append(citation.getUrl().trim())
          .append('\n');
    }
    VisionCompleteResult structured = ai.complete(VisionCompleteRequest.builder()
        .systemPrompt(ResearchAiWebSearchPrompts.STRUCTURE_SYSTEM)
        .prompt("引用 URL 列表：\n"
            + citationBlock
            + "\n联网草稿：\n"
            + StrUtil.sub(StrUtil.nullToEmpty(draftText), 0, 12_000))
        .scene(ModelCallScenes.HOT_NEWS_TOPIC_RESEARCH_AI_WEB_SEARCH_STRUCTURE)
        .webSearchMode(WebSearchMode.OFF)
        .timeoutSeconds(120)
        .build());
    ResearchAiWebSearchItem.Batch batch = LlmJsonSupport.parseObject(
        structured.getText(), ResearchAiWebSearchItem.Batch.class);
    if (batch == null || batch.getItems() == null) {
      return List.of();
    }
    List<ResearchAiWebSearchItem> out = new ArrayList<>();
    for (ResearchAiWebSearchItem item : batch.getItems()) {
      if (item == null || StrUtil.isBlank(item.getUrl())) {
        continue;
      }
      Optional<WebSearchCitationIndex.ResolvedCitation> resolved = index.resolve(item.getUrl());
      if (resolved.isEmpty()) {
        continue;
      }
      WebSearchCitationIndex.ResolvedCitation citation = resolved.get();
      String resolvedTitle = StrUtil.firstNonBlank(item.getTitle(), citation.title(), citation.url());
      out.add(ResearchAiWebSearchItem.builder()
          .title(resolvedTitle)
          .url(citation.url())
          .summary(StrUtil.nullToEmpty(item.getSummary()))
          .build());
      if (out.size() >= HotNewsResearchAgentLimits.MAX_AI_WEB_SEARCH_ITEMS) {
        break;
      }
    }
    return out;
  }

  /**
   * 条目转搜索命中，并剔除已占用 / 屏蔽域 / 营销页。
   *
   * @param items           结构化条目
   * @param occupiedUrlKeys 已占用规范化 URL
   * @param policy          来源策略
   * @return 命中列表
   */
  List<SearchHitView> toHits(
      List<ResearchAiWebSearchItem> items,
      Set<String> occupiedUrlKeys,
      ResearchSourcePolicy policy) {
    if (CollUtil.isEmpty(items)) {
      return List.of();
    }
    Set<String> occupied = occupiedUrlKeys == null ? Set.of() : occupiedUrlKeys;
    Set<String> seen = new LinkedHashSet<>();
    List<SearchHitView> hits = new ArrayList<>();
    for (ResearchAiWebSearchItem item : items) {
      if (item == null || StrUtil.isBlank(item.getUrl())) {
        continue;
      }
      String urlKey = TopicResearchPlanner.normalizeUrl(item.getUrl());
      if (StrUtil.isBlank(urlKey) || occupied.contains(urlKey) || !seen.add(urlKey)) {
        continue;
      }
      if (ResearchSourcePolicyResolver.isBlockedUrl(item.getUrl(), policy)) {
        continue;
      }
      if (MarketingPageFilter.shouldSkip(item.getUrl(), item.getTitle())) {
        continue;
      }
      hits.add(new SearchHitView(
          StrUtil.blankToDefault(item.getTitle(), item.getUrl()),
          item.getUrl(),
          StrUtil.nullToEmpty(item.getSummary())));
      if (hits.size() >= HotNewsResearchAgentLimits.MAX_AI_WEB_SEARCH_ITEMS) {
        break;
      }
    }
    return hits;
  }

  private static String buildUserPrompt(
      String title, String summary, ResearchSourcePolicy policy) {
    String preferred = policy.getPreferredDomains() == null
        ? ""
        : String.join("、", policy.getPreferredDomains());
    String blocked = policy.getBlockedDomains() == null
        ? ""
        : String.join("、", policy.getBlockedDomains());
    String angles = policy.getQueryAngles() == null
        ? ""
        : String.join("、", policy.getQueryAngles());
    return """
        请为下列选题搜索可核验的网页佐证资料（新闻、公告、数据页、分析稿等），给出可打开的原文链接。

        选题标题：%s
        选题摘要：%s
        优选域名参考：%s
        避免域名：%s
        检索角度参考：%s
        """.formatted(
        StrUtil.blankToDefault(title, "(无标题)"),
        StrUtil.blankToDefault(summary, "(无摘要)"),
        StrUtil.blankToDefault(preferred, "(无)"),
        StrUtil.blankToDefault(blocked, "(无)"),
        StrUtil.blankToDefault(angles, "(无)"));
  }
}
