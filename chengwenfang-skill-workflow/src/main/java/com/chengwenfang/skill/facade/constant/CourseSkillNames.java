package com.chengwenfang.skill.facade.constant;

/**
 * 课程内容生成默认 Skill 名称（与节点级绑定方案一致）。
 */
public final class CourseSkillNames {
  private CourseSkillNames() {}

  /** 成文坊规则理解。 */
  public static final String CHENGWENFANG_RULES = "成文坊规则理解";
  /**
   * 旧版 Skill 名「工作台规则理解」。
   * <p>启动与 bootstrap 时迁到 {@link #CHENGWENFANG_RULES}，绑定解析亦认此别名。
   */
  public static final String LEGACY_RULES = "工作台规则理解";
  /** 课程文章审校。 */
  public static final String COURSE_REVIEW = "课程文章审校";
  /** 图片生成与检查。 */
  public static final String IMAGE_GENERATION = "图片生成与检查";
  /** 课程策划与原稿生成。 */
  public static final String COURSE_DRAFT = "课程策划与原稿生成";
  /** 公众号文章转换。 */
  public static final String WECHAT_CONVERSION = "公众号文章转换";
  /** 配图规划。 */
  public static final String IMAGE_PLANNING = "配图规划";
  /** 完稿规范。 */
  public static final String FINALIZATION = "完稿规范";
  /** 作者风格（可编辑个性 Skill）。 */
  public static final String AUTHOR_STYLE = "作者风格";
}
