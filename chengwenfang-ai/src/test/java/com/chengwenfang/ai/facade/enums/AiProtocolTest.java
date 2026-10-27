package com.chengwenfang.ai.facade.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** AI 协议枚举单测。 */
class AiProtocolTest {

  @Test
  void parsesUnderscoreAndHyphen() {
    assertEquals(AiProtocol.OPENAI_CHAT, AiProtocol.from("openai_chat"));
    assertEquals(AiProtocol.OPENAI_CHAT, AiProtocol.from("openai-chat"));
    assertEquals(AiProtocol.OPENAI_RESPONSES, AiProtocol.from("openai_responses"));
    assertEquals(AiProtocol.OPENAI_RESPONSES, AiProtocol.from("openai-responses"));
  }

  @Test
  void blanksDefaultToChat() {
    assertEquals(AiProtocol.OPENAI_CHAT, AiProtocol.from(null));
    assertEquals(AiProtocol.OPENAI_CHAT, AiProtocol.from(""));
    assertEquals(AiProtocol.OPENAI_CHAT, AiProtocol.from("unknown"));
  }
}
