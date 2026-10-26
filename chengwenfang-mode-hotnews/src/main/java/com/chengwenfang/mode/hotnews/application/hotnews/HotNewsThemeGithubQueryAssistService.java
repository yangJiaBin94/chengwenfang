package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.content.facade.dto.request.HotNewsGithubQuerySuggestRequest;
import com.chengwenfang.content.facade.dto.view.HotNewsGithubQuerySuggestView;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.constant.HotNewsSkillNames;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 热点主题创建：根据主题说明用 AI 生成 GitHub Search 查询串。
 * <p>AI 不可用或解析失败时返回空串（{@code fromAi=false}），不静默凑词。
 * <p>系统提示词来自 Skill「{@link HotNewsSkillNames#THEME_GITHUB_QUERY}」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsThemeGithubQueryAssistService {
  /** 单条查询串最大长度。 */
  private static final int MAX_QUERY_CHARS = 200;

  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** Skill 门面。 */
  private final SkillFacade skills;

  /**
   * 生成 GitHub 查询串。
   *
   * @param request 主题上下文
   * @return 建议结果
   */
  public HotNewsGithubQuerySuggestView suggest(HotNewsGithubQuerySuggestRequest request) {
    HotNewsGithubQuerySuggestRequest body =
        request == null ? new HotNewsGithubQuerySuggestRequest() : request;
    String name = StrUtil.trim(StrUtil.nullToEmpty(body.getName()));
    String description = StrUtil.trim(StrUtil.nullToEmpty(body.getDescription()));
    String domainLabel = StrUtil.trim(StrUtil.nullToEmpty(body.getDomainLabel()));
    List<String> searchQueries =
        body.getSearchQueries() == null ? List.of() : body.getSearchQueries();

    String fromAi = tryAi(name, description, domainLabel, searchQueries);
    if (StrUtil.isNotBlank(fromAi)) {
      return HotNewsGithubQuerySuggestView.builder().query(fromAi).fromAi(true).build();
    }
    log.warn("热点主题 GitHub 查询串 AI 未产出可用结果 name={}", name);
    return HotNewsGithubQuerySuggestView.builder().query("").fromAi(false).build();
  }

  /**
   * 调用文本模型并解析 query。
   *
   * @param name          主题名
   * @param description   说明
   * @param domainLabel   领域
   * @param searchQueries 已有搜索词
   * @return 规范化查询串；失败空串
   */
  private String tryAi(
      String name, String description, String domainLabel, List<String> searchQueries) {
    String userPrompt = """
        主题名称：%s
        领域：%s
        主题说明：%s
        已有搜索词：%s
        """.formatted(
        StrUtil.blankToDefault(name, "未命名"),
        StrUtil.blankToDefault(domainLabel, "未指定"),
        StrUtil.blankToDefault(description, "无"),
        CollUtil.isEmpty(searchQueries) ? "无" : String.join("、", searchQueries));
    String systemPrompt = resolveSystemPrompt();
    if (StrUtil.isBlank(systemPrompt)) {
      log.warn("热点主题 GitHub 查询 Skill 缺失或指令为空，跳过 AI name={}", name);
      return "";
    }
    try {
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(systemPrompt)
          .prompt(userPrompt)
          .scene(ModelCallScenes.HOT_NEWS_THEME_GITHUB_QUERY)
          .timeoutSeconds(60)
          .build());
      String text = result == null ? null : result.getText();
      String query = normalizeQuery(parseQuery(text));
      if (StrUtil.isBlank(query)) {
        return "";
      }
      log.info("热点主题 GitHub 查询串 AI 成功 name={} len={}", name, query.length());
      return query;
    } catch (Exception exception) {
      log.warn("热点主题 GitHub 查询串 AI 失败 name={} err={}", name, exception.getMessage());
      return "";
    }
  }

  /**
   * 读取库内 Skill 系统提示；缺失则返回空（不以代码内嵌提示词兜底）。
   *
   * @return 系统提示词；不可用时为空
   */
  private String resolveSystemPrompt() {
    try {
      List<SkillView> all = skills.listSkills();
      if (all != null) {
        for (SkillView skill : all) {
          if (skill == null || !HotNewsSkillNames.THEME_GITHUB_QUERY.equals(skill.getName())) {
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
      log.warn("读取主题 GitHub 查询 Skill 失败 err={}", exception.getMessage());
    }
    return "";
  }

  /**
   * 解析模型返回的 query 字段。
   *
   * @param text 原始文本
   * @return 原始 query；失败空串
   */
  private static String parseQuery(String text) {
    JSONObject json = extractJsonObject(text);
    if (json == null) {
      return "";
    }
    return StrUtil.trim(StrUtil.nullToEmpty(json.getStr("query")));
  }

  /**
   * 从模型输出抠出 JSON 对象。
   *
   * @param text 原始输出
   * @return JSON；失败 null
   */
  private static JSONObject extractJsonObject(String text) {
    if (StrUtil.isBlank(text)) {
      return null;
    }
    String raw = text.trim();
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start >= 0 && end > start) {
      raw = raw.substring(start, end + 1);
    }
    try {
      return JSONUtil.parseObj(raw);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 规范化查询串：去首尾空白与包裹引号，截断长度。
   *
   * @param raw 原始串
   * @return 可用串；无效则空
   */
  private static String normalizeQuery(String raw) {
    String query = StrUtil.trim(StrUtil.nullToEmpty(raw));
    if (query.isEmpty()) {
      return "";
    }
    if ((query.startsWith("\"") && query.endsWith("\""))
        || (query.startsWith("'") && query.endsWith("'"))) {
      query = query.substring(1, query.length() - 1).trim();
    }
    if (query.length() > MAX_QUERY_CHARS) {
      query = query.substring(0, MAX_QUERY_CHARS).trim();
    }
    return query;
  }
}
