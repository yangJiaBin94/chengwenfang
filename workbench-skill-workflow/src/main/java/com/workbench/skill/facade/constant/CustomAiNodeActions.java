package com.workbench.skill.facade.constant;

import java.util.Set;

/**
 * 自定义 AI 节点允许的结构化动作 op 常量（白名单）。
 */
public final class CustomAiNodeActions {
  private CustomAiNodeActions() {}

  /** 声明读取节点已配置 Skill。 */
  public static final String READ_SKILL = "read_skill";
  /** 声明读取文章骨架。 */
  public static final String READ_SKELETON = "read_skeleton";
  /** 声明读取简报。 */
  public static final String READ_BRIEF = "read_brief";
  /** 声明读取资料库线索。 */
  public static final String READ_REFERENCES = "read_references";
  /** 声明读取当前原稿。 */
  public static final String READ_DRAFT = "read_draft";
  /** 声明读取公众号文章。 */
  public static final String READ_WECHAT = "read_wechat";
  /** 写入/覆盖原稿。 */
  public static final String WRITE_DRAFT = "write_draft";
  /** 写入/覆盖公众号文章。 */
  public static final String WRITE_WECHAT = "write_wechat";

  /** JSON 根字段：动作列表。 */
  public static final String FIELD_ACTIONS = "actions";
  /** 动作字段：操作码。 */
  public static final String FIELD_OP = "op";
  /** 动作字段：正文内容（write_*）。 */
  public static final String FIELD_CONTENT = "content";
  /** 动作字段：Skill Id（read_skill）。 */
  public static final String FIELD_SKILL_ID = "skillId";

  /** 全部合法 op。 */
  public static final Set<String> ALL = Set.of(
      READ_SKILL,
      READ_SKELETON,
      READ_BRIEF,
      READ_REFERENCES,
      READ_DRAFT,
      READ_WECHAT,
      WRITE_DRAFT,
      WRITE_WECHAT);

  /**
   * 是否为白名单内的动作码。
   *
   * @param op 动作码
   * @return 合法则为 true
   */
  public static boolean isAllowed(String op) {
    return op != null && ALL.contains(op);
  }
}
