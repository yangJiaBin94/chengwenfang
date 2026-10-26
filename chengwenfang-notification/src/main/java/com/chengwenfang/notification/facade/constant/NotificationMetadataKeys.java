package com.chengwenfang.notification.facade.constant;

/**
 * 通知 metadata 键名（开集常量）：前端与后端实际读取的键，与库列一一对应。
 *
 * <p>视图组装 {@code metadata} 时只输出本类列出的键；写入时只落这些键对应的列。
 */
public final class NotificationMetadataKeys {
  /** 任务类型（metadata.jobType → job_type）。 */
  public static final String JOB_TYPE = "jobType";
  /** 模式编码（metadata.mode → mode_code）。 */
  public static final String MODE = "mode";
  /** 热点文章/作品 ID（metadata.articleId → work_id）。 */
  public static final String ARTICLE_ID = "articleId";
  /** 公众号产出 ID（metadata.documentId → output_id）。 */
  public static final String DOCUMENT_ID = "documentId";
  /** 原稿产出 ID（metadata.draftDocumentId → draft_output_id）。 */
  public static final String DRAFT_DOCUMENT_ID = "draftDocumentId";
  /** 主题 ID（metadata.themeId → theme_id）。 */
  public static final String THEME_ID = "themeId";
  /** 主题编码（metadata.themeCode → theme_code）。 */
  public static final String THEME_CODE = "themeCode";
  /** 主题名称快照（metadata.themeName → theme_name）。 */
  public static final String THEME_NAME = "themeName";
  /** 选题 ID（metadata.topicId → topic_id）。 */
  public static final String TOPIC_ID = "topicId";
  /** 抓取运行 ID（metadata.fetchRunId → fetch_run_id）。 */
  public static final String FETCH_RUN_ID = "fetchRunId";
  /** 调研运行 ID（metadata.researchRunId → research_run_id）。 */
  public static final String RESEARCH_RUN_ID = "researchRunId";
  /** 主体标题（metadata.courseTitle → subject_title）。 */
  public static final String COURSE_TITLE = "courseTitle";
  /** 热点文章标题（metadata.articleTitle → article_title）。 */
  public static final String ARTICLE_TITLE = "articleTitle";
  /** 课程系列名（metadata.seriesName → series_name）。 */
  public static final String SERIES_NAME = "seriesName";
  /** 课序号（metadata.lessonNo → lesson_no）。 */
  public static final String LESSON_NO = "lessonNo";
  /** 失败错误码（metadata.errorCode → error_code）。 */
  public static final String ERROR_CODE = "errorCode";
  /** 失败原始信息（metadata.rawErrorMessage → raw_error_message）。 */
  public static final String RAW_ERROR_MESSAGE = "rawErrorMessage";

  private NotificationMetadataKeys() {}
}
