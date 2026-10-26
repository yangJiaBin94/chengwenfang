package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AiThemeSeedAiResult;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AiThemeSeedBatchAiResult;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.ArticlePublishDateAiResult;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.MarketingFilterAiResult;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.ThemeRelevanceBatchAiResult;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.crawler.facade.PublishDateSnippetSupport;
import com.chengwenfang.crawler.facade.PublishTimeUtil;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.constant.HotNewsRadarCollectSkillNames;
import com.chengwenfang.skill.facade.constant.HotNewsRadarCollectWorkflowKeys;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 雷达采集 AI：按 Skill 名取提示词并调用文本模型。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RadarCollectAiSupport {
  /** 用户提示截断上限（正文类）。 */
  private static final int BODY_PROMPT_MAX_CHARS = 12_000;
  /** 用户提示截断上限（营销摘录）。 */
  private static final int MARKETING_SNIPPET_MAX_CHARS = 6_000;
  /** 主题相关性一次送审的标题条数。 */
  private static final int THEME_BATCH_SIZE = 40;
  /** 词典未命中时的系统提示：只列明显偏题，拿不准的留下。 */
  private static final String THEME_BATCH_SYSTEM = """
      你是热点素材编辑。根据主题信号判断标题是否明显偏题。
      新模型名、产品名、开源发布、行业日报，只要属于该主题领域，都算相关。
      只把明显不属于该主题的条目 id 列出来。拿不准的不要列。
      只输出 JSON：{"offTopicIds":["id"]}。没有偏题时输出 {"offTopicIds":[]}。
      """;

  /** Skill 门面。 */
  private final SkillFacade skills;
  /** 文本 AI。 */
  private final AiTextFacade ai;

  /**
   * 生成文章事实速览。
   *
   * @param skillBindings 节点绑定
   * @param title         标题
   * @param body          正文
   * @return 速览
   */
  public String articleFactBrief(
      List<SkillPromptBindingView> skillBindings, String title, String body) {
    String system = resolveSystemPrompt(
        skillBindings, HotNewsRadarCollectSkillNames.ARTICLE_FACT_BRIEF);
    return complete(
        system,
        "标题：" + StrUtil.nullToEmpty(title) + "\n\n正文：\n"
            + StrUtil.sub(StrUtil.nullToEmpty(body), 0, BODY_PROMPT_MAX_CHARS),
        ModelCallScenes.RADAR_ARTICLE_FACT_BRIEF);
  }

  /**
   * 从日期输入文本（或正则片段）抽取发布日；抽不到返回 null。
   *
   * @param skillBindings 节点绑定
   * @param title         标题
   * @param dateInputText 日期输入全文
   * @return 发布时间；无法判断为 null
   */
  public LocalDateTime extractPublishDate(
      List<SkillPromptBindingView> skillBindings, String title, String dateInputText) {
    if (StrUtil.isBlank(dateInputText)) {
      return null;
    }
    String system = resolveSystemPrompt(
        skillBindings, HotNewsRadarCollectSkillNames.ARTICLE_PUBLISH_DATE);
    // 正则候选±100 字（或无命中时的受控全文）交给模型判定发布日
    String context = PublishDateSnippetSupport.buildAiContext(dateInputText);
    if (StrUtil.isBlank(context)) {
      return null;
    }
    String text = complete(
        system,
        "标题：" + StrUtil.nullToEmpty(title) + "\n\n文本：\n" + context,
        ModelCallScenes.RADAR_ARTICLE_PUBLISH_DATE);
    ArticlePublishDateAiResult result = LlmJsonSupport.parseObject(
        text, ArticlePublishDateAiResult.class);
    if (result == null || StrUtil.isBlank(result.getPublishedAt())) {
      return null;
    }
    String raw = result.getPublishedAt().trim();
    if ("null".equalsIgnoreCase(raw)) {
      return null;
    }
    return PublishTimeUtil.parseFlexibleDate(raw);
  }

  /**
   * 生成视频事实速览。
   *
   * @param skillBindings 节点绑定
   * @param title         标题
   * @param transcript    字幕/口播
   * @return 速览
   */
  public String videoFactBrief(
      List<SkillPromptBindingView> skillBindings, String title, String transcript) {
    String system = resolveSystemPrompt(
        skillBindings, HotNewsRadarCollectSkillNames.VIDEO_FACT_BRIEF);
    return complete(
        system,
        "标题：" + StrUtil.nullToEmpty(title) + "\n\n口播/字幕：\n"
            + StrUtil.sub(StrUtil.nullToEmpty(transcript), 0, BODY_PROMPT_MAX_CHARS),
        ModelCallScenes.RADAR_VIDEO_FACT_BRIEF);
  }

  /**
   * 判定是否营销。
   *
   * @param skillBindings 节点绑定
   * @param title         标题
   * @param factBrief     事实速览
   * @param rawSnippet    原文片段
   * @return true 表示营销应弃用
   */
  public boolean isMarketing(
      List<SkillPromptBindingView> skillBindings,
      String title,
      String factBrief,
      String rawSnippet) {
    String system = resolveSystemPrompt(
        skillBindings, HotNewsRadarCollectSkillNames.MARKETING_FILTER);
    String text = complete(
        system,
        "标题：" + StrUtil.nullToEmpty(title)
            + "\n事实速览：" + StrUtil.nullToEmpty(factBrief)
            + "\n原文摘录：\n"
            + StrUtil.sub(StrUtil.nullToEmpty(rawSnippet), 0, MARKETING_SNIPPET_MAX_CHARS),
        ModelCallScenes.RADAR_CANDIDATE_FILTER_MARKETING);
    MarketingFilterAiResult result = LlmJsonSupport.parseObject(text, MarketingFilterAiResult.class);
    return result != null && Boolean.TRUE.equals(result.getMarketing());
  }

  /**
   * 整包判断词典未命中的标题里哪些明显偏题。调用失败或解析失败时返回空集，调用方应留下这些标题。
   *
   * @param profile    主题信号
   * @param candidates 待判定候选
   * @return 明确偏题的临时 ID
   */
  public Set<String> findOffTopicIds(
      ThemeTitleRelevance.Profile profile, List<RadarCollectCandidate> candidates) {
    if (profile == null || candidates == null || candidates.isEmpty()) {
      return Set.of();
    }
    Set<String> offTopic = new LinkedHashSet<>();
    for (int offset = 0; offset < candidates.size(); offset += THEME_BATCH_SIZE) {
      int end = Math.min(candidates.size(), offset + THEME_BATCH_SIZE);
      offTopic.addAll(findOffTopicChunk(profile, candidates.subList(offset, end)));
    }
    return offTopic;
  }

  /**
   * 从模型原文取出偏题 ID；无效 JSON 视为没有偏题。
   *
   * @param text 模型原文
   * @return ID 列表；解析失败为空列表
   */
  public static List<String> parseOffTopicIds(String text) {
    ThemeRelevanceBatchAiResult result = LlmJsonSupport.parseObject(
        text, ThemeRelevanceBatchAiResult.class);
    if (result == null || result.getOffTopicIds() == null) {
      return List.of();
    }
    return result.getOffTopicIds();
  }

  /**
   * 问一轮模型，只收回本批里明确偏题的 ID。
   *
   * @param profile 主题信号
   * @param chunk   本批候选
   * @return 偏题 ID；失败为空集
   */
  private Set<String> findOffTopicChunk(
      ThemeTitleRelevance.Profile profile, List<RadarCollectCandidate> chunk) {
    StringBuilder user = new StringBuilder();
    user.append("主题：").append(StrUtil.nullToEmpty(profile.themeName())).append('\n');
    user.append("搜索词：").append(joinSignals(profile.searchQueries())).append('\n');
    user.append("关注点：").append(joinSignals(profile.mustWatch())).append("\n\n");
    user.append("待判定标题（id|标题）：\n");
    Set<String> knownIds = new LinkedHashSet<>();
    for (RadarCollectCandidate candidate : chunk) {
      if (candidate == null || StrUtil.isBlank(candidate.getTempId())) {
        continue;
      }
      knownIds.add(candidate.getTempId());
      user.append(candidate.getTempId())
          .append('|')
          .append(StrUtil.nullToEmpty(candidate.getTitle()))
          .append('\n');
    }
    if (knownIds.isEmpty()) {
      return Set.of();
    }
    String text = complete(THEME_BATCH_SYSTEM, user.toString(), ModelCallScenes.RADAR_CANDIDATE_FILTER_THEME);
    Set<String> offTopic = new LinkedHashSet<>();
    for (String id : parseOffTopicIds(text)) {
      if (knownIds.contains(StrUtil.trim(id))) {
        offTopic.add(StrUtil.trim(id));
      }
    }
    return offTopic;
  }

  /**
   * 把主题信号拼成一行。
   *
   * @param values 搜索词或关注点
   * @return 展示文案
   */
  private static String joinSignals(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "无";
    }
    return String.join("、", values);
  }

  /**
   * 按主题问卷生成一条 AI 主题种子；失败返回 null。
   *
   * @param skillBindings 节点绑定
   * @param userPrompt    用户提示
   * @return 单条结果；无效为 null
   */
  public AiThemeSeedAiResult generateAiThemeSeed(
      List<SkillPromptBindingView> skillBindings, String userPrompt) {
    List<AiThemeSeedAiResult> items = generateAiThemeSeeds(skillBindings, userPrompt, 1);
    return items.isEmpty() ? null : items.get(0);
  }

  /**
   * 按主题问卷一次生成多条 AI 主题种子；过滤空标题/速览并截断到 count。
   *
   * @param skillBindings 节点绑定
   * @param userPrompt    已拼装的主题问卷用户提示（须含条数 N）
   * @param count         期望条数
   * @return 有效条目；失败为空列表
   */
  public List<AiThemeSeedAiResult> generateAiThemeSeeds(
      List<SkillPromptBindingView> skillBindings, String userPrompt, int count) {
    if (StrUtil.isBlank(userPrompt) || count <= 0) {
      return List.of();
    }
    String system = resolveSystemPrompt(
        skillBindings, HotNewsRadarCollectSkillNames.AI_THEME_SEED);
    String text = complete(system, userPrompt, ModelCallScenes.RADAR_AI_THEME_SEED);
    List<AiThemeSeedAiResult> parsed = parseAiThemeSeedItems(text);
    // 同批响应内标题自检去重，避免一次返回里撞题
    return AiThemeSeedDedupeSupport.takeUnique(parsed, Set.of(), count);
  }

  /**
   * 解析批量或单条 AI 主题种子 JSON（供单测与批量生成复用）。
   *
   * @param text 模型原文
   * @return 条目列表（可能为空）
   */
  public static List<AiThemeSeedAiResult> parseAiThemeSeedItems(String text) {
    AiThemeSeedBatchAiResult batch = LlmJsonSupport.parseObject(text, AiThemeSeedBatchAiResult.class);
    if (batch != null && batch.getItems() != null && !batch.getItems().isEmpty()) {
      return new ArrayList<>(batch.getItems());
    }
    AiThemeSeedAiResult single = LlmJsonSupport.parseObject(text, AiThemeSeedAiResult.class);
    if (single == null) {
      return List.of();
    }
    return List.of(single);
  }

  /**
   * 合并簇摘要。
   *
   * @param skillBindings 节点绑定
   * @param clusterTitle  簇标题
   * @param memberBriefs  成员速览
   * @return 簇摘要
   */
  public String mergeClusterSummary(
      List<SkillPromptBindingView> skillBindings,
      String clusterTitle,
      List<String> memberBriefs) {
    String system = resolveSystemPrompt(
        skillBindings, HotNewsRadarCollectSkillNames.CLUSTER_SUMMARY_MERGE);
    StringBuilder body = new StringBuilder();
    body.append("选题标题：").append(StrUtil.nullToEmpty(clusterTitle)).append("\n\n成员速览：\n");
    int index = 1;
    for (String brief : memberBriefs) {
      if (StrUtil.isBlank(brief)) {
        continue;
      }
      body.append(index++).append(". ").append(brief.trim()).append('\n');
    }
    return complete(system, body.toString(), ModelCallScenes.RADAR_CLUSTER_SUMMARY_MERGE);
  }

  /**
   * 解析节点绑定或库内 Skill 的系统提示词。
   *
   * @param bindings  节点绑定
   * @param skillName Skill 名
   * @return 系统提示；无则返回通用兜底文案
   */
  private String resolveSystemPrompt(List<SkillPromptBindingView> bindings, String skillName) {
    SkillView matched = findBoundSkill(bindings, skillName);
    if (matched != null && StrUtil.isNotBlank(matched.getSystemInstructions())) {
      return matched.getSystemInstructions();
    }
    // 回退：按 scene + 名从库中取
    for (SkillView skill : skills.listSkills()) {
      if (skill == null) {
        continue;
      }
      if (skillName.equals(skill.getName())
          && HotNewsRadarCollectWorkflowKeys.SCENE.equals(StrUtil.nullToEmpty(skill.getScene()))
          && StrUtil.isNotBlank(skill.getSystemInstructions())) {
        return skill.getSystemInstructions();
      }
    }
    return "请按编辑要求输出简洁中文结果。";
  }

  /**
   * 在节点绑定中查找指定名称的 Skill。
   *
   * @param bindings  绑定列表
   * @param skillName Skill 名
   * @return 命中视图；未找到为 null
   */
  private SkillView findBoundSkill(List<SkillPromptBindingView> bindings, String skillName) {
    if (bindings == null || bindings.isEmpty()) {
      return null;
    }
    List<SkillView> all = skills.listSkills();
    for (SkillPromptBindingView binding : bindings) {
      if (binding == null || Boolean.FALSE.equals(binding.getEnabled())
          || StrUtil.isBlank(binding.getSkillId())) {
        continue;
      }
      for (SkillView skill : all) {
        if (skill != null
            && binding.getSkillId().equals(skill.getId())
            && skillName.equals(skill.getName())) {
          return skill;
        }
      }
    }
    return null;
  }

  /**
   * 调用文本模型；失败返回空串。
   *
   * @param systemPrompt 系统提示
   * @param userPrompt   用户提示
   * @param scene        model_calls.scene 审计键
   * @return 模型正文
   */
  private String complete(String systemPrompt, String userPrompt, String scene) {
    try {
      var result = ai.completeText(TextCompleteRequest.builder()
          .scene(scene)
          .systemPrompt(systemPrompt)
          .prompt(userPrompt)
          .timeoutSeconds(120)
          .build());
      return result == null ? "" : StrUtil.trim(StrUtil.nullToEmpty(result.getText()));
    } catch (Exception exception) {
      log.warn("雷达采集 AI 调用失败 scene={} err={}", scene, exception.getMessage());
      return "";
    }
  }

  /**
   * 组合全部绑定 Skill 正文长度，供 load_skills 校验。
   *
   * @param bindings 绑定
   * @return 组合提示词
   */
  public String composeAll(List<SkillPromptBindingView> bindings) {
    return skills.composePromptFromBindings(bindings == null ? List.of() : new ArrayList<>(bindings));
  }
}
