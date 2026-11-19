package com.chengwenfang.knowledge.facade.constant;

import com.chengwenfang.knowledge.facade.enums.KnowledgeDegradedReason;

/**
 * 知识库 RAG 业务常量（拒答文案、场景提示片段等）。
 */
public final class KnowledgeRagConstants {

  /** 证据不足 / 忠实度未过时的固定拒答句（提示词与门禁均认此短语）。 */
  public static final String ABSTAIN_MESSAGE =
      "知识库中未找到足够相关的内容，无法基于现有资料回答。请换一个与已入库作品相关的问题。";

  /** 证据门：引用少于该数视为不足（可与空列表同拒）。 */
  public static final int MIN_CITATIONS_TO_ANSWER = 2;

  /** Agent 提前停：合并引用达到该数视为证据足够。 */
  public static final int AGENT_ENOUGH_CITATIONS = 3;

  /** 拼进 prompt 的最近原文轮数（一问一答算一轮侧的条数用 2N）。 */
  public static final int RECENT_TURN_MESSAGES = 6;

  /** 最近原文块最大字符。 */
  public static final int RECENT_CONTEXT_MAX_CHARS = 2400;

  /** 降级原因：向量回退内存（兼容旧常量引用，取值见枚举）。 */
  public static final String DEGRADED_VECTOR_MEMORY =
      KnowledgeDegradedReason.VECTOR_FALLBACK_MEMORY.getValue();

  /** 降级原因：图不可用（兼容旧常量引用，取值见枚举）。 */
  public static final String DEGRADED_GRAPH_UNAVAILABLE =
      KnowledgeDegradedReason.GRAPH_UNAVAILABLE.getValue();

  /** 寒暄类回复（不检索、不挂引用卡）。 */
  public static final String CHITCHAT_REPLY =
      "你好，我是成文坊知识库助手。可以直接问文档里的内容，例如某主题下写过哪些方法。";

  /** 新建会话默认标题（首问后会被短标题替换）。 */
  public static final String DEFAULT_SESSION_TITLE = "新的知识库对话";

  /** 自动会话标题最大字数。 */
  public static final int SESSION_TITLE_MAX_CHARS = 20;

  /** 会话摘要软上限：超过后尝试 LLM 压缩。 */
  public static final int SUMMARY_SOFT_LIMIT = 1500;

  /** 会话摘要硬上限：压缩失败时截断。 */
  public static final int SUMMARY_HARD_LIMIT = 2000;

  /** 显式记住写入长期记忆的默认 TTL（天）。 */
  public static final int MEMORY_DEFAULT_TTL_DAYS = 90;

  private KnowledgeRagConstants() {}
}
