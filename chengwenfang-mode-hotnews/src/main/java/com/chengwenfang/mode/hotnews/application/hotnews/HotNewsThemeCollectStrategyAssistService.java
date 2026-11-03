package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.content.facade.dto.request.HotNewsCollectStrategySuggestRequest;
import com.chengwenfang.content.facade.dto.view.HotNewsCollectStrategySuggestView;
import com.chengwenfang.content.facade.dto.view.ThemeCollectStrategyView;
import com.chengwenfang.content.facade.enums.HotNewsCollectStrategyMode;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.constant.HotNewsSkillNames;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 热点主题创建：根据主要意图推荐结构化/非结构化采集策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsThemeCollectStrategyAssistService {
  /** Skill 缺失时的兜底系统提示。 */
  private static final String FALLBACK_SYSTEM = """
      你是热点采集策略顾问。根据主题主要意图，选择采集模式：
      - structured：固定搜索词 + RSS/搜索渠道即可稳定拿到素材
      - unstructured：需要「先发现再深挖」（如先找火热板块再搜个股、人物动态、跨源综合），须由 AI 多轮规划搜索
      只返回合法 JSON：{"mode":"structured|unstructured","rationale":"不超过60字理由"}
      """;

  private final AiTextFacade ai;
  private final SkillFacade skills;

  /**
   * 推荐采集策略。
   *
   * @param request 意图上下文
   * @return 建议结果
   */
  public HotNewsCollectStrategySuggestView suggest(HotNewsCollectStrategySuggestRequest request) {
    HotNewsCollectStrategySuggestRequest body =
        request == null ? new HotNewsCollectStrategySuggestRequest() : request;
    ThemeCollectStrategyView fromAi = tryAi(body);
    if (fromAi != null && fromAi.hasMode()) {
      fromAi.setUserOverride(false);
      return HotNewsCollectStrategySuggestView.builder().strategy(fromAi).fromAi(true).build();
    }
    // AI 失败时按意图启发式：含「板块/个股/人物/发现」倾向非结构化
    ThemeCollectStrategyView fallback = heuristic(body);
    return HotNewsCollectStrategySuggestView.builder().strategy(fallback).fromAi(false).build();
  }

  private ThemeCollectStrategyView tryAi(HotNewsCollectStrategySuggestRequest body) {
    String userPrompt = """
        主题名称：%s
        领域：%s
        主题说明：%s
        主要意图：%s
        要的素材：%s
        不要的素材：%s
        """.formatted(
        StrUtil.blankToDefault(body.getName(), "未命名"),
        StrUtil.blankToDefault(body.getDomainLabel(), "未指定"),
        StrUtil.blankToDefault(body.getDescription(), "无"),
        StrUtil.blankToDefault(body.getIntentSummary(), "无"),
        CollUtil.isEmpty(body.getMaterialWants()) ? "无" : String.join("、", body.getMaterialWants()),
        CollUtil.isEmpty(body.getMaterialAvoids())
            ? "无"
            : String.join("、", body.getMaterialAvoids()));
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(resolveSystemPrompt())
          .prompt(userPrompt)
          .scene(ModelCallScenes.HOT_NEWS_THEME_COLLECT_STRATEGY)
          .timeoutSeconds(60)
          .build());
      return parseStrategy(result == null ? null : result.getText());
    } catch (Exception exception) {
      log.warn("热点主题策略 AI 失败 name={} err={}", body.getName(), exception.getMessage());
      return null;
    }
  }

  private String resolveSystemPrompt() {
    try {
      List<SkillView> all = skills.listSkills();
      if (all != null) {
        for (SkillView skill : all) {
          if (skill == null || !HotNewsSkillNames.THEME_COLLECT_STRATEGY.equals(skill.getName())) {
            continue;
          }
          if (StrUtil.isNotBlank(skill.getScene())
              && !HotNewsWorkflowKeys.SCENE.equals(skill.getScene())) {
            continue;
          }
          if (Boolean.FALSE.equals(skill.getEnabled())) {
            continue;
          }
          if (StrUtil.isNotBlank(skill.getSystemInstructions())) {
            return skill.getSystemInstructions().strip();
          }
        }
      }
    } catch (Exception exception) {
      log.warn("读取主题策略 Skill 失败 err={}", exception.getMessage());
    }
    return FALLBACK_SYSTEM;
  }

  private static ThemeCollectStrategyView parseStrategy(String text) {
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
      JSONObject json = JSONUtil.parseObj(raw.substring(start, end + 1));
      String mode = StrUtil.trim(StrUtil.nullToEmpty(json.getStr("mode"))).toLowerCase();
      if (HotNewsCollectStrategyMode.from(mode).isEmpty()) {
        return null;
      }
      return ThemeCollectStrategyView.builder()
          .mode(mode)
          .rationale(StrUtil.trim(StrUtil.nullToEmpty(json.getStr("rationale"))))
          .userOverride(false)
          .build();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static ThemeCollectStrategyView heuristic(HotNewsCollectStrategySuggestRequest body) {
    String blob = (StrUtil.nullToEmpty(body.getIntentSummary())
            + " "
            + StrUtil.nullToEmpty(body.getDescription())
            + " "
            + String.join(" ", body.getMaterialWants() == null ? List.of() : body.getMaterialWants()))
        .toLowerCase();
    boolean unstructured = blob.contains("板块")
        || blob.contains("个股")
        || blob.contains("大牛")
        || blob.contains("人物")
        || blob.contains("先发现")
        || blob.contains("走势")
        || blob.contains("融资");
    return ThemeCollectStrategyView.builder()
        .mode(unstructured
            ? HotNewsCollectStrategyMode.UNSTRUCTURED.getValue()
            : HotNewsCollectStrategyMode.STRUCTURED.getValue())
        .rationale(unstructured
            ? "意图需要先发现线索再深挖，建议非结构化多轮搜索"
            : "意图可用固定搜索词与渠道覆盖，建议结构化采集")
        .userOverride(false)
        .build();
  }
}
