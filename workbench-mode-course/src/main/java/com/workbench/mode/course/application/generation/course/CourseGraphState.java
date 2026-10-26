package com.workbench.mode.course.application.generation.course;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程成文工作流图状态（固定结构；与 GraphRuntime 的 Map 状态互转）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseGraphState {
  /** 课程 ID。 */
  private String courseId;
  /** Job ID。 */
  private String jobId;
  /** 工作流 ID。 */
  private String workflowId;
  /** 字数档。 */
  private String wordCountOption;
  /** 插图档。 */
  private String imageCountOption;
  /** 仅续跑配图。 */
  private Boolean resumeImagesOnly;
  /** 课程标题。 */
  private String title;
  /** 原稿文章 ID。 */
  private String draftArticleId;
  /** 公众号文章 ID。 */
  private String wechatArticleId;
  /** 上下文摘要。 */
  @Builder.Default
  private Map<String, Object> contextSummary = new LinkedHashMap<>();
  /** 分段原稿列表。 */
  @Builder.Default
  private List<String> draftParts = new ArrayList<>();
  /** 累计原稿。 */
  private String draft;
  /** 公众号正文。 */
  private String wechat;
  /** 配图结果列表。 */
  @Builder.Default
  private List<Map<String, Object>> images = new ArrayList<>();
  /** 封面是否已生成。 */
  private Boolean coverDone;
  /** 正文插图是否已生成。 */
  private Boolean inlineDone;
  /** 是否已提交。 */
  private Boolean committed;
  /** 修复反馈（跨草稿重试）。 */
  private String repairFeedback;
  /** 工作流快照（nodes/edges）。 */
  @Builder.Default
  private Map<String, Object> workflowSnapshot = new LinkedHashMap<>();

  /**
   * 从 GraphRuntime 状态 Map 构建。
   *
   * @param raw 图状态
   * @return 类型化状态
   */
  @SuppressWarnings("unchecked")
  public static CourseGraphState fromMap(Map<String, Object> raw) {
    Map<String, Object> source = raw == null ? Map.of() : raw;
    CourseGraphState state = new CourseGraphState();
    state.courseId = Convert.toStr(source.get(CourseGraphStateKeys.COURSE_ID), "");
    state.jobId = Convert.toStr(source.get(CourseGraphStateKeys.JOB_ID), "");
    state.workflowId = Convert.toStr(source.get(CourseGraphStateKeys.WORKFLOW_ID), "");
    state.wordCountOption = Convert.toStr(source.get(CourseGraphStateKeys.WORD_COUNT_OPTION), "");
    state.imageCountOption = Convert.toStr(source.get(CourseGraphStateKeys.IMAGE_COUNT_OPTION), "");
    if (source.get(CourseGraphStateKeys.RESUME_IMAGES_ONLY) instanceof Boolean resumeFlag) {
      state.resumeImagesOnly = resumeFlag;
    } else if (source.get("resume_images_only") instanceof Boolean legacyResume) {
      state.resumeImagesOnly = legacyResume;
    }
    state.title = Convert.toStr(source.get(CourseGraphStateKeys.TITLE), "");
    state.draftArticleId = Convert.toStr(source.get(CourseGraphStateKeys.DRAFT_ARTICLE_ID), "");
    state.wechatArticleId = Convert.toStr(source.get(CourseGraphStateKeys.WECHAT_ARTICLE_ID), "");
    Object summaryRaw = source.get(CourseGraphStateKeys.CONTEXT_SUMMARY);
    if (summaryRaw instanceof Map<?, ?> summaryMap) {
      state.contextSummary = new LinkedHashMap<>((Map<String, Object>) summaryMap);
    }
    Object partsRaw = source.get(CourseGraphStateKeys.DRAFT_PARTS);
    if (partsRaw instanceof List<?> partsList) {
      List<String> parts = new ArrayList<>();
      for (Object item : partsList) {
        if (item != null) {
          parts.add(String.valueOf(item));
        }
      }
      state.draftParts = parts;
    }
    state.draft = Convert.toStr(source.get(CourseGraphStateKeys.DRAFT), "");
    state.wechat = Convert.toStr(source.get(CourseGraphStateKeys.WECHAT), "");
    Object imagesRaw = source.get(CourseGraphStateKeys.IMAGES);
    if (imagesRaw instanceof List<?> imageList) {
      List<Map<String, Object>> images = new ArrayList<>();
      for (Object item : imageList) {
        if (item instanceof Map<?, ?> imageMap) {
          images.add(new LinkedHashMap<>((Map<String, Object>) imageMap));
        }
      }
      state.images = images;
    }
    if (source.get(CourseGraphStateKeys.COVER_DONE) instanceof Boolean coverFlag) {
      state.coverDone = coverFlag;
    }
    if (source.get(CourseGraphStateKeys.INLINE_DONE) instanceof Boolean inlineFlag) {
      state.inlineDone = inlineFlag;
    }
    if (source.get(CourseGraphStateKeys.COMMITTED) instanceof Boolean committedFlag) {
      state.committed = committedFlag;
    }
    state.repairFeedback = Convert.toStr(source.get(CourseGraphStateKeys.REPAIR_FEEDBACK), "");
    Object snapshotRaw = source.get(CourseGraphStateKeys.SNAPSHOT);
    if (snapshotRaw instanceof Map<?, ?> snapshotMap) {
      state.workflowSnapshot = new LinkedHashMap<>((Map<String, Object>) snapshotMap);
    }
    return state;
  }

  /**
   * 转为 GraphRuntime 可合并的状态 Map。
   *
   * @return 状态增量
   */
  public Map<String, Object> toMap() {
    Map<String, Object> output = new LinkedHashMap<>();
    putIfPresent(output, CourseGraphStateKeys.COURSE_ID, courseId);
    putIfPresent(output, CourseGraphStateKeys.JOB_ID, jobId);
    putIfPresent(output, CourseGraphStateKeys.WORKFLOW_ID, workflowId);
    putIfPresent(output, CourseGraphStateKeys.WORD_COUNT_OPTION, wordCountOption);
    putIfPresent(output, CourseGraphStateKeys.IMAGE_COUNT_OPTION, imageCountOption);
    if (resumeImagesOnly != null) {
      output.put(CourseGraphStateKeys.RESUME_IMAGES_ONLY, resumeImagesOnly);
    }
    putIfPresent(output, CourseGraphStateKeys.TITLE, title);
    putIfPresent(output, CourseGraphStateKeys.DRAFT_ARTICLE_ID, draftArticleId);
    putIfPresent(output, CourseGraphStateKeys.WECHAT_ARTICLE_ID, wechatArticleId);
    if (contextSummary != null && !contextSummary.isEmpty()) {
      output.put(CourseGraphStateKeys.CONTEXT_SUMMARY, contextSummary);
    }
    if (draftParts != null) {
      output.put(CourseGraphStateKeys.DRAFT_PARTS, draftParts);
    }
    putIfPresent(output, CourseGraphStateKeys.DRAFT, draft);
    putIfPresent(output, CourseGraphStateKeys.WECHAT, wechat);
    if (images != null) {
      output.put(CourseGraphStateKeys.IMAGES, images);
    }
    if (coverDone != null) {
      output.put(CourseGraphStateKeys.COVER_DONE, coverDone);
    }
    if (inlineDone != null) {
      output.put(CourseGraphStateKeys.INLINE_DONE, inlineDone);
    }
    if (committed != null) {
      output.put(CourseGraphStateKeys.COMMITTED, committed);
    }
    putIfPresent(output, CourseGraphStateKeys.REPAIR_FEEDBACK, repairFeedback);
    if (workflowSnapshot != null && !workflowSnapshot.isEmpty()) {
      output.put(CourseGraphStateKeys.SNAPSHOT, workflowSnapshot);
    }
    return output;
  }

  /**
   * 重建 {@link com.workbench.mode.course.application.course.CourseGenerationContextBuilder} 所需的 Job 输入 Map。
   *
   * @return 含字数/配图档位等覆盖项的输入
   */
  public Map<String, Object> toContextBuilderInput() {
    Map<String, Object> input = new LinkedHashMap<>();
    if (StrUtil.isNotBlank(wordCountOption)) {
      input.put(CourseGraphStateKeys.WORD_COUNT_OPTION, wordCountOption);
    }
    if (StrUtil.isNotBlank(imageCountOption)) {
      input.put(CourseGraphStateKeys.IMAGE_COUNT_OPTION, imageCountOption);
    }
    if (resumeImagesOnly != null) {
      input.put(CourseGraphStateKeys.RESUME_IMAGES_ONLY, resumeImagesOnly);
    }
    if (StrUtil.isNotBlank(workflowId)) {
      input.put(CourseGraphStateKeys.WORKFLOW_ID, workflowId);
    }
    return input;
  }

  /**
   * 组装 Job 成功输出（draft / wechat / images / shell）。
   *
   * @return Job 输出 Map
   */
  public Map<String, Object> toJobOutputShell() {
    Map<String, Object> output = new LinkedHashMap<>();
    putIfPresent(output, CourseGraphStateKeys.COURSE_ID, courseId);
    putIfPresent(output, CourseGraphStateKeys.WORKFLOW_ID, workflowId);
    putIfPresent(output, CourseGraphStateKeys.DRAFT, draft);
    putIfPresent(output, CourseGraphStateKeys.WECHAT, wechat);
    if (images != null) {
      output.put(CourseGraphStateKeys.IMAGES, images);
    }
    output.put("partial", false);
    Map<String, Object> shell = new LinkedHashMap<>();
    putIfPresent(shell, CourseGraphStateKeys.COURSE_ID, courseId);
    putIfPresent(shell, CourseGraphStateKeys.TITLE, title);
    putIfPresent(shell, CourseGraphStateKeys.DRAFT_ARTICLE_ID, draftArticleId);
    putIfPresent(shell, CourseGraphStateKeys.WECHAT_ARTICLE_ID, wechatArticleId);
    output.put("shell", shell);
    return output;
  }

  /**
   * 非空白字符串才写入状态 Map。
   *
   * @param target 目标 Map
   * @param key    字段名
   * @param value  字符串值
   */
  private static void putIfPresent(Map<String, Object> target, String key, String value) {
    if (StrUtil.isNotBlank(value)) {
      target.put(key, value);
    }
  }
}
