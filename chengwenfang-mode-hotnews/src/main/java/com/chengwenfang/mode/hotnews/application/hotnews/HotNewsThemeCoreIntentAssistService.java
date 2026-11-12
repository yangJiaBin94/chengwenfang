package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.content.facade.dto.request.HotNewsCoreIntentSuggestRequest;
import com.chengwenfang.content.facade.dto.view.HotNewsCoreIntentSuggestView;
import com.chengwenfang.content.facade.dto.view.ThemeCoreIntentView;
import com.chengwenfang.prompt.facade.PromptPackFacade;
import com.chengwenfang.prompt.facade.constant.HotNewsPromptPackNames;
import com.chengwenfang.prompt.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 热点主题创建：根据名称/说明推导主要意图供用户确认。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsThemeCoreIntentAssistService {
  /** 提示词包 缺失时的兜底系统提示。 */
  private static final String FALLBACK_SYSTEM = """
      你是热点选题编辑。根据主题名称与说明，提炼用户创建该主题后希望雷达带来的「主要意图」。
      只返回合法 JSON：
      {"summary":"一句话意图","material_wants":["要的素材类型"],"material_avoids":["不要的噪声"],"success_looks_like":"好素材验收句"}
      规则：summary 不超过 80 字；wants/avoids 各 2～6 条短短语；避免空泛「关注新闻」；avoids 须点名百科/机构介绍/纯营销等噪声。
      """;

  private final AiTextFacade ai;
  private final PromptPackFacade promptPacks;

  /**
   * 推导主要意图（confirmed=false，须用户确认）。
   *
   * @param request 主题上下文
   * @return 建议结果
   */
  public HotNewsCoreIntentSuggestView suggest(HotNewsCoreIntentSuggestRequest request) {
    HotNewsCoreIntentSuggestRequest body =
        request == null ? new HotNewsCoreIntentSuggestRequest() : request;
    String name = StrUtil.trim(StrUtil.nullToEmpty(body.getName()));
    String description = StrUtil.trim(StrUtil.nullToEmpty(body.getDescription()));
    String domainLabel = StrUtil.trim(StrUtil.nullToEmpty(body.getDomainLabel()));
    ThemeCoreIntentView intent = tryAi(name, description, domainLabel);
    if (intent != null && intent.hasSummary()) {
      intent.setConfirmed(false);
      return HotNewsCoreIntentSuggestView.builder().intent(intent).fromAi(true).build();
    }
    log.warn("热点主题意图 AI 未产出可用结果 name={}", name);
    return HotNewsCoreIntentSuggestView.builder()
        .intent(ThemeCoreIntentView.builder().confirmed(false).build())
        .fromAi(false)
        .build();
  }

  private ThemeCoreIntentView tryAi(String name, String description, String domainLabel) {
    String userPrompt = """
        主题名称：%s
        领域：%s
        主题说明（用户想用这个主题干什么）：%s
        """.formatted(
        StrUtil.blankToDefault(name, "未命名"),
        StrUtil.blankToDefault(domainLabel, "未指定"),
        StrUtil.blankToDefault(description, "无"));
    String systemPrompt = resolveSystemPrompt();
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(systemPrompt)
          .prompt(userPrompt)
          .scene(ModelCallScenes.HOT_NEWS_THEME_CORE_INTENT)
          .timeoutSeconds(90)
          .build());
      return parseIntent(result == null ? null : result.getText());
    } catch (Exception exception) {
      log.warn("热点主题意图 AI 失败 name={} err={}", name, exception.getMessage());
      return null;
    }
  }

  private String resolveSystemPrompt() {
    try {
      List<PromptPackView> all = promptPacks.listPromptPacks();
      if (all != null) {
        for (PromptPackView promptPack : all) {
          if (promptPack == null || !HotNewsPromptPackNames.THEME_CORE_INTENT.equals(promptPack.getName())) {
            continue;
          }
          if (StrUtil.isNotBlank(promptPack.getScene())
              && !HotNewsWorkflowKeys.SCENE.equals(promptPack.getScene())) {
            continue;
          }
          if (Boolean.FALSE.equals(promptPack.getEnabled())) {
            continue;
          }
          if (StrUtil.isNotBlank(promptPack.getSystemInstructions())) {
            return promptPack.getSystemInstructions().strip();
          }
        }
      }
    } catch (Exception exception) {
      log.warn("读取主题意图 提示词包 失败 err={}", exception.getMessage());
    }
    return FALLBACK_SYSTEM;
  }

  private static ThemeCoreIntentView parseIntent(String text) {
    JSONObject json = extractJsonObject(text);
    if (json == null) {
      return null;
    }
    String summary = StrUtil.trim(StrUtil.nullToEmpty(json.getStr("summary")));
    if (StrUtil.isBlank(summary)) {
      return null;
    }
    return ThemeCoreIntentView.builder()
        .summary(summary)
        .materialWants(stringList(json.getJSONArray("material_wants")))
        .materialAvoids(stringList(json.getJSONArray("material_avoids")))
        .successLooksLike(StrUtil.trim(StrUtil.nullToEmpty(json.getStr("success_looks_like"))))
        .confirmed(false)
        .build();
  }

  private static List<String> stringList(JSONArray array) {
    List<String> out = new ArrayList<>();
    if (array == null) {
      return out;
    }
    for (Object item : array) {
      String text = StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(item)));
      if (StrUtil.isNotBlank(text)) {
        out.add(text);
      }
    }
    return out;
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
