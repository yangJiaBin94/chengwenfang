package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.sourcing.facade.constant.HotNewsChannelLimits;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.constant.HotNewsSkillNames;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 渠道建议提示词：系统提示与 AI 候选调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSuggestionPromptBuilder {

  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** Skill 门面。 */
  private final SkillFacade skills;

  List<ChannelSuggestionParser.AiChannelCandidate> tryAi(
      String themeName,
      String description,
      List<String> searchQueries,
      List<String> mustWatch,
      String githubQuery,
      String themeContext,
      ChannelSuggestionExistingIndex existingIndex) {
    String userPrompt = """
        主题名称：%s
        主题说明：%s
        关注实体：%s
        已有搜索词：%s
        已有 GitHub 查询：%s
        主题上下文：
        %s
        已有来源（勿重复）：
        %s
        已占用网页 URL（禁止再次推荐）：
        %s
        数量上限：本次最多推荐 %d 条；主题已启用渠道上限 %d（当前已启用 %d，剩余 %d）；网页渠道 urls 不超过 %d 个，且不得与已占用网页 URL 重复。
        """.formatted(
        StrUtil.blankToDefault(themeName, "未命名"),
        StrUtil.blankToDefault(description, "无"),
        CollUtil.isEmpty(mustWatch) ? "无" : String.join("、", mustWatch),
        CollUtil.isEmpty(searchQueries) ? "无" : String.join("、", searchQueries),
        StrUtil.blankToDefault(githubQuery, "无"),
        StrUtil.blankToDefault(themeContext, "无"),
        existingIndex.summaryForPrompt(),
        existingIndex.webUrlsForPrompt(),
        HotNewsThemeSourceChannelGenerateService.MAX_CHANNELS,
        HotNewsChannelLimits.MAX_CHANNELS_PER_THEME,
        existingIndex.enabledCount(),
        Math.max(0, HotNewsChannelLimits.MAX_CHANNELS_PER_THEME - existingIndex.enabledCount()),
        HotNewsThemeSourceChannelGenerateService.MAX_WEB_URLS);
    String systemPrompt = resolveSystemPrompt();
    if (StrUtil.isBlank(systemPrompt)) {
      log.warn("热点主题来源渠道 Skill 缺失或指令为空，跳过 AI name={}", themeName);
      return List.of();
    }
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(systemPrompt)
          .prompt(userPrompt)
          .scene(ModelCallScenes.HOT_NEWS_THEME_SOURCE_CHANNELS)
          .timeoutSeconds(120)
          .build());
      String text = result == null ? null : result.getText();
      List<ChannelSuggestionParser.AiChannelCandidate> parsed =
          ChannelSuggestionParser.parseCandidates(text);
      if (parsed.isEmpty()) {
        return List.of();
      }
      log.info("热点主题来源渠道 AI 成功 name={} count={}", themeName, parsed.size());
      return parsed;
    } catch (Exception exception) {
      log.warn("热点主题来源渠道 AI 失败 name={} err={}", themeName, exception.getMessage());
      return List.of();
    }
  }

  String resolveSystemPrompt() {
    try {
      List<SkillView> all = skills.listSkills();
      if (all != null) {
        for (SkillView skill : all) {
          if (skill == null || !HotNewsSkillNames.THEME_SOURCE_CHANNELS.equals(skill.getName())) {
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
      log.warn("读取主题来源渠道 Skill 失败 err={}", exception.getMessage());
    }
    return "";
  }

  /**
   * 解析主题说明：优先主题表 description，其次 briefing naming.description。
   *
   * @param theme    主题视图
   * @param briefing 问卷
   * @return 说明
   */
  static String resolveDescription(ContentThemeView theme, Map<String, Object> briefing) {
    if (theme != null && StrUtil.isNotBlank(theme.getDescription())) {
      return theme.getDescription().trim();
    }
    Map<String, Object> naming = ChannelSuggestionParser.nestedMap(briefing, "naming");
    Object description = naming.get("description");
    if (description != null && StrUtil.isNotBlank(String.valueOf(description))) {
      return String.valueOf(description).trim();
    }
    return "";
  }


}
