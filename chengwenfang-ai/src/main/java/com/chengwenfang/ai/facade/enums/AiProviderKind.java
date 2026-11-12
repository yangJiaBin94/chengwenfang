package com.chengwenfang.ai.facade.enums;

/** AI 提供商配置 kind：image / vision / embedding。 */
public enum AiProviderKind {
  /** 图像生成模型。 */
  IMAGE("image"),
  /** 多模态补全模型（可含图）。 */
  VISION("vision"),
  /** 文本嵌入模型。 */
  EMBEDDING("embedding");

  private final String value;

  AiProviderKind(String value) {
    this.value = value;
  }

  /** 持久化 / API 用的小写字符串。 */
  public String getValue() {
    return value;
  }

  /** 解析 kind；无法识别时抛出 IllegalArgumentException。 */
  public static AiProviderKind from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("AI kind 不能为空");
    }
    String normalized = raw.trim().toLowerCase();
    for (AiProviderKind candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("未知 AI kind: " + raw);
  }
}
