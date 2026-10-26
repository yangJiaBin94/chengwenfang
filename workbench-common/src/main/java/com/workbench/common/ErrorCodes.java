package com.workbench.common;

/** 跨模块业务错误码（英文标识）；对应 message 使用中文。 */
public final class ErrorCodes {
  public static final String ARTICLE_NOT_FOUND = "article_not_found";
  /** 统一正文文档不存在。 */
  public static final String DOCUMENT_NOT_FOUND = "document_not_found";
  /** 统一文档域与接口不匹配（如对课程文档调热点专用接口）。 */
  public static final String DOCUMENT_DOMAIN_MISMATCH = "document_domain_mismatch";
  public static final String ARTICLE_STALE = "article_stale";
  public static final String VERSION_NOT_FOUND = "version_not_found";
  public static final String VERSION_CONFLICT = "version_conflict";
  public static final String JOB_NOT_FOUND = "job_not_found";
  public static final String JOB_REQUIRED = "job_required";
  public static final String UNSUPPORTED_JOB_TYPE = "unsupported_job_type";
  public static final String INVALID_STATUS = "invalid_status";
  public static final String AI_KEY_MISSING = "ai_key_missing";
  public static final String AI_CONFIG_MISSING = "ai_config_missing";
  public static final String AI_TEST_FAILED = "ai_test_failed";
  public static final String IMAGE_FAILED = "image_failed";
  public static final String TEXT_FAILED = "text_failed";
  public static final String CONNECTION_FAILED = "connection_failed";
  public static final String WORKFLOW_NOT_FOUND = "workflow_not_found";
  public static final String WORKFLOW_REQUIRED = "workflow_required";
  public static final String WORKFLOW_INVALID = "workflow_invalid";
  public static final String WORKFLOW_DRAFT_NOT_FOUND = "workflow_draft_not_found";
  public static final String WORKFLOW_VERSION_NOT_FOUND = "workflow_version_not_found";
  /** 工作流草稿 revision 乐观锁冲突。 */
  public static final String WORKFLOW_REVISION_CONFLICT = "workflow_revision_conflict";
  /** 功能所需工作流已停用或未发布。 */
  public static final String WORKFLOW_UNAVAILABLE = "workflow_unavailable";

  public static final String SKILL_NOT_FOUND = "skill_not_found";
  public static final String ALERT_NOT_FOUND = "alert_not_found";
  public static final String CHANNEL_NOT_FOUND = "channel_not_found";
  /** 主题下来源渠道数量已达上限。 */
  public static final String CHANNEL_LIMIT_EXCEEDED = "channel_limit_exceeded";
  /** 网页渠道 URL 数量超过上限。 */
  public static final String CHANNEL_WEB_URLS_LIMIT_EXCEEDED = "channel_web_urls_limit_exceeded";
  public static final String BRIEF_NOT_FOUND = "brief_not_found";
  public static final String TOPIC_NOT_FOUND = "topic_not_found";
  public static final String RESEARCH_RUN_NOT_FOUND = "research_run_not_found";
  /** 热点抓取运行不存在。 */
  public static final String FETCH_RUN_NOT_FOUND = "fetch_run_not_found";
  public static final String RESEARCH_ITEM_EXISTS = "research_item_exists";
  public static final String INVALID_URL = "invalid_url";
  public static final String INVALID_CONTENT_KIND = "invalid_content_kind";
  public static final String FILE_MISSING = "file_missing";
  public static final String FILE_KEY_REQUIRED = "file_key_required";
  public static final String HOT_REVIEW_AI_INVALID = "HOT_REVIEW_AI_INVALID";
  public static final String HOT_ARTICLE_REVIEW_BLOCKED = "HOT_ARTICLE_REVIEW_BLOCKED";
  /** 视频解析能力未启用。 */
  public static final String VIDEO_PARSE_DISABLED = "video_parse_disabled";
  public static final String THEME_NOT_FOUND = "theme_not_found";
  public static final String THEME_CODE_CONFLICT = "theme_code_conflict";
  public static final String THEME_INVALID_STATUS = "theme_invalid_status";
  public static final String THEME_DELETE_FORBIDDEN = "theme_delete_forbidden";
  /** 系统排版主题只读（不可删）。 */
  public static final String THEME_READONLY = "theme_readonly";
  public static final String THEME_DELETED = "theme_deleted";
  public static final String CURRICULUM_DRAFT_INVALID = "curriculum_draft_invalid";
  public static final String CURRICULUM_ALREADY_COMMITTED = "curriculum_already_committed";
  public static final String MODE_NOT_FOUND = "mode_not_found";
  /** 未配置公众号 AppID / AppSecret。 */
  public static final String WECHAT_MP_CONFIG_MISSING = "wechat_mp_config_missing";
  /** 公众号 API 调用失败。 */
  public static final String WECHAT_MP_API_FAILED = "wechat_mp_api_failed";
  /** 推送草稿缺少封面图。 */
  public static final String WECHAT_MP_COVER_REQUIRED = "wechat_mp_cover_required";
  /** 推送草稿正文或图片处理失败。 */
  public static final String WECHAT_MP_DRAFT_FAILED = "wechat_mp_draft_failed";
  /** 绑定的公众号草稿 media_id 已失效（后台已删等）。 */
  public static final String WECHAT_MP_DRAFT_GONE = "wechat_mp_draft_gone";

  private ErrorCodes() {}
}
