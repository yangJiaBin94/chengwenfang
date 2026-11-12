package com.chengwenfang.knowledge.facade.enums;

/** 知识库对话消息角色。 */
public enum KnowledgeChatRole {
  /** 用户。 */
  USER("user"),
  /** 助手。 */
  ASSISTANT("assistant");

  private final String value;

  KnowledgeChatRole(String value) {
    this.value = value;
  }

  /** @return 持久化值 */
  public String getValue() {
    return value;
  }

  /**
   * 解析角色。
   *
   * @param raw 原始字符串
   * @return 枚举
   */
  public static KnowledgeChatRole from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("对话角色不能为空");
    }
    String normalized = raw.trim().toLowerCase();
    for (KnowledgeChatRole candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知对话角色: " + raw);
  }

  /**
   * 是否匹配。
   *
   * @param raw 原始值
   * @return 匹配时 true
   */
  public boolean matches(String raw) {
    return raw != null && value.equalsIgnoreCase(raw.trim());
  }
}
