package com.workbench.mode.course.application.course;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.constant.ModelCallScenes;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.ai.facade.dto.view.TextCompleteResult;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.dto.view.ContentThemeView;
import com.workbench.content.facade.dto.view.CurriculumDraftView;
import com.workbench.content.facade.dto.view.CurriculumLessonDraftView;
import com.workbench.content.facade.dto.view.CurriculumSeriesDraftView;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.dto.job.ThemeCurriculumGenerationJobOutput;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 根据主题问卷生成课表草案（AI，失败时回退启发式结构）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeCurriculumOrchestrator {
  private final ContentFacade content;
  private final AiTextFacade ai;

  /**
   * 执行课表生成并写入主题草案。
   *
   * @param job     任务（entityId=themeId）
   * @param context 进度上下文
   */
  public void generate(JobEntity job, JobExecutionContext context) {
    String themeId = job.getEntityId();
    context.progress(job.getId(), "load_theme", 10, "读取主题问卷");
    ContentThemeView theme = content.getTheme(themeId);
    String briefing = theme.getBriefingJson() == null ? "{}" : theme.getBriefingJson();

    context.progress(job.getId(), "model", 40, "调用模型生成课表");
    CurriculumDraftView draft;
    try {
      draft = generateWithAi(theme, briefing);
    } catch (Exception ex) {
      log.warn("主题课表 AI 生成失败，改用启发式草案 themeId={} err={}", themeId, ex.getMessage());
      context.progress(job.getId(), "fallback", 70, "模型不可用，使用启发式课表");
      draft = heuristicDraft(theme, briefing);
    }

    context.progress(job.getId(), "save", 90, "写入课表草案");
    content.applyGeneratedCurriculum(themeId, draft);
    context.completeWith(ThemeCurriculumGenerationJobOutput.builder()
        .ok(true)
        .themeId(themeId)
        .seriesCount(draft.getSeries() == null ? 0 : draft.getSeries().size())
        .build());
    context.progress(job.getId(), "done", 100, "课表草案已就绪");
  }

  private CurriculumDraftView generateWithAi(ContentThemeView theme, String briefingJson) {
    String system = """
        你是课程体系架构师。根据用户问卷 JSON，输出一套完整课程结构。
        只输出 JSON，不要 Markdown 围栏，不要解释。
        JSON 结构：
        {
          "schemaVersion": 1,
          "routeNarrative": "为什么按这个顺序学",
          "series": [
            {
              "tempId": "s1",
              "name": "系列名",
              "sortOrder": 1,
              "positioning": "一句话定位",
              "lessons": [
                {"lessonNo": 1, "title": "课标题", "summary": "摘要", "learningOutcomes": ["可选"]}
              ]
            }
          ]
        }
        约束：系列 3–8 个（尊重问卷 structure 偏好），每系列 3–8 课，标题具体可学，中文。
        """;
    String prompt = "主题名称：" + theme.getName() + "\n主题描述：" + theme.getDescription()
        + "\n问卷 JSON：\n" + briefingJson;
    TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
        .prompt(prompt)
        .systemPrompt(system)
        .scene(ModelCallScenes.THEME_CURRICULUM_GENERATION)
        .timeoutSeconds(120)
        .build());
    String text = result == null ? null : result.getText();
    return parseDraft(text);
  }

  private CurriculumDraftView parseDraft(String text) {
    if (StrUtil.isBlank(text)) {
      throw new IllegalStateException("模型返回为空");
    }
    String json = text.trim();
    if (json.startsWith("```")) {
      int start = json.indexOf('{');
      int end = json.lastIndexOf('}');
      if (start >= 0 && end > start) {
        json = json.substring(start, end + 1);
      }
    }
    JSONObject root = JSONUtil.parseObj(json);
    CurriculumDraftView draft = new CurriculumDraftView();
    draft.setSchemaVersion(root.getInt("schemaVersion", 1));
    draft.setRouteNarrative(root.getStr("routeNarrative", ""));
    List<CurriculumSeriesDraftView> seriesList = new ArrayList<>();
    JSONArray seriesArr = root.getJSONArray("series");
    if (seriesArr != null) {
      for (int i = 0; i < seriesArr.size(); i++) {
        JSONObject s = seriesArr.getJSONObject(i);
        CurriculumSeriesDraftView series = new CurriculumSeriesDraftView();
        series.setTempId(s.getStr("tempId", "s" + (i + 1)));
        series.setName(s.getStr("name"));
        series.setSortOrder(s.getInt("sortOrder", i + 1));
        series.setPositioning(s.getStr("positioning", ""));
        List<CurriculumLessonDraftView> lessons = new ArrayList<>();
        JSONArray lessonArr = s.getJSONArray("lessons");
        if (lessonArr != null) {
          for (int j = 0; j < lessonArr.size(); j++) {
            JSONObject l = lessonArr.getJSONObject(j);
            CurriculumLessonDraftView lesson = new CurriculumLessonDraftView();
            lesson.setLessonNo(l.getInt("lessonNo", j + 1));
            lesson.setTitle(l.getStr("title"));
            lesson.setSummary(l.getStr("summary", ""));
            List<String> outcomes = new ArrayList<>();
            JSONArray out = l.getJSONArray("learningOutcomes");
            if (out != null) {
              for (int k = 0; k < out.size(); k++) {
                outcomes.add(out.getStr(k));
              }
            }
            lesson.setLearningOutcomes(outcomes);
            lessons.add(lesson);
          }
        }
        series.setLessons(lessons);
        seriesList.add(series);
      }
    }
    draft.setSeries(seriesList);
    return draft;
  }

  private CurriculumDraftView heuristicDraft(ContentThemeView theme, String briefingJson) {
    JSONObject briefing = JSONUtil.parseObj(StrUtil.blankToDefault(briefingJson, "{}"));
    int seriesCount = 4;
    int lessonsPer = 4;
    JSONObject structure = briefing.getJSONObject("structure");
    if (structure != null) {
      String seriesPref = structure.getStr("series_count_pref", "3_5");
      if ("5_8".equals(seriesPref)) {
        seriesCount = 6;
      } else if ("custom".equals(seriesPref) && structure.getInt("series_count_custom") != null) {
        seriesCount = Math.max(1, Math.min(12, structure.getInt("series_count_custom")));
      }
      String lessonPref = structure.getStr("lessons_per_series_pref", "3_5");
      if ("5_8".equals(lessonPref)) {
        lessonsPer = 6;
      } else if ("custom".equals(lessonPref) && structure.getInt("lessons_per_series_custom") != null) {
        lessonsPer = Math.max(1, Math.min(12, structure.getInt("lessons_per_series_custom")));
      }
    }
    List<String> mustCover = new ArrayList<>();
    JSONObject scope = briefing.getJSONObject("scope");
    if (scope != null && scope.getJSONArray("must_cover") != null) {
      JSONArray arr = scope.getJSONArray("must_cover");
      for (int i = 0; i < arr.size(); i++) {
        mustCover.add(arr.getStr(i));
      }
    }
    while (mustCover.size() < seriesCount) {
      mustCover.add("进阶模块 " + (mustCover.size() + 1));
    }

    List<CurriculumSeriesDraftView> seriesList = new ArrayList<>();
    for (int i = 0; i < seriesCount; i++) {
      String moduleName = mustCover.get(i);
      CurriculumSeriesDraftView series = new CurriculumSeriesDraftView();
      series.setTempId("s" + (i + 1));
      series.setName(moduleName);
      series.setSortOrder(i + 1);
      series.setPositioning("围绕「" + moduleName + "」建立可迁移认知与练习。");
      List<CurriculumLessonDraftView> lessons = new ArrayList<>();
      for (int j = 1; j <= lessonsPer; j++) {
        CurriculumLessonDraftView lesson = new CurriculumLessonDraftView();
        lesson.setLessonNo(j);
        lesson.setTitle(moduleName + " · 第 " + j + " 课");
        lesson.setSummary("学习「" + theme.getName() + "」中与「" + moduleName + "」相关的核心要点（第 " + j + " 课）。");
        lesson.setLearningOutcomes(List.of("理解本课核心概念", "完成对应练习或复述"));
        lessons.add(lesson);
      }
      series.setLessons(lessons);
      seriesList.add(series);
    }
    return CurriculumDraftView.builder()
        .schemaVersion(1)
        .routeNarrative("先建立基础认知，再按模块递进，最后综合练习。结构由问卷偏好与覆盖范围启发式生成，可在确认页调整。")
        .series(seriesList)
        .build();
  }
}
