package com.workbench.mode.course.application.generation.course;

/**
 * 课程成文图状态键（与 GraphRuntime Map 互转，禁止业务散落魔法字符串）。
 */
public final class CourseGraphStateKeys {
  private CourseGraphStateKeys() {}

  /** 课程 ID。 */
  public static final String COURSE_ID = "courseId";
  /** Job ID。 */
  public static final String JOB_ID = "jobId";
  /** 工作流 ID。 */
  public static final String WORKFLOW_ID = "workflowId";
  /** 字数档。 */
  public static final String WORD_COUNT_OPTION = "wordCountOption";
  /** 插图档。 */
  public static final String IMAGE_COUNT_OPTION = "imageCountOption";
  /** 仅续跑配图。 */
  public static final String RESUME_IMAGES_ONLY = "resumeImagesOnly";
  /** 上下文摘要 Map。 */
  public static final String CONTEXT_SUMMARY = "contextSummary";
  /** 课程标题。 */
  public static final String TITLE = "title";
  /** 原稿文章 ID。 */
  public static final String DRAFT_ARTICLE_ID = "draftArticleId";
  /** 公众号文章 ID。 */
  public static final String WECHAT_ARTICLE_ID = "wechatArticleId";
  /** 分段原稿列表。 */
  public static final String DRAFT_PARTS = "draftParts";
  /** 累计原稿。 */
  public static final String DRAFT = "draft";
  /** 公众号正文。 */
  public static final String WECHAT = "wechat";
  /** 配图结果列表。 */
  public static final String IMAGES = "images";
  /** 封面是否已生成。 */
  public static final String COVER_DONE = "coverDone";
  /** 正文插图是否已生成。 */
  public static final String INLINE_DONE = "inlineDone";
  /** 是否已提交。 */
  public static final String COMMITTED = "committed";
  /** 当前课程步骤标记。 */
  public static final String COURSE_STEP = "courseStep";
  /** 工作流快照（nodes/edges）。 */
  public static final String SNAPSHOT = "workflowSnapshot";
  /** 修复反馈（跨草稿重试）。 */
  public static final String REPAIR_FEEDBACK = "repairFeedback";
}
