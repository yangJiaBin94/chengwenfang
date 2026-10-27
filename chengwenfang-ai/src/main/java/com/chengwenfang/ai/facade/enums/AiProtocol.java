package com.chengwenfang.ai.facade.enums;

import lombok.Getter;

/** 文本模型协议。 */
@Getter
public enum AiProtocol {
  /** OpenAI Chat Completions（经 LangChain4j）。 */
  OPENAI_CHAT("openai_chat"),
  /** OpenAI Responses API。 */
  OPENAI_RESPONSES("openai_responses");

  private final String value;

  AiProtocol(String value) {
    this.value = value;
  }

  /**
   * 解析协议字符串；空值或未知值默认 {@link #OPENAI_CHAT}。
   * <p>兼容前端连字符写法（{@code openai-chat} / {@code openai-responses}）。
   *
   * @param raw 协议字符串
   * @return 对应枚举
   */
  public static AiProtocol from(String raw) {
    if (raw == null || raw.isBlank()) {
      return OPENAI_CHAT;
    }
    String normalized = raw.trim().toLowerCase().replace('-', '_');
    for (AiProtocol candidate : values()) {
      if (candidate.value.equals(normalized)) {
        return candidate;
      }
    }
    return OPENAI_CHAT;
  }
}
