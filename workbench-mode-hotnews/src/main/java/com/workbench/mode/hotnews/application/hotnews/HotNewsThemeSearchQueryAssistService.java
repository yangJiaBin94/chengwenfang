package com.workbench.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.constant.ModelCallScenes;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.common.SearchQueryNormalizer;
import com.workbench.content.facade.dto.request.HotNewsSearchQuerySuggestRequest;
import com.workbench.content.facade.dto.view.HotNewsSearchQuerySuggestView;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.constant.HotNewsSkillNames;
import com.workbench.skill.facade.constant.HotNewsWorkflowKeys;
import com.workbench.skill.facade.dto.view.SkillView;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 热点主题创建：根据主题说明用 AI 总结可检索短搜索词。
 * <p>AI 不可用或解析失败时返回空列表（{@code fromAi=false}），不静默用分词规则凑词。
 * <p>系统提示词来自 Skill「{@link HotNewsSkillNames#THEME_SEARCH_QUERIES}」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsThemeSearchQueryAssistService {
  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** Skill 门面（读取主题搜索词生成指令）。 */
  private final SkillFacade skills;

  /**
   * 生成规范化搜索词。
   *
   * @param request 主题上下文
   * @return 建议结果
   */
  public HotNewsSearchQuerySuggestView suggest(HotNewsSearchQuerySuggestRequest request) {
    HotNewsSearchQuerySuggestRequest body =
        request == null ? new HotNewsSearchQuerySuggestRequest() : request;
    String name = StrUtil.trim(StrUtil.nullToEmpty(body.getName()));
    String description = StrUtil.trim(StrUtil.nullToEmpty(body.getDescription()));
    String domainLabel = StrUtil.trim(StrUtil.nullToEmpty(body.getDomainLabel()));
    List<String> mustWatch = body.getMustWatch() == null ? List.of() : body.getMustWatch();

    List<String> fromAi = tryAi(name, description, domainLabel, mustWatch);
    if (CollUtil.isNotEmpty(fromAi)) {
      return HotNewsSearchQuerySuggestView.builder().queries(fromAi).fromAi(true).build();
    }
    // AI 失败时不返回分词/模板词，避免前端误以为已由模型生成
    log.warn("热点主题搜索词 AI 未产出可用结果 name={}", name);
    return HotNewsSearchQuerySuggestView.builder().queries(List.of()).fromAi(false).build();
  }

  /**
   * 调用文本模型并解析 queries。
   *
   * @param name         主题名
   * @param description  说明
   * @param domainLabel  领域
   * @param mustWatch    关注实体
   * @return 规范化短词；失败空列表
   */
  private List<String> tryAi(
      String name, String description, String domainLabel, List<String> mustWatch) {
    String userPrompt = """
        主题名称：%s
        领域：%s
        主题说明：%s
        关注实体：%s
        """.formatted(
        StrUtil.blankToDefault(name, "未命名"),
        StrUtil.blankToDefault(domainLabel, "未指定"),
        StrUtil.blankToDefault(description, "无"),
        mustWatch.isEmpty() ? "无" : String.join("、", mustWatch));
    String systemPrompt = resolveSystemPrompt();
    if (StrUtil.isBlank(systemPrompt)) {
      log.warn("热点主题搜索词 Skill 缺失或指令为空，跳过 AI name={}", name);
      return List.of();
    }
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(systemPrompt)
          .prompt(userPrompt)
          .scene(ModelCallScenes.HOT_NEWS_THEME_SEARCH_QUERIES)
          .timeoutSeconds(90)
          .build());
      String text = result == null ? null : result.getText();
      List<String> parsed = parseQueries(text);
      List<String> normalized = SearchQueryNormalizer.normalize(parsed);
      if (normalized.isEmpty()) {
        return List.of();
      }
      log.info("热点主题搜索词 AI 成功 name={} count={}", name, normalized.size());
      return normalized;
    } catch (Exception exception) {
      log.warn("热点主题搜索词 AI 失败 name={} err={}", name, exception.getMessage());
      return List.of();
    }
  }

  /**
   * 读取库内 Skill「热点主题搜索词生成」的 systemInstructions；缺失则返回空（不以代码内嵌提示词兜底）。
   *
   * @return 系统提示词；不可用时为空
   */
  private String resolveSystemPrompt() {
    try {
      List<SkillView> all = skills.listSkills();
      if (all != null) {
        for (SkillView skill : all) {
          if (skill == null || !HotNewsSkillNames.THEME_SEARCH_QUERIES.equals(skill.getName())) {
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
      log.warn("读取主题搜索词 Skill 失败 err={}", exception.getMessage());
    }
    return "";
  }

  /**
   * 解析模型返回的 queries 数组。
   *
   * @param text 原始文本
   * @return 原始词列表
   */
  private static List<String> parseQueries(String text) {
    JSONObject json = extractJsonObject(text);
    if (json == null) {
      return List.of();
    }
    JSONArray array = json.getJSONArray("queries");
    if (array == null) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (Object item : array) {
      String query = StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(item)));
      if (StrUtil.isNotBlank(query)) {
        out.add(query);
      }
    }
    return out;
  }

  /**
   * 从模型输出抠出 JSON 对象（容忍 markdown 代码块）。
   *
   * @param text 原始输出
   * @return JSON 对象；失败 null
   */
  private static JSONObject extractJsonObject(String text) {
    if (StrUtil.isBlank(text)) {
      return null;
    }
    String raw = text.trim();
    if (raw.startsWith("```")) {
      int start = raw.indexOf('{');
      int end = raw.lastIndexOf('}');
      if (start >= 0 && end > start) {
        raw = raw.substring(start, end + 1);
      }
    } else {
      int start = raw.indexOf('{');
      int end = raw.lastIndexOf('}');
      if (start >= 0 && end > start) {
        raw = raw.substring(start, end + 1);
      }
    }
    try {
      return JSONUtil.parseObj(raw);
    } catch (Exception ignored) {
      return null;
    }
  }

}
