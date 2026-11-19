package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatMessageEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatSessionEntity;
import com.chengwenfang.knowledge.facade.constant.KnowledgeMemoryProvenance;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import com.chengwenfang.knowledge.facade.dto.KnowledgeAnswerPayload;
import com.chengwenfang.knowledge.facade.dto.KnowledgeRetrievalTracePayload;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeToolStepView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeAbstainReason;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChatRole;
import com.chengwenfang.knowledge.facade.enums.KnowledgeRetrieveMode;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatMessageMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatSessionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAG 助手消息落库、会话摘要滚动与检索轨迹写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagAnswerPersistService {

  /** 会话 Mapper。 */
  private final KnowledgeChatSessionMapper sessionMapper;
  /** 消息 Mapper。 */
  private final KnowledgeChatMessageMapper messageMapper;
  /** 观测。 */
  private final KnowledgeRetrievalTraceService retrievalTraceService;
  /** 长期记忆。 */
  private final KnowledgeLongTermMemoryService longTermMemoryService;
  /** 精排（写轨迹用）。 */
  private final KnowledgeRerankService rerankService;
  /** 摘要压缩。 */
  private final AiVisionFacade aiVisionFacade;

  /**
   * 短事务落库用户消息。
   *
   * @param sessionId 会话 ID
   * @param question  问题正文
   */
  @Transactional
  public void persistUserMessage(String sessionId, String question) {
    KnowledgeChatMessageEntity userMessage = KnowledgeChatMessageEntity.builder()
        .id(IdsUtil.newId())
        .sessionId(sessionId)
        .roleKey(KnowledgeChatRole.USER.getValue())
        .content(question)
        .createdAt(LocalDateTime.now())
        .build();
    messageMapper.insert(userMessage);
  }

  /**
   * 落库助手消息并写观测（短事务）。
   *
   * @param session         会话
   * @param sessionId       会话 ID
   * @param answerText      正文
   * @param citations       引用
   * @param abstained       是否拒答
   * @param mode            模式
   * @param rounds          Agent 轮次
   * @param rewritten       改写查询
   * @param memoryOn        是否滚动摘要
   * @param startedMs       开始毫秒
   * @param question        原问题
   * @param degradedReasons 降级原因
   * @param abstainReason   拒答原因
   * @param toolSteps       工具步骤
   * @return 回答视图
   */
  @Transactional
  public KnowledgeChatAnswerView persistAssistantAnswer(
      KnowledgeChatSessionEntity session,
      String sessionId,
      String answerText,
      List<KnowledgeCitationView> citations,
      boolean abstained,
      KnowledgeRetrieveMode mode,
      int rounds,
      String rewritten,
      boolean memoryOn,
      long startedMs,
      String question,
      List<String> degradedReasons,
      KnowledgeAbstainReason abstainReason,
      List<KnowledgeToolStepView> toolSteps) {
    KnowledgeAnswerPayload payload = KnowledgeAnswerPayload.builder()
        .citations(citations)
        .abstained(abstained)
        .mode(mode.getValue())
        .rounds(rounds)
        .rewrittenQuery(rewritten)
        .degradedReasons(degradedReasons == null ? List.of() : degradedReasons)
        .abstainReason(abstainReason == null ? null : abstainReason.getValue())
        .toolSteps(toolSteps == null ? List.of() : toolSteps)
        .build();
    KnowledgeChatMessageEntity assistantMessage = KnowledgeChatMessageEntity.builder()
        .id(IdsUtil.newId())
        .sessionId(sessionId)
        .roleKey(KnowledgeChatRole.ASSISTANT.getValue())
        .content(answerText)
        .payloadJson(JSONUtil.toJsonStr(payload))
        .createdAt(LocalDateTime.now())
        .build();
    messageMapper.insert(assistantMessage);

    maybeAutoTitle(session, question);
    maybeRememberExplicit(question);
    if (memoryOn && !abstained) {
      session.setSummary(rollSummary(StrUtil.nullToEmpty(session.getSummary()), question, answerText));
      long assistantTurns = messageMapper.selectCount(
          new LambdaQueryWrapper<KnowledgeChatMessageEntity>()
              .eq(KnowledgeChatMessageEntity::getSessionId, sessionId)
              .eq(KnowledgeChatMessageEntity::getRoleKey, KnowledgeChatRole.ASSISTANT.getValue()));
      longTermMemoryService.maybePromoteFromSummary(
          session.getSummary(), (int) Math.min(assistantTurns, Integer.MAX_VALUE));
    }
    session.setUpdatedAt(LocalDateTime.now());
    sessionMapper.updateById(session);

    List<String> chunkIds = citations.stream()
        .map(KnowledgeCitationView::getChunkId)
        .filter(StrUtil::isNotBlank)
        .collect(Collectors.toList());
    KnowledgeRetrievalTracePayload traceExtra = KnowledgeRetrievalTracePayload.builder()
        .question(StrUtil.nullToEmpty(question))
        .degradedReasons(degradedReasons == null ? List.of() : degradedReasons)
        .tools(toolSteps == null ? List.of() : toolSteps)
        .build();
    retrievalTraceService.record(
        sessionId,
        rounds,
        mode.getValue(),
        rewritten,
        chunkIds,
        rerankService.isEnabled(),
        abstained
            ? (abstainReason == null
                ? KnowledgeAbstainReason.INSUFFICIENT_EVIDENCE.getValue()
                : abstainReason.getValue())
            : null,
        (int) (System.currentTimeMillis() - startedMs),
        traceExtra);

    log.info(
        "知识库问答完成 sessionId={} mode={} abstained={} citations={} degraded={}",
        sessionId,
        mode.getValue(),
        abstained,
        citations.size(),
        degradedReasons);
    String abstainReasonValue = abstainReason == null ? null : abstainReason.getValue();
    return KnowledgeChatAnswerView.builder()
        .message(toMessageView(assistantMessage, citations))
        .citations(citations)
        .abstained(abstained)
        .mode(mode.getValue())
        .rounds(rounds)
        .rewrittenQuery(rewritten)
        .degradedReasons(degradedReasons)
        .abstainReason(abstainReasonValue)
        .toolSteps(toolSteps)
        .build();
  }

  /**
   * 实体转消息视图。
   *
   * @param entity    消息实体
   * @param citations 引用
   * @return 视图
   */
  public static KnowledgeChatMessageView toMessageView(
      KnowledgeChatMessageEntity entity, List<KnowledgeCitationView> citations) {
    KnowledgeAnswerPayload payload = parsePayload(entity.getPayloadJson());
    return KnowledgeChatMessageView.builder()
        .id(entity.getId())
        .role(KnowledgeChatRole.from(entity.getRoleKey()))
        .content(entity.getContent())
        .citations(citations)
        .abstained(payload == null ? null : payload.getAbstained())
        .mode(payload == null ? null : payload.getMode())
        .rounds(payload == null ? null : payload.getRounds())
        .degradedReasons(payload == null ? null : payload.getDegradedReasons())
        .abstainReason(payload == null ? null : payload.getAbstainReason())
        .toolSteps(payload == null ? null : payload.getToolSteps())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 从 payload 解析引用列表。
   *
   * @param payloadJson JSON
   * @return 引用列表
   */
  public static List<KnowledgeCitationView> parseCitations(String payloadJson) {
    KnowledgeAnswerPayload payload = parsePayload(payloadJson);
    if (payload == null || payload.getCitations() == null) {
      return List.of();
    }
    return payload.getCitations();
  }

  /**
   * 解析助手 payload。
   *
   * @param payloadJson JSON
   * @return 载荷；失败为空
   */
  static KnowledgeAnswerPayload parsePayload(String payloadJson) {
    if (StrUtil.isBlank(payloadJson)) {
      return null;
    }
    try {
      return JSONUtil.toBean(payloadJson, KnowledgeAnswerPayload.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 默认会话标题时用首问生成短标题。
   *
   * @param session  会话
   * @param question 本轮问题
   */
  private void maybeAutoTitle(KnowledgeChatSessionEntity session, String question) {
    if (session == null || StrUtil.isBlank(question) || RagChatService.isChitchat(question)) {
      return;
    }
    String current = StrUtil.nullToEmpty(session.getTitle()).trim();
    if (StrUtil.isNotBlank(current)
        && !KnowledgeRagConstants.DEFAULT_SESSION_TITLE.equals(current)) {
      return;
    }
    session.setTitle(StrUtil.maxLength(question.trim(), KnowledgeRagConstants.SESSION_TITLE_MAX_CHARS));
  }

  /**
   * 用户显式「记住」时写入长期记忆。
   *
   * @param question 本轮问题
   */
  private void maybeRememberExplicit(String question) {
    String content = RagChatService.extractRememberContent(question);
    if (StrUtil.isBlank(content)) {
      return;
    }
    longTermMemoryService.remember(
        content,
        KnowledgeMemoryProvenance.USER_EXPLICIT,
        KnowledgeRagConstants.MEMORY_DEFAULT_TTL_DAYS);
  }

  /**
   * 滚动会话摘要；超软上限时尝试 LLM 压缩。
   *
   * @param previous 旧摘要
   * @param question 本轮问
   * @param answer   本轮答
   * @return 新摘要
   */
  private String rollSummary(String previous, String question, String answer) {
    String next = "Q: " + question + "\nA: " + StrUtil.maxLength(answer, 400);
    String merged = StrUtil.isBlank(previous) ? next : previous + "\n" + next;
    if (merged.length() <= KnowledgeRagConstants.SUMMARY_SOFT_LIMIT) {
      return merged;
    }
    String compressed = compressSummaryWithLlm(merged);
    if (StrUtil.isNotBlank(compressed)) {
      return RagChatService.keepSummaryTail(compressed, KnowledgeRagConstants.SUMMARY_HARD_LIMIT);
    }
    return RagChatService.keepSummaryTail(merged, KnowledgeRagConstants.SUMMARY_HARD_LIMIT);
  }

  /**
   * 用 LLM 把过长摘要压成要点；失败返回空串。
   *
   * @param summary 原文摘要
   * @return 压缩结果
   */
  private String compressSummaryWithLlm(String summary) {
    try {
      VisionCompleteResult result = aiVisionFacade.complete(VisionCompleteRequest.builder()
          .systemPrompt(
              "你压缩知识库多轮对话摘要。保留指代所需的关键实体、结论与未决问题。"
                  + "只输出压缩后的中文要点，不要解释。")
          .prompt(summary)
          .scene(ModelCallScenes.KNOWLEDGE_RAG_CHAT)
          .build());
      return result == null ? "" : StrUtil.nullToEmpty(result.getText()).trim();
    } catch (Exception ignored) {
      return "";
    }
  }
}
