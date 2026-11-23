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
import com.chengwenfang.content.facade.constant.HotNewsTopicFreshness;
import com.chengwenfang.crawler.facade.PublishTimeUtil;
import com.chengwenfang.crawler.facade.enums.ChannelType;
import com.chengwenfang.sourcing.facade.dto.request.HotNewsChannelUpsertRequest;
import com.chengwenfang.sourcing.facade.dto.view.HotNewsChannelView;
import com.chengwenfang.sourcing.facade.enums.SourceMediaType;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AiWebSearchTopicAiResult;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.RadarAiWebSearchBriefResult;
import com.chengwenfang.mode.hotnews.application.websearch.WebSearchCitationIndex;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicOriginKinds;
import com.chengwenfang.prompt.facade.dto.view.PromptPackPromptBindingView;
import com.chengwenfang.prompt.facade.enums.HotNewsRadarCollectNodeType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  /**
   * 联网系统提示（开启时效硬闸）：检索动作优先于输出验收。
   * <p>搜索重点仍由 searchBrief 承载。
   */
  private static final String WEB_SEARCH_SYSTEM = """
      你是热点内容主编。必须使用联网搜索，严格按用户给出的搜索指令寻找真实可写话题。
      检索动作（硬性）：
      1. 每一次联网搜索的 query 必须带上用户提示中的「窗口下限」绝对日期，或写成 after:窗口下限（ISO 日期）；
         禁止只写「近 N 天 / 最新 / 优先 / 尽量」而不带绝对日期。
      2. 冲突顺序：窗内首发日 > 可打开链接 > 主题相关 > 权威；不得为权威长文牺牲时效。
      3. 优先资讯/新闻/一手发布页；禁止用无明确首发日的产品文档、旧 changelog、旧论文凑数。
      4. 必须以用户提示中的服务器「今天」为准，禁止用模型知识年份当作今天。
      5. 若搜不到窗内合格结果：正文明确说明无合格结果，禁止用旧闻凑条数。
      输出验收：只收录原文首发日落在「窗口下限～今天」闭区间内、且可核验可打开的链接；
      每条必须写明原文发布日期（年月日）；不要编造链接或日期；用中文简要说明为何可写。
      """;
  /** 联网系统提示（关闭时效硬闸）：软约束。 */
  private static final String WEB_SEARCH_SYSTEM_SOFT = """
      你是热点内容主编。必须使用联网搜索，严格按用户给出的搜索指令寻找真实可写话题。
      优先权威媒体与一手发布；给出可核验、可打开的网页链接。
      每条话题必须写明原文发布日期（年月日）；不确定日期的不要列入。
      用中文简要说明每条话题为何可写；不要编造无法打开的链接，也不要编造日期。
      """;
  /** 结构化系统提示（开启时效硬闸）。 */
  private static final String STRUCTURE_SYSTEM = """
      你是结构化编辑。根据「联网草稿」与「允许的引用 URL 列表」，抽取可写选题。
      每条 url 必须一字不差出现在引用列表中；禁止发明新链接。
      硬性时效：用户提示中会给出今天与窗口下限；publishedAt 必须落在该闭区间内（优先 ISO）；超窗或无法确认日期禁止输出。
      若草稿与引用中全是超窗或无法确认日期的条目，输出 {"topics":[]}，禁止用旧闻凑数。
      只输出 JSON：{"topics":[{"title":"...","url":"...","summary":"...","whyWrite":"...","publishedAt":"2026-11-20"}]}
      最多 10 条；没有合格条目时输出 {"topics":[]}。
      """;
  /** 结构化系统提示（关闭时效硬闸）。 */
  private static final String STRUCTURE_SYSTEM_SOFT = """
      你是结构化编辑。根据「联网草稿」与「允许的引用 URL 列表」，抽取可写选题。
      每条 url 必须一字不差出现在引用列表中；禁止发明新链接。
      publishedAt 必须是可核验的原文发布/首发日期（优先 ISO 如 2026-11-20）；草稿或引用中无法确认日期的条目不要输出。
      只输出 JSON：{"topics":[{"title":"...","url":"...","summary":"...","whyWrite":"...","publishedAt":"2026-11-20"}]}
      最多 10 条；没有合格条目时输出 {"topics":[]}。
      """;

  /** 多模态门面。 */
  private final AiVisionFacade ai;
  /** 热点门面（占位渠道）。 */
  private final HotNewsFacade hotNews;
  /** 任务门面。 */
  private final JobFacade jobs;
  /** 按量表生成联网搜索指令。 */
  private final RadarAiWebSearchBriefService searchBrief;

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
    reportProgress(jobId, stepKey, 73, "按主题量表生成联网搜索指令…");
    RadarCollectLeaseSupport.renew(lightState);
    try {
      RadarAiWebSearchBriefResult briefResult = searchBrief.generate(session);
      boolean fromAi = isAiSearchBrief(briefResult);
      boolean requireFreshness = session.isRequireTopicFreshness();
      // 绝对日期锚点必须出现在联网 user prompt，避免模型用错「今天」的年份
      String searchPrompt = withAbsoluteFreshnessAnchor(
          resolveSearchPrompt(briefResult, session), requireFreshness);
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_BRIEF_FROM_AI, fromAi);
      if (fromAi) {
        reportProgress(jobId, stepKey, 74, "已按量表生成搜索指令，正在联网搜索…");
      } else {
        reportProgress(jobId, stepKey, 74, "搜索指令回退模板，正在联网搜索…");
      }
      RadarCollectLeaseSupport.renew(lightState);
      VisionCompleteResult webResult = ai.complete(VisionCompleteRequest.builder()
          .systemPrompt(webSearchSystem(requireFreshness))
          .prompt(searchPrompt)
          .scene(ModelCallScenes.RADAR_AI_WEB_SEARCH)
          .webSearchMode(WebSearchMode.ON)
          .timeoutSeconds(180)
          .build());
      RadarCollectLeaseSupport.renew(lightState);
      List<WebSearchCitation> citations =
          webResult.getCitations() == null ? List.of() : webResult.getCitations();
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_CITATION_COUNT, citations.size());
      // 临时排查：输出联网正文与引用数，便于核对「有字无链接」
      log.info(
          "AI 联网搜选题原始返回 themeId={} citationCount={} textChars={} text=\n{}",
          session.getThemeId(),
          citations.size(),
          StrUtil.length(webResult.getText()),
          StrUtil.nullToEmpty(webResult.getText()));
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
      List<AiWebSearchTopicAiResult> topics =
          structureTopics(webResult.getText(), citations, requireFreshness);
      AppendOutcome outcome = appendCandidates(session, topics, citations, channelId);
      String note = "AI 联网搜选题追加 "
          + outcome.added()
          + " 条（引用 "
          + citations.size()
          + (outcome.skippedNoDate() > 0 ? "，跳过无日期 " + outcome.skippedNoDate() : "")
          + (outcome.skippedStale() > 0 ? "，跳过超窗 " + outcome.skippedStale() : "")
          + "）";
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_NOTE, note);
      lightState.put(RadarCollectGraphStateKeys.AI_WEB_SEARCH_ADDED_COUNT, outcome.added());
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
      String draftText, List<WebSearchCitation> citations, boolean requireFreshness) {
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
    String structureUser = "引用 URL 列表：\n"
        + citationBlock
        + "\n联网草稿：\n"
        + StrUtil.sub(StrUtil.nullToEmpty(draftText), 0, 12_000);
    VisionCompleteResult structured = ai.complete(VisionCompleteRequest.builder()
        .systemPrompt(structureSystem(requireFreshness))
        .prompt(withAbsoluteFreshnessAnchor(structureUser, requireFreshness))
        .scene(ModelCallScenes.RADAR_AI_WEB_SEARCH_STRUCTURE)
        .webSearchMode(WebSearchMode.OFF)
        .timeoutSeconds(120)
        .build());
    AiWebSearchTopicAiResult.Batch batch = LlmJsonSupport.parseObject(
        structured.getText(), AiWebSearchTopicAiResult.Batch.class);
    if (batch == null || batch.getTopics() == null) {
      return List.of();
    }
    // citation 闸门：只保留引用集合内的 URL，并回填规范形态
    WebSearchCitationIndex citationIndex = WebSearchCitationIndex.from(citations);
    List<AiWebSearchTopicAiResult> out = new ArrayList<>();
    for (AiWebSearchTopicAiResult topic : batch.getTopics()) {
      if (topic == null || StrUtil.isBlank(topic.getUrl())) {
        continue;
      }
      Optional<WebSearchCitationIndex.ResolvedCitation> resolved = citationIndex.resolve(topic.getUrl());
      if (resolved.isEmpty()) {
        continue;
      }
      WebSearchCitationIndex.ResolvedCitation citation = resolved.get();
      String title = StrUtil.firstNonBlank(topic.getTitle(), citation.title(), citation.url());
      topic.setTitle(title);
      topic.setUrl(citation.url());
      topic.setSummary(StrUtil.nullToEmpty(topic.getSummary()));
      topic.setWhyWrite(StrUtil.nullToEmpty(topic.getWhyWrite()));
      topic.setPublishedAt(StrUtil.trim(StrUtil.nullToEmpty(topic.getPublishedAt())));
      LocalDateTime publishedAt = parsePublishedAt(topic.getPublishedAt());
      // 无日期或不满足时效硬闸的条目不占 MAX_TOPICS 配额
      if (publishedAt == null
          || RadarCollectFreshnessGate.shouldDiscard(publishedAt, requireFreshness)) {
        continue;
      }
      out.add(topic);
      if (out.size() >= MAX_TOPICS) {
        break;
      }
    }
    return out;
  }

  /**
   * 将结构化选题追加为雷达候选；无发布日或时效硬闸超窗则跳过（联网候选不经 enrich）。
   *
   * @param session    会话
   * @param topics     结构化选题
   * @param citations  引用列表
   * @param channelId  占位渠道 ID
   * @return 追加与跳过统计
   */
  AppendOutcome appendCandidates(
      RadarCollectSessionStore.Session session,
      List<AiWebSearchTopicAiResult> topics,
      List<WebSearchCitation> citations,
      String channelId) {
    Set<String> existingUrls = new LinkedHashSet<>();
    for (RadarCollectCandidate existing : session.getCandidates()) {
      if (existing != null && StrUtil.isNotBlank(existing.getUrl())) {
        existingUrls.add(TopicResearchPlanner.normalizeUrl(existing.getUrl()));
      }
    }
    WebSearchCitationIndex citationIndex = WebSearchCitationIndex.from(citations);
    boolean requireFreshness = session.isRequireTopicFreshness();
    int added = 0;
    int skippedNoDate = 0;
    int skippedStale = 0;
    for (AiWebSearchTopicAiResult topic : topics) {
      if (topic == null || StrUtil.isBlank(topic.getUrl())) {
        continue;
      }
      String url = topic.getUrl().trim();
      LocalDateTime publishedAt = parsePublishedAt(topic.getPublishedAt());
      if (publishedAt == null) {
        skippedNoDate++;
        log.info(
            "AI 联网选题跳过无发布日 title={} url={} rawDate={}",
            topic.getTitle(),
            url,
            topic.getPublishedAt());
        continue;
      }
      if (RadarCollectFreshnessGate.shouldDiscard(publishedAt, requireFreshness)) {
        skippedStale++;
        log.info(
            "AI 联网选题跳过超窗 title={} url={} publishedAt={}",
            topic.getTitle(),
            url,
            publishedAt);
        continue;
      }
      // 日期与时效通过后再占去重集
      if (!existingUrls.add(TopicResearchPlanner.normalizeUrl(url))) {
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
          .publishedAt(publishedAt)
          .publishDateSource(ArticlePublishDateSource.AI_WEB_SEARCH.getValue())
          .siteLabel(citationIndex.titleOf(url))
          .mediaType(SourceMediaType.ARTICLE.getValue())
          .originKind(HotNewsTopicOriginKinds.AI_WEB_SEARCH)
          .discarded(false)
          .build());
      added++;
      if (added >= MAX_TOPICS) {
        break;
      }
    }
    return new AppendOutcome(added, skippedNoDate, skippedStale);
  }

  /**
   * 解析联网选题发布日；非法或空白返回 null。
   *
   * @param raw 模型返回的日期串
   * @return 本地时间；失败 null
   */
  static LocalDateTime parsePublishedAt(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    return PublishTimeUtil.parseFlexibleDate(StrUtil.trim(raw));
  }

  /** 联网选题追加结果。 */
  record AppendOutcome(int added, int skippedNoDate, int skippedStale) {}

  /**
   * 按时效开关选择联网系统提示。
   *
   * @param requireFreshness 是否硬闸
   * @return 系统提示
   */
  static String webSearchSystem(boolean requireFreshness) {
    return requireFreshness ? WEB_SEARCH_SYSTEM : WEB_SEARCH_SYSTEM_SOFT;
  }

  /**
   * 按时效开关选择结构化系统提示。
   *
   * @param requireFreshness 是否硬闸
   * @return 系统提示
   */
  static String structureSystem(boolean requireFreshness) {
    return requireFreshness ? STRUCTURE_SYSTEM : STRUCTURE_SYSTEM_SOFT;
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

  /**
   * 是否采用 AI 生成的搜索指令。
   *
   * @param brief 生成结果
   * @return 可用则为 true
   */
  static boolean isAiSearchBrief(RadarAiWebSearchBriefResult brief) {
    return brief != null && brief.isFromAi() && StrUtil.isNotBlank(brief.getSearchBrief());
  }

  /**
   * 选择联网 user prompt：优先 AI searchBrief，否则硬编码回退。
   *
   * @param brief   AI 结果；可空
   * @param session 会话
   * @return 搜索提示正文
   */
  static String resolveSearchPrompt(
      RadarAiWebSearchBriefResult brief, RadarCollectSessionStore.Session session) {
    if (isAiSearchBrief(brief)) {
      return brief.getSearchBrief();
    }
    return buildUserPrompt(session);
  }

  static String buildUserPrompt(RadarCollectSessionStore.Session session) {
    ThemeTitleRelevance.Profile profile = session.getThemeRelevanceProfile();
    String themeName = profile == null ? "" : StrUtil.nullToEmpty(profile.themeName());
    String intent = StrUtil.nullToEmpty(session.getCoreIntentPrompt());
    String queries = profile == null || profile.searchQueries() == null
        ? ""
        : String.join("、", profile.searchQueries());
    String mustWatch = profile == null || profile.mustWatch() == null
        ? ""
        : String.join("、", profile.mustWatch());
    boolean requireFreshness = session.isRequireTopicFreshness();
    if (requireFreshness) {
      return """
          请按下列绝对日期窗检索与主题相关的资讯选题，给出可打开的原文链接。
          %s
          检索要求：先列出 3～6 条可直接执行的短 query，每条必须含窗口下限绝对日期或 after:窗口下限；
          冲突顺序：窗内首发日 > 可打开链接 > 主题相关 > 权威；
          搜不到窗内结果则说明无合格结果，禁止用旧闻凑数。
          每条候选必须带可核验的原文发布日期；不确定日期的不要列入。

          主题名称：%s
          搜索词参考：%s
          必看关注：%s

          主要意图：
          %s
          """.formatted(
          HotNewsTopicFreshness.absoluteWindowInstruction(),
          StrUtil.blankToDefault(themeName, "(未命名主题)"),
          StrUtil.blankToDefault(queries, "(无)"),
          StrUtil.blankToDefault(mustWatch, "(无)"),
          StrUtil.blankToDefault(intent, "围绕主题找窗内可写热点。"));
    }
    return """
        请在网上搜索与下列主题相关的高热度资讯话题，挑选适合展开写作的选题，并给出可打开的原文链接。
        每条必须带可核验的原文发布日期；不确定日期的不要列入。
        回溯约 %d 小时内优先。

        主题名称：%s
        搜索词参考：%s
        必看关注：%s

        主要意图：
        %s
        """.formatted(
        Math.max(1, session.getHours()),
        StrUtil.blankToDefault(themeName, "(未命名主题)"),
        StrUtil.blankToDefault(queries, "(无)"),
        StrUtil.blankToDefault(mustWatch, "(无)"),
        StrUtil.blankToDefault(intent, "围绕主题找近期可写热点。"));
  }

  /**
   * 在提示前附加服务器绝对日期窗（开启时效时）。
   *
   * @param prompt             原提示
   * @param requireFreshness   是否开启时效
   * @return 附加后的提示
   */
  static String withAbsoluteFreshnessAnchor(String prompt, boolean requireFreshness) {
    if (!requireFreshness) {
      return StrUtil.nullToEmpty(prompt);
    }
    return HotNewsTopicFreshness.absoluteWindowInstruction()
        + "\n\n"
        + StrUtil.nullToEmpty(prompt);
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
