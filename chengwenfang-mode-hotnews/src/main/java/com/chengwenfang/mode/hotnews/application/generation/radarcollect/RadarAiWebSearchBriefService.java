package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.ai.facade.enums.WebSearchMode;
import com.chengwenfang.common.LlmJsonSupport;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.content.facade.ScoringRubricSupport;
import com.chengwenfang.content.facade.constant.HotNewsTopicFreshness;
import com.chengwenfang.content.facade.dto.view.ScoringRubricDimensionView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.RadarAiWebSearchBriefResult;
import com.chengwenfang.mode.hotnews.application.hotnews.TopicAiWritingScoreService;
import com.chengwenfang.mode.hotnews.application.prompt.RadarAiWebSearchBriefPrompts;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 按主题意图与评分量表生成雷达 AI 联网搜索指令；失败返回 null 由调用方回退。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RadarAiWebSearchBriefService {

  /** searchBrief 最短可用长度。 */
  private static final int MIN_BRIEF_LENGTH = 40;
  /** searchBrief 最长保留长度。 */
  private static final int MAX_BRIEF_LENGTH = 4_000;

  /** 多模态补全。 */
  private final AiVisionFacade ai;
  /** 主题量表读取。 */
  private final TopicAiWritingScoreService writingScore;

  /**
   * 为当前雷达会话生成联网搜索指令。
   *
   * @param session 雷达会话
   * @return 成功时 fromAi=true；失败 null
   */
  public RadarAiWebSearchBriefResult generate(RadarCollectSessionStore.Session session) {
    if (session == null || StrUtil.isBlank(session.getThemeId())) {
      return null;
    }
    try {
      ScoringRubricView rubric = writingScore.loadRubric(session.getThemeId());
      String userPrompt = buildUserFacts(session, rubric);
      VisionCompleteResult result = ai.complete(VisionCompleteRequest.builder()
          .systemPrompt(RadarAiWebSearchBriefPrompts.SYSTEM_PROMPT)
          .prompt(userPrompt)
          .scene(ModelCallScenes.RADAR_AI_WEB_SEARCH_BRIEF)
          .webSearchMode(WebSearchMode.OFF)
          .timeoutSeconds(90)
          .build());
      return parse(result == null ? null : result.getText());
    } catch (Exception exception) {
      log.warn(
          "雷达联网搜索指令生成失败 themeId={} err={}",
          session.getThemeId(),
          exception.getMessage());
      return null;
    }
  }

  /**
   * 解析模型输出为搜索指令结果。
   *
   * @param text 模型原文
   * @return 合法结果；否则 null
   */
  static RadarAiWebSearchBriefResult parse(String text) {
    JSONObject json = LlmJsonSupport.parseJsonObject(text);
    if (json == null) {
      return null;
    }
    String brief = StrUtil.trim(StrUtil.nullToEmpty(json.getStr("searchBrief")));
    if (StrUtil.isBlank(brief) || brief.length() < MIN_BRIEF_LENGTH) {
      return null;
    }
    if (brief.length() > MAX_BRIEF_LENGTH) {
      brief = brief.substring(0, MAX_BRIEF_LENGTH);
    }
    List<String> focus = new ArrayList<>();
    JSONArray dims = json.getJSONArray("focusDimensions");
    if (dims != null) {
      for (Object item : dims) {
        String label = StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(item)));
        if (StrUtil.isBlank(label) || focus.contains(label)) {
          continue;
        }
        focus.add(label);
        if (focus.size() >= 4) {
          break;
        }
      }
    }
    return RadarAiWebSearchBriefResult.builder()
        .searchBrief(brief)
        .focusDimensions(focus)
        .fromAi(true)
        .build();
  }

  /**
   * 组装给元模型的事实材料（不做权重话术拼接）。
   *
   * @param session 会话
   * @param rubric  量表；可空
   * @return user prompt
   */
  static String buildUserFacts(RadarCollectSessionStore.Session session, ScoringRubricView rubric) {
    ThemeTitleRelevance.Profile profile = session.getThemeRelevanceProfile();
    String themeName = profile == null ? "" : StrUtil.nullToEmpty(profile.themeName());
    String queries = profile == null || profile.searchQueries() == null
        ? ""
        : String.join("、", profile.searchQueries());
    String mustWatch = profile == null || profile.mustWatch() == null
        ? ""
        : String.join("、", profile.mustWatch());
    String intent = StrUtil.nullToEmpty(session.getCoreIntentPrompt());
    int hours = Math.max(1, session.getHours());
    boolean requireFreshness = session.isRequireTopicFreshness();
    StringBuilder dims = new StringBuilder();
    if (rubric != null
        && ScoringRubricSupport.isValid(rubric)
        && CollUtil.isNotEmpty(rubric.getDimensions())) {
      for (ScoringRubricDimensionView dim : rubric.getDimensions()) {
        if (dim == null) {
          continue;
        }
        dims.append("- key=")
            .append(StrUtil.nullToEmpty(dim.getKey()))
            .append(" label=")
            .append(StrUtil.nullToEmpty(dim.getLabel()))
            .append(" weight=")
            .append(dim.getWeight() == null ? 0 : dim.getWeight())
            .append("% description=")
            .append(StrUtil.nullToEmpty(dim.getDescription()))
            .append('\n');
      }
      if (StrUtil.isNotBlank(rubric.getScoreGuidance())) {
        dims.append("量表指引：").append(rubric.getScoreGuidance().trim()).append('\n');
      }
    } else {
      dims.append("(无合法评分量表，请按主题意图与回溯窗口自行组织搜索重点)\n");
    }
    String absoluteWindow = requireFreshness
        ? HotNewsTopicFreshness.absoluteWindowInstruction()
        : "(时效硬开关关闭，可不写绝对日期窗)";
    String hoursLine = requireFreshness
        ? "回溯小时数：%d（仅作参考，以绝对日期窗为准）".formatted(hours)
        : "回溯小时数：%d".formatted(hours);
    return """
        请根据下列事实材料，生成联网搜索指令 JSON。

        主题名称：%s
        搜索词参考：%s
        必看关注：%s
        %s
        时效硬开关：%s
        时效硬窗口天数：%d
        %s

        主要意图：
        %s

        写作价值评分量表维度（权重为百分比，总和 100）：
        %s
        """.formatted(
        StrUtil.blankToDefault(themeName, "(未命名主题)"),
        StrUtil.blankToDefault(queries, "(无)"),
        StrUtil.blankToDefault(mustWatch, "(无)"),
        hoursLine,
        requireFreshness ? "开" : "关",
        HotNewsTopicFreshness.WINDOW_DAYS,
        absoluteWindow,
        StrUtil.blankToDefault(
            intent,
            requireFreshness ? "围绕主题找窗内可写热点。" : "围绕主题找近期可写热点。"),
        dims);
  }
}
