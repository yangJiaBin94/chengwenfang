package com.chengwenfang.skill.facade.constant;

/**
 * 课程体系成文内置工作流标识常量（key / 展示名 / scene）。
 */
public final class CourseWorkflowKeys {
  private CourseWorkflowKeys() {}

  /** 工作流定义唯一 key。 */
  public static final String DEFINITION_KEY = "course-content-generation";

  /** 工作流展示名。 */
  public static final String DISPLAY_NAME = "生成课程内容";

  /** 与 JobTypes / Skill.scene 对齐的场景码。 */
  public static final String SCENE = "course_content_generation";

  /** 规范主流程图发布说明。 */
  public static final String BUILTIN_RELEASE_NOTES = "规范主流程：原逻辑切段 14 节点";

  /**
   * 公众号转换后的审校进度步（前端第 9 步「审校公众号文章」）。
   * <p>图内无独立 review 节点时，字数/等量校验失败应挂此步，勿挂 generate_wechat。
   */
  public static final String PROGRESS_REVIEW_WECHAT = "review_wechat";
}
