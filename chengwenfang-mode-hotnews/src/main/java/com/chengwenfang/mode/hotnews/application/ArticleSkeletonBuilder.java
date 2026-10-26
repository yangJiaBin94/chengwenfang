package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.mode.hotnews.application.prompt.ArticleSkeletonPrompts;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsWordCountOption;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.constant.HotNewsSkillNames;
import com.chengwenfang.skill.facade.constant.HotNewsWorkflowKeys;
import com.chengwenfang.skill.facade.dto.view.SkillView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 热点文章骨架 AI 拟定与解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleSkeletonBuilder {
  private final AiTextFacade ai;
  /** Skill 门面：读取骨架配图规范。 */
  private final SkillFacade skills;

  /**
   * 根据选题与参考资料拟定文章骨架；AI 失败时回退启发式骨架。
   * <p>配图风格与生图要求来自 Skill「热点文章骨架配图规范」。
   *
   * @param topicId       选题 ID
   * @param topicTitle    选题标题
   * @param topicSummary  选题摘要
   * @param researchItems 资料池条目
   * @param themeContext  主题读者/边界等上下文；可空
   * @return 文章骨架视图
   */
  public HotNewsArticleSkeletonView buildArticleSkeleton(
      String topicId,
      String topicTitle,
      String topicSummary,
      List<HotNewsResearchItemView> researchItems,
      String themeContext) {
    List<HotNewsResearchItemView> refs = ResearchRelevanceFilter.filterArticleReferences(researchItems);
    List<Map<String, Object>> payloadRefs = new ArrayList<>();
    for (HotNewsResearchItemView item : refs) {
      String summary = item.getSummary() == null ? "" : item.getSummary();
      if (summary.length() > 400) {
        summary = summary.substring(0, 400);
      }
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("title", StrUtil.nullToEmpty(item.getTitle()));
      row.put("summary", summary);
      row.put("url", StrUtil.nullToEmpty(item.getUrl()));
      row.put("score", item.getScore() == null ? 0 : item.getScore());
      payloadRefs.add(row);
    }
    String illustrationSkill = resolveSkeletonIllustrationSkill();
    String systemPrompt = ArticleSkeletonPrompts.SKELETON_SYSTEM.formatted(illustrationSkill);
    if (StrUtil.isNotBlank(themeContext)) {
      systemPrompt = systemPrompt + "\n\n主题上下文（须遵守读者与边界）：\n" + themeContext.trim();
    }
    try {
      Map<String, Object> user = new LinkedHashMap<>();
      user.put("topicTitle", StrUtil.nullToEmpty(topicTitle));
      user.put("topicSummary", StrUtil.nullToEmpty(topicSummary));
      if (StrUtil.isNotBlank(themeContext)) {
        user.put("themeContext", themeContext.trim());
      }
      user.put("references", payloadRefs);
      TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
          .scene(ModelCallScenes.HOT_NEWS_ARTICLE_SKELETON)
          .systemPrompt(systemPrompt)
          .prompt(JSONUtil.toJsonStr(user))
          .timeoutSeconds(120)
          .build());
      HotNewsArticleSkeletonView parsed = parseSkeleton(
          topicId, result == null ? null : result.getText());
      if (parsed != null && StrUtil.isNotBlank(parsed.getCoreThesis())) {
        log.info("文章骨架 AI 拟定成功 topicId={} modules={}",
            topicId,
            parsed.getModules() == null ? 0 : parsed.getModules().size());
        return parsed;
      }
    } catch (Exception ex) {
      log.warn("文章骨架 AI 拟定失败，回退启发式 err={}", ex.getMessage());
    }
    return heuristicSkeleton(topicId, topicTitle, topicSummary, refs);
  }

  /**
   * 读取骨架配图 Skill 的 systemInstructions；缺失时回退默认硬性要求。
   *
   * @return Skill 指令文本
   */
  private String resolveSkeletonIllustrationSkill() {
    try {
      List<SkillView> all = skills.listSkills();
      if (all != null) {
        for (SkillView skill : all) {
          if (skill == null || !HotNewsSkillNames.SKELETON_ILLUSTRATION.equals(skill.getName())) {
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
    } catch (Exception ex) {
      log.warn("读取骨架配图 Skill 失败，使用默认要求 err={}", ex.getMessage());
    }
    return HotNewsMarkdownUtil.defaultImagePromptRequirements();
  }

  /**
   * 解析骨架 JSON。
   *
   * @param topicId 选题 ID
   * @param raw     AI 原文
   * @return 骨架视图；解析失败 null
   */
  static HotNewsArticleSkeletonView parseSkeleton(String topicId, String raw) {
    JSONObject json = extractJsonObject(raw);
    if (json == null) {
      return null;
    }
    List<String> architecture = stringList(json.getJSONArray("architecture"));
    List<String> flow = stringList(json.getJSONArray("flow"));
    List<String> hints = stringList(json.getJSONArray("referenceHints"));
    List<String> keyInsights = stringList(json.getJSONArray("keyInsights"));
    List<String> impacts = stringList(json.getJSONArray("impacts"));
    List<String> readerSignals = stringList(json.getJSONArray("readerSignals"));
    List<String> sectionPlan = stringList(json.getJSONArray("sectionPlan"));
    List<Map<String, Object>> modules = new ArrayList<>();
    JSONArray moduleArr = json.getJSONArray("modules");
    if (moduleArr != null) {
      for (Object row : moduleArr) {
        if (!(row instanceof JSONObject obj)) {
          continue;
        }
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("name", StrUtil.trim(obj.getStr("name")));
        module.put("purpose", StrUtil.trim(obj.getStr("purpose")));
        module.put("keyPoints", stringList(obj.getJSONArray("keyPoints")));
        modules.add(module);
      }
    }
    String illustrationStyle = StrUtil.blankToDefault(
        StrUtil.trim(json.getStr("illustrationStyle")),
        HotNewsMarkdownUtil.DEFAULT_ILLUSTRATION_STYLE);
    String coverBrief = StrUtil.blankToDefault(
        StrUtil.trim(json.getStr("coverBrief")),
        StrUtil.blankToDefault(StrUtil.trim(json.getStr("workingTitle")), "热点封面"));
    String imagePromptRequirements = StrUtil.blankToDefault(
        StrUtil.trim(json.getStr("imagePromptRequirements")),
        HotNewsMarkdownUtil.defaultImagePromptRequirements());
    String wordOption = HotNewsWordCountOption.normalize(json.getStr("recommendedWordCountOption"));
    String imageOption = HotNewsImageCountOption.normalize(json.getStr("recommendedImageCountOption"));
    Map<String, Object> rawMap = new LinkedHashMap<>(json);
    rawMap.put("illustrationStyle", illustrationStyle);
    rawMap.put("coverBrief", coverBrief);
    rawMap.put("imagePromptRequirements", imagePromptRequirements);
    rawMap.put("recommendedWordCountOption", wordOption);
    rawMap.put("recommendedImageCountOption", imageOption);
    LocalDateTime now = LocalDateTime.now();
    return HotNewsArticleSkeletonView.builder()
        .topicId(topicId)
        .workingTitle(StrUtil.trim(json.getStr("workingTitle")))
        .scope(StrUtil.trim(json.getStr("scope")))
        .coreThesis(StrUtil.trim(json.getStr("coreThesis")))
        .coreEvent(StrUtil.trim(json.getStr("coreEvent")))
        .keyInsights(keyInsights)
        .impacts(impacts)
        .readerSignals(readerSignals)
        .narrativeShape(StrUtil.trim(json.getStr("narrativeShape")))
        .sectionPlan(sectionPlan)
        .illustrationStyle(illustrationStyle)
        .coverBrief(coverBrief)
        .imagePromptRequirements(imagePromptRequirements)
        .recommendedWordCountOption(wordOption)
        .recommendedImageCountOption(imageOption)
        .architecture(architecture)
        .flow(flow)
        .modules(modules)
        .referenceHints(hints)
        .updatedAt(now)
        .raw(rawMap)
        .build();
  }

  private static HotNewsArticleSkeletonView heuristicSkeleton(
      String topicId,
      String topicTitle,
      String topicSummary,
      List<HotNewsResearchItemView> refs) {
    String title = StrUtil.blankToDefault(topicTitle, "热点选题");
    List<String> hints = new ArrayList<>();
    for (HotNewsResearchItemView item : refs) {
      if (StrUtil.isNotBlank(item.getTitle())) {
        hints.add(item.getTitle().trim());
      }
      if (hints.size() >= 5) {
        break;
      }
    }
    String thesis = StrUtil.blankToDefault(topicSummary, title);
    List<String> insights = List.of("抓住事件里真正变化的那一点", "分清公告口径与实际影响");
    List<String> impacts = List.of("对从业者工具链或决策节奏的影响", "对普通人使用方式的潜在变化");
    List<String> signals = List.of("这件事说明方向在往哪偏", "读者可带走的一个可验证判断");
    List<String> sectionPlan = List.of(
        "用具体场景或冲突开场，不要目录式报幕",
        "交代核心事件与关键信息",
        "展开影响与边界",
        "收束到读者可带走的信号");
    List<String> architecture = List.of("现场切入", "变化在哪", "影响与边界", "带走什么");
    List<Map<String, Object>> modules = new ArrayList<>();
    modules.add(Map.of(
        "name", "现场切入",
        "purpose", "用具体场景带出事件",
        "keyPoints", List.of("一个可感知的场景", "事件一句话")));
    modules.add(Map.of(
        "name", "变化在哪",
        "purpose", "点出真正改变的东西",
        "keyPoints", List.of("相对以前的差异", "别被通稿带偏")));
    modules.add(Map.of(
        "name", "影响与边界",
        "purpose", "讲清影响面与不确定处",
        "keyPoints", List.of("谁受影响", "作者判断边界")));
    modules.add(Map.of(
        "name", "带走什么",
        "purpose", "给出信号与可执行判断",
        "keyPoints", List.of("读者信号", "下一步可观察的点")));
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("workingTitle", title);
    raw.put("scope", "围绕选题主线展开，资料仅作可选借鉴");
    raw.put("coreThesis", thesis);
    raw.put("coreEvent", title);
    raw.put("keyInsights", insights);
    raw.put("impacts", impacts);
    raw.put("readerSignals", signals);
    raw.put("narrativeShape", "冲突推进");
    raw.put("sectionPlan", sectionPlan);
    String wordOption = HotNewsWordCountOption.defaultOption().getValue();
    String imageOption = HotNewsImageCountOption.defaultOption().getValue();
    String illustrationStyle = HotNewsMarkdownUtil.DEFAULT_ILLUSTRATION_STYLE;
    String coverBrief = title + "｜一眼看出事件冲突与变化";
    String imagePromptRequirements = HotNewsMarkdownUtil.defaultImagePromptRequirements();
    raw.put("illustrationStyle", illustrationStyle);
    raw.put("coverBrief", coverBrief);
    raw.put("imagePromptRequirements", imagePromptRequirements);
    raw.put("recommendedWordCountOption", wordOption);
    raw.put("recommendedImageCountOption", imageOption);
    raw.put("architecture", architecture);
    raw.put("flow", sectionPlan);
    raw.put("modules", modules);
    raw.put("referenceHints", hints);
    LocalDateTime now = LocalDateTime.now();
    return HotNewsArticleSkeletonView.builder()
        .topicId(topicId)
        .workingTitle(title)
        .scope("围绕选题主线展开，资料仅作可选借鉴")
        .coreThesis(thesis)
        .coreEvent(title)
        .keyInsights(insights)
        .impacts(impacts)
        .readerSignals(signals)
        .narrativeShape("冲突推进")
        .sectionPlan(sectionPlan)
        .illustrationStyle(illustrationStyle)
        .coverBrief(coverBrief)
        .imagePromptRequirements(imagePromptRequirements)
        .recommendedWordCountOption(wordOption)
        .recommendedImageCountOption(imageOption)
        .architecture(architecture)
        .flow(sectionPlan)
        .modules(modules)
        .referenceHints(hints)
        .updatedAt(now)
        .raw(raw)
        .build();
  }

  private static List<String> stringList(JSONArray arr) {
    List<String> out = new ArrayList<>();
    if (arr == null) {
      return out;
    }
    for (Object item : arr) {
      String text = StrUtil.trim(String.valueOf(item));
      if (StrUtil.isNotBlank(text) && !"null".equalsIgnoreCase(text)) {
        out.add(text);
      }
    }
    return out;
  }

  private static JSONObject extractJsonObject(String raw) {
    if (StrUtil.isBlank(raw)) {
      return null;
    }
    try {
      String text = raw.trim();
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start >= 0 && end > start) {
        text = text.substring(start, end + 1);
      }
      return JSONUtil.parseObj(text);
    } catch (Exception ignored) {
      return null;
    }
  }
}
