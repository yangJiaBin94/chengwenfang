package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.ai.facade.dto.view.WebSearchCitation;
import com.chengwenfang.ai.facade.enums.WebSearchMode;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelUpsertRequest;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelView;
import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AiWebSearchTopicAiResult;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicOriginKinds;
import com.chengwenfang.prompt.facade.dto.view.PromptPackPromptBindingView;
import com.chengwenfang.prompt.facade.enums.HotNewsRadarCollectNodeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 具备 WebSearch 能力时：按主题意图联网自搜，citations 定 URL，二次结构化后追加候选；否则空跑。
 * <p>插在素材筛选之后、落库之前，故自搜候选不经 enrich。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiWebSearchTopicsRadarNodeHandler implements RadarCollectNodeHandler {
  /** 单次最多追加选题数。 */
  static final int MAX_TOPICS = 10;
  /** 联网步骤系统提示。 */
  private static final String WEB_SEARCH_SYSTEM = """
      你是热点内容主编。必须使用联网搜索，根据用户给出的主题意图，寻找近期热度较高、适合展开写成文章的真实话题。
      优先权威媒体与一手发布；给出可核验的网页链接。
      用中文简要说明每条话题为何可写；不要编造无法打开的链接。
      """;
  /** 二次结构化系统提示。 */
  private static final String STRUCTURE_SYSTEM = """
      你是结构化编辑。根据「联网草稿」与「允许的引用 URL 列表」，抽取可写选题。
      每条 url 必须一字不差出现在引用列表中；禁止发明新链接。
      只输出 JSON：{"topics":[{"title":"...","url":"...","summary":"...","whyWrite":"..."}]}
      最多 10 条；没有合格条目时输出 {"topics":[]}。
      """;

  /** 多模态门面。 */
  private final AiVisionFacade ai;
  /** 热点门面（占位渠道）。 */
  private final HotNewsFacade hotNews;
  /** 任务门面。 */
  private final JobFacade jobs;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.AI_WEB_SEARCH_TOPICS;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<PromptPackPromptBindingView> promptPackBindings,
      String nodeKey,
      String nodeName) {
    String jobId = StrUtil.blankToDefault(
        stringVal(lightState.get(RadarCollectGraphStateKeys.JOB_ID)),
        session.getFetchRunId());
    String stepKey = HotNewsRadarCollectNodeType.AI_WEB_SEARCH_TOPICS.getValue();
    if (!ai.supportsWebSearch()) {
      String note = "跳过 AI 联网搜选题：当前多模态配置不具备 WebSearch 能力";
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_NOTE, note);
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_ADDED_COUNT, 0);
      reportProgress(jobId, stepKey, 76, note);
      log.info(note);
      return;
    }
    reportProgress(jobId, stepKey, 74, "AI 正在联网搜索可写选题…");
    RadarCollectLeaseSupport.renew(lightState);
    try {
      VisionCompleteResult webResult = ai.complete(VisionCompleteRequest.builder()
          .systemPrompt(WEB_SEARCH_SYSTEM)
          .prompt(buildUserPrompt(session))
          .scene(ModelCallScenes.RADAR_AI_WEB_SEARCH)
          .webSearchMode(WebSearchMode.ON)
          .timeoutSeconds(180)
          .build());
      RadarCollectLeaseSupport.renew(lightState);
      List<WebSearchCitation> citations =
          webResult.getCitations() == null ? List.of() : webResult.getCitations();
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_CITATION_COUNT, citations.size());
      if (citations.isEmpty()) {
        String note = "AI 联网搜选题未返回引用链接，跳过追加";
        lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_NOTE, note);
        lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_ADDED_COUNT, 0);
        reportProgress(jobId, stepKey, 77, note);
        log.warn(note);
        return;
      }
      reportProgress(jobId, stepKey, 75, "整理联网结果为选题…");
      String channelId = ensureAiWebSearchChannelId(session.getThemeId());
      if (StrUtil.isBlank(channelId)) {
        String note = "AI 联网搜选题无法准备占位渠道，跳过追加";
        lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_NOTE, note);
        lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_ADDED_COUNT, 0);
        reportProgress(jobId, stepKey, 77, note);
        log.warn(note);
        return;
      }
      List<AiWebSearchTopicAiResult> topics = structureTopics(webResult.getText(), citations);
      int added = appendCandidates(session, topics, citations, channelId);
      String note = "AI 联网搜选题追加 " + added + " 条（引用 " + citations.size() + "）";
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_NOTE, note);
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_ADDED_COUNT, added);
      reportProgress(jobId, stepKey, 77, note);
      log.info("{} themeId={}", note, session.getThemeId());
    } catch (Exception exception) {
      String note = "AI 联网搜选题失败，已跳过：" + StrUtil.blankToDefault(exception.getMessage(), "unknown");
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_NOTE, note);
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_ADDED_COUNT, 0);
      reportProgress(jobId, stepKey, 77, note);
      log.warn("雷达 AI 联网搜选题失败 themeId={} err={}", session.getThemeId(), exception.getMessage());
    }
  }

  private List<AiWebSearchTopicAiResult> structureTopics(
      String draftText, List<WebSearchCitation> citations) {
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
        .systemPrompt(STRUCTURE_SYSTEM)
        .prompt("引用 URL 列表：\n"
            + citationBlock
            + "\n联网草稿：\n"
            + StrUtil.sub(StrUtil.nullToEmpty(draftText), 0, 12_000))
        .scene(ModelCallScenes.RADAR_AI_WEB_SEARCH_STRUCTURE)
        .webSearchMode(WebSearchMode.OFF)
        .timeoutSeconds(120)
        .build());
    AiWebSearchTopicAiResult.Batch batch = LlmJsonSupport.parseObject(
        structured.getText(), AiWebSearchTopicAiResult.Batch.class);
    if (batch == null || batch.getTopics() == null) {
      return List.of();
    }
    // 归一化 URL → 引用中的规范 URL（保留原始大小写/斜杠形态供落库）
    Map<String, String> canonicalByNormalized = new LinkedHashMap<>();
    Map<String, String> titleByNormalized = new LinkedHashMap<>();
    for (WebSearchCitation citation : citations) {
      if (citation == null || StrUtil.isBlank(citation.getUrl())) {
        continue;
      }
      String url = citation.getUrl().trim();
      String key = normalizeUrl(url);
      canonicalByNormalized.putIfAbsent(key, url);
      titleByNormalized.putIfAbsent(key, StrUtil.nullToEmpty(citation.getTitle()));
    }
    List<AiWebSearchTopicAiResult> out = new ArrayList<>();
    for (AiWebSearchTopicAiResult topic : batch.getTopics()) {
      if (topic == null || StrUtil.isBlank(topic.getUrl())) {
        continue;
      }
      String key = normalizeUrl(topic.getUrl());
      String canonical = canonicalByNormalized.get(key);
      if (StrUtil.isBlank(canonical)) {
        continue;
      }
      String title = StrUtil.firstNonBlank(
          topic.getTitle(),
          titleByNormalized.get(key),
          canonical);
      topic.setTitle(title);
      topic.setUrl(canonical);
      topic.setSummary(StrUtil.nullToEmpty(topic.getSummary()));
      topic.setWhyWrite(StrUtil.nullToEmpty(topic.getWhyWrite()));
      out.add(topic);
      if (out.size() >= MAX_TOPICS) {
        break;
      }
    }
    return out;
  }

  private int appendCandidates(
      RadarCollectSessionStore.Session session,
      List<AiWebSearchTopicAiResult> topics,
      List<WebSearchCitation> citations,
      String channelId) {
    Set<String> existingUrls = new LinkedHashSet<>();
    for (RadarCollectCandidate existing : session.getCandidates()) {
      if (existing != null && StrUtil.isNotBlank(existing.getUrl())) {
        existingUrls.add(normalizeUrl(existing.getUrl()));
      }
    }
    int added = 0;
    for (AiWebSearchTopicAiResult topic : topics) {
      if (topic == null || StrUtil.isBlank(topic.getUrl())) {
        continue;
      }
      String url = topic.getUrl().trim();
      if (!existingUrls.add(normalizeUrl(url))) {
        continue;
      }
      String brief = StrUtil.firstNonBlank(topic.getSummary(), topic.getWhyWrite(), topic.getTitle());
      session.getCandidates().add(RadarCollectCandidate.builder()
          .tempId(IdsUtil.newId())
          .themeId(session.getThemeId())
          .channelId(channelId)
          .channelName(HotNewsTopicOriginKinds.AI_WEB_SEARCH_LABEL)
          .channelType(ChannelType.WEB.getValue())
          .url(url)
          .title(topic.getTitle())
          .fetchSummary(brief)
          .factBrief(brief)
          .rawText(brief)
          .siteLabel(siteLabelOf(url, citations))
          .mediaType(SourceMediaType.ARTICLE.getValue())
          .originKind(HotNewsTopicOriginKinds.AI_WEB_SEARCH)
          .discarded(false)
          .build());
      added++;
      if (added >= MAX_TOPICS) {
        break;
      }
    }
    return added;
  }

  /**
   * 确保主题下存在 disabled 的 AI 联网占位渠道，满足 source_items.channel_id FK。
   *
   * @param themeId 主题 ID
   * @return 渠道 ID；失败为空串
   */
  private String ensureAiWebSearchChannelId(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return "";
    }
    String marker = HotNewsTopicOriginKinds.AI_WEB_SEARCH_CHANNEL_BASE_URL;
    for (HotNewsChannelView channel : hotNews.listChannels(themeId)) {
      if (channel == null || StrUtil.isBlank(channel.getId())) {
        continue;
      }
      if (marker.equals(StrUtil.trim(channel.getBaseUrl()))
          || HotNewsTopicOriginKinds.AI_WEB_SEARCH_LABEL.equals(StrUtil.trim(channel.getName()))) {
        return channel.getId();
      }
    }
    HotNewsChannelView created = hotNews.upsertChannel(HotNewsChannelUpsertRequest.builder()
        .themeId(themeId)
        .name(HotNewsTopicOriginKinds.AI_WEB_SEARCH_LABEL)
        .type(ChannelType.WEB.getValue())
        .baseUrl(marker)
        .enabled(false)
        .build());
    return created == null ? "" : StrUtil.nullToEmpty(created.getId());
  }

  private static String buildUserPrompt(RadarCollectSessionStore.Session session) {
    ThemeTitleRelevance.Profile profile = session.getThemeRelevanceProfile();
    String themeName = profile == null ? "" : StrUtil.nullToEmpty(profile.themeName());
    String intent = StrUtil.nullToEmpty(session.getCoreIntentPrompt());
    String queries = profile == null || profile.searchQueries() == null
        ? ""
        : String.join("、", profile.searchQueries());
    String mustWatch = profile == null || profile.mustWatch() == null
        ? ""
        : String.join("、", profile.mustWatch());
    return """
        请在网上搜索与下列主题相关的最新高热度资讯话题，挑选适合展开写作的选题，并给出可打开的原文链接。

        主题名称：%s
        搜索词参考：%s
        必看关注：%s
        回溯约 %d 小时内优先。

        主要意图：
        %s
        """.formatted(
        StrUtil.blankToDefault(themeName, "(未命名主题)"),
        StrUtil.blankToDefault(queries, "(无)"),
        StrUtil.blankToDefault(mustWatch, "(无)"),
        Math.max(1, session.getHours()),
        StrUtil.blankToDefault(intent, "围绕主题找近期可写热点。"));
  }

  private static String siteLabelOf(String url, List<WebSearchCitation> citations) {
    String key = normalizeUrl(url);
    for (WebSearchCitation citation : citations) {
      if (citation == null || StrUtil.isBlank(citation.getUrl())) {
        continue;
      }
      if (key.equals(normalizeUrl(citation.getUrl())) && StrUtil.isNotBlank(citation.getTitle())) {
        return citation.getTitle();
      }
    }
    return "";
  }

  private static String normalizeUrl(String url) {
    String normalized = StrUtil.trim(url).toLowerCase(Locale.ROOT);
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private void reportProgress(String jobId, String stepKey, int progress, String message) {
    if (StrUtil.isBlank(jobId)) {
      return;
    }
    try {
      jobs.updateProgress(jobId, stepKey, Math.min(95, Math.max(1, progress)), message);
    } catch (Exception ignored) {
      // 进度失败不阻断
    }
  }

  private static String stringVal(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
