package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.mode.hotnews.application.prompt.ThemeScoringRubricPrompts;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.content.facade.ScoringRubricSupport;
import com.chengwenfang.content.facade.dto.request.HotNewsScoringRubricSuggestRequest;
import com.chengwenfang.content.facade.dto.view.HotNewsScoringRubricSuggestView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricDimensionView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 热点主题创建：根据主题上下文用 AI 生成写作价值评分量表；失败时回退启发式量表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsThemeScoringRubricAssistService {
  private static final Pattern KEY_SANITIZE = Pattern.compile("[^a-z0-9_]+");

  /** 文本 AI。 */
  private final AiTextFacade ai;

  /**
   * 生成评分量表。
   *
   * @param request 主题上下文
   * @return 量表结果
   */
  public HotNewsScoringRubricSuggestView suggest(HotNewsScoringRubricSuggestRequest request) {
    HotNewsScoringRubricSuggestRequest body =
        request == null ? new HotNewsScoringRubricSuggestRequest() : request;
    ScoringRubricView fromAi = tryAi(body);
    if (fromAi != null && ScoringRubricSupport.isValid(fromAi)) {
      return HotNewsScoringRubricSuggestView.builder().rubric(fromAi).fromAi(true).build();
    }
    ScoringRubricView fallback = fallbackRubric(body);
    log.info(
        "热点主题评分量表回退启发式 name={} dims={}",
        body.getName(),
        fallback.getDimensions().size());
    return HotNewsScoringRubricSuggestView.builder().rubric(fallback).fromAi(false).build();
  }

  /**
   * 调用模型生成量表。
   *
   * @param body 请求
   * @return 量表；失败 null
   */
  private ScoringRubricView tryAi(HotNewsScoringRubricSuggestRequest body) {
    String userPrompt = """
        主题名称：%s
        领域：%s
        主题说明：%s
        搜索关键词：%s
        值得写标准：%s
        主要读者：%s
        内容姿态：%s
        明确不做：%s
        请为该主题设计写作价值评分维度与权重。
        """.formatted(
        StrUtil.blankToDefault(body.getName(), "未命名"),
        StrUtil.blankToDefault(body.getDomainLabel(), "未指定"),
        StrUtil.blankToDefault(body.getDescription(), "无"),
        joinOrNone(body.getSearchQueries()),
        joinOrNone(body.getWorthWriting()),
        joinOrNone(body.getReaders()),
        StrUtil.blankToDefault(body.getContentStance(), "无"),
        StrUtil.blankToDefault(body.getOutOfScope(), "无"));
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(ThemeScoringRubricPrompts.SYSTEM_PROMPT)
          .prompt(userPrompt)
          .scene(ModelCallScenes.HOT_NEWS_THEME_SCORING_RUBRIC)
          .timeoutSeconds(90)
          .build());
      String text = result == null ? null : result.getText();
      ScoringRubricView parsed = parseRubric(text);
      if (parsed == null) {
        return null;
      }
      parsed.setGeneratedAt(nowIso());
      if (parsed.getVersion() == null) {
        parsed.setVersion(1);
      }
      ScoringRubricView normalized = ScoringRubricSupport.normalize(parsed);
      if (!ScoringRubricSupport.isValid(normalized)) {
        return null;
      }
      log.info(
          "热点主题评分量表 AI 成功 name={} dims={}",
          body.getName(),
          normalized.getDimensions().size());
      return normalized;
    } catch (Exception exception) {
      log.warn("热点主题评分量表 AI 失败 name={} err={}", body.getName(), exception.getMessage());
      return null;
    }
  }

  /**
   * 解析模型 JSON 为量表。
   *
   * @param text 模型原文
   * @return 量表；失败 null
   */
  private static ScoringRubricView parseRubric(String text) {
    JSONObject json = extractJsonObject(text);
    if (json == null) {
      return null;
    }
    JSONArray array = json.getJSONArray("dimensions");
    if (array == null || array.isEmpty()) {
      return null;
    }
    List<ScoringRubricDimensionView> dims = new ArrayList<>();
    for (Object item : array) {
      if (!(item instanceof JSONObject row)) {
        continue;
      }
      String key = sanitizeKey(row.getStr("key"));
      String label = StrUtil.trim(StrUtil.nullToEmpty(row.getStr("label")));
      String description = StrUtil.trim(StrUtil.nullToEmpty(row.getStr("description")));
      Integer weight = row.getInt("weight");
      if (StrUtil.isBlank(key) || StrUtil.isBlank(label)) {
        continue;
      }
      dims.add(ScoringRubricDimensionView.builder()
          .key(key)
          .label(label)
          .description(description)
          .weight(weight)
          .build());
    }
    if (dims.isEmpty()) {
      return null;
    }
    return ScoringRubricView.builder()
        .version(1)
        .dimensions(dims)
        .scoreGuidance(StrUtil.trim(StrUtil.nullToEmpty(json.getStr("scoreGuidance"))))
        .build();
  }

  /**
   * 启发式回退量表（仍满足 3 维、权重和 100）。
   *
   * @param body 请求
   * @return 合法量表
   */
  private static ScoringRubricView fallbackRubric(HotNewsScoringRubricSuggestRequest body) {
    String theme = StrUtil.blankToDefault(body.getName(), "本主题");
    List<ScoringRubricDimensionView> dims = List.of(
        ScoringRubricDimensionView.builder()
            .key("theme_fit")
            .label("主题契合")
            .description("线索是否落在「" + theme + "」的领域与关键词语义内，触碰不做范围则低分。")
            .weight(35)
            .build(),
        ScoringRubricDimensionView.builder()
            .key("audience_payoff")
            .label("读者收益")
            .description("对目标读者是否有可带走的判断、决策或可执行启发。")
            .weight(35)
            .build(),
        ScoringRubricDimensionView.builder()
            .key("angle_potential")
            .label("成文切入")
            .description("能否写出差异化角度，而不是只能搬运摘要。")
            .weight(30)
            .build());
    return ScoringRubricView.builder()
        .version(1)
        .generatedAt(nowIso())
        .scoreGuidance("高分须同时满足主题契合与读者收益；材料过薄或明显越界应整体压低。")
        .dimensions(dims)
        .build();
  }

  private static String joinOrNone(List<String> values) {
    if (CollUtil.isEmpty(values)) {
      return "无";
    }
    List<String> cleaned = new ArrayList<>();
    for (String value : values) {
      if (StrUtil.isNotBlank(value)) {
        cleaned.add(value.trim());
      }
    }
    return cleaned.isEmpty() ? "无" : String.join("、", cleaned);
  }

  private static String sanitizeKey(String raw) {
    String key = StrUtil.trim(StrUtil.nullToEmpty(raw)).toLowerCase(Locale.ROOT);
    key = KEY_SANITIZE.matcher(key).replaceAll("_");
    key = key.replaceAll("^_+|_+$", "");
    if (key.isEmpty() || !Character.isLetter(key.charAt(0))) {
      return "";
    }
    return key.length() > 48 ? key.substring(0, 48) : key;
  }

  private static String nowIso() {
    return OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private static JSONObject extractJsonObject(String text) {
    if (StrUtil.isBlank(text)) {
      return null;
    }
    String raw = text.trim();
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return null;
    }
    try {
      return JSONUtil.parseObj(raw.substring(start, end + 1));
    } catch (Exception ignored) {
      return null;
    }
  }
}
