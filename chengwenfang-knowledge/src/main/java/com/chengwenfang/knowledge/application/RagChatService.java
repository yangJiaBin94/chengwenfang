package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatMessageEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatSessionEntity;
import com.chengwenfang.knowledge.domain.rule.DocumentExcerptIsolation;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import com.chengwenfang.knowledge.facade.constant.KnowledgeToolNames;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatMessageRequest;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeAbstainReason;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChatRole;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDegradedReason;
import com.chengwenfang.knowledge.facade.enums.KnowledgeRetrieveMode;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatMessageMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatSessionMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 知识库 RAG 问答：改写、路由、检索、可选 Agent、拒答与记忆。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

  /** 默认检索引用条数。 */
  private static final int DEFAULT_TOP_K = 8;

  /** 寒暄短句（不走检索）。 */
  private static final Set<String> CHITCHAT_PHRASES = Set.of(
      "你好", "您好", "嗨", "哈喽", "hello", "hi", "hey",
      "在吗", "早上好", "下午好", "晚上好", "谢谢", "感谢", "再见", "拜拜");

  /** 会话 Mapper。 */
  private final KnowledgeChatSessionMapper sessionMapper;
  /** 消息 Mapper。 */
  private final KnowledgeChatMessageMapper messageMapper;
  /** 混合检索。 */
  private final HybridRetriever hybridRetriever;
  /** 多模态补全。 */
  private final AiVisionFacade aiVisionFacade;
  /** 配置。 */
  private final ChengwenfangProperties properties;
  /** 改写。 */
  private final KnowledgeQueryRewriteService rewriteService;
  /** 精排。 */
  private final KnowledgeRerankService rerankService;
  /** 路由。 */
  private final KnowledgeRetrieveRouter retrieveRouter;
  /** 图存储。 */
  private final KnowledgeGraphStore graphStore;
  /** Agent。 */
  private final AgenticRetrieveLoop agenticRetrieveLoop;
  /** 长期记忆。 */
  private final KnowledgeLongTermMemoryService longTermMemoryService;
  /** 向量存储（用于降级探测）。 */
  private final KnowledgeVectorStore vectorStore;
  /** Context Budget 切片。 */
  private final KnowledgeContextBudget contextBudget;
  /** 忠实度门禁。 */
  private final RagFaithfulnessGate faithfulnessGate;
  /** 助手落库与轨迹。 */
  private final RagAnswerPersistService answerPersistService;
  /** 用户数据范围。 */
  private final OwnerScope ownerScope;

  /**
   * 同步问答（LLM / 检索在事务外；用户消息与助手落库为短事务）。
   *
   * @param sessionId 会话 ID
   * @param request   提问
   * @return 回答视图
   */
  public KnowledgeChatAnswerView ask(String sessionId, KnowledgeChatMessageRequest request) {
    long started = System.currentTimeMillis();
    KnowledgeChatSessionEntity session = sessionMapper.selectById(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_SESSION_NOT_FOUND, "知识库会话不存在", 404);
    }
    ownerScope.assertCanAccess(session.getOwnerUserId());
    String question = request == null ? "" : StrUtil.nullToEmpty(request.getContent()).trim();
    if (StrUtil.isBlank(question)) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "问题不能为空", 400);
    }
    answerPersistService.persistUserMessage(sessionId, question);

    // 寒暄不检索、不挂引用，避免「你好」也甩出资料卡
    List<String> degradedReasons = collectDegradedReasons();
    KnowledgeToolStepRecorder toolSteps = new KnowledgeToolStepRecorder();
    if (isChitchat(question)) {
      return answerPersistService.persistAssistantAnswer(
          session,
          sessionId,
          KnowledgeRagConstants.CHITCHAT_REPLY,
          List.of(),
          false,
          KnowledgeRetrieveMode.HYBRID,
          0,
          question,
          false,
          System.currentTimeMillis(),
          question,
          degradedReasons,
          null,
          toolSteps.snapshot());
    }

    boolean memoryOn = isMemoryEnabled(session);
    String summary = memoryOn ? StrUtil.nullToEmpty(session.getSummary()) : "";
    List<String> recalled = memoryOn ? longTermMemoryService.recall(question, 3) : List.of();
    String memoryBlock = CollUtil.isEmpty(recalled)
        ? ""
        : "长期记忆：\n" + String.join("\n", recalled);
    String rewriteContext = StrUtil.isBlank(memoryBlock)
        ? summary
        : (StrUtil.isBlank(summary) ? memoryBlock : summary + "\n" + memoryBlock);
    long rewriteStart = System.currentTimeMillis();
    String rewritten = rewriteService.rewrite(question, rewriteContext);
    long rewriteLatencyMs = System.currentTimeMillis() - rewriteStart;

    long routeStart = System.currentTimeMillis();
    KnowledgeRetrieveMode mode = retrieveRouter.route(rewritten);
    long routeLatencyMs = System.currentTimeMillis() - routeStart;
    long searchStart = System.currentTimeMillis();
    List<KnowledgeCitationView> citations = retrieveByMode(
        mode,
        rewritten,
        request == null ? null : request.getSourceKinds(),
        request == null ? null : request.getMediaKinds());
    String searchTool = switch (mode) {
      case GRAPH_LOCAL -> KnowledgeToolNames.GRAPH_LOCAL;
      case GRAPH_GLOBAL -> KnowledgeToolNames.GRAPH_GLOBAL;
      default -> KnowledgeToolNames.HYBRID_SEARCH;
    };
    if (CollUtil.isEmpty(citations) && mode != KnowledgeRetrieveMode.HYBRID) {
      log.info("知识库图检索空结果，回退 Hybrid sessionId={} mode={}", sessionId, mode.getValue());
      citations = hybridRetriever.retrieve(
          rewritten,
          DEFAULT_TOP_K,
          request == null ? null : request.getSourceKinds(),
          request == null ? null : request.getMediaKinds());
      mode = KnowledgeRetrieveMode.HYBRID;
      searchTool = KnowledgeToolNames.HYBRID_SEARCH;
    }
    long searchLatencyMs = System.currentTimeMillis() - searchStart;
    citations = finalizeCitations(rewritten, citations);
    int rounds = 0;
    String agentToolContext = "";
    // Agent 仅首轮证据不足时进入，避免常态放大账单
    boolean weakEvidence = CollUtil.isEmpty(citations)
        || citations.size() < KnowledgeRagConstants.MIN_CITATIONS_TO_ANSWER;
    // 首轮 rewrite 只记一次；进 Agent 时复用 rewritten，Agent 内不再首次改写
    toolSteps.append(
        KnowledgeToolNames.REWRITE,
        "question=" + StrUtil.maxLength(question, 80),
        "query=" + StrUtil.maxLength(rewritten, 120),
        rewriteLatencyMs,
        true);
    if (weakEvidence && agenticRetrieveLoop.isEnabled()) {
      log.info("知识库证据不足，进入 Agent sessionId={} hits={}", sessionId,
          citations == null ? 0 : citations.size());
      toolSteps.append(
          KnowledgeToolNames.ROUTE,
          "query",
          mode.getValue(),
          routeLatencyMs,
          true);
      toolSteps.append(
          searchTool,
          "topK=" + DEFAULT_TOP_K,
          "hits=" + (citations == null ? 0 : citations.size()),
          searchLatencyMs,
          true);
      AgenticRetrieveLoop.AgentRetrieveResult agentResult = agenticRetrieveLoop.run(
          question,
          rewriteContext,
          request == null ? null : request.getSourceKinds(),
          request == null ? null : request.getMediaKinds(),
          DEFAULT_TOP_K,
          toolSteps,
          rewritten);
      citations = finalizeCitations(agentResult.getQuery(), agentResult.getCitations());
      rounds = agentResult.getRounds();
      rewritten = agentResult.getQuery();
      mode = KnowledgeRetrieveMode.AGENT;
      agentToolContext = StrUtil.nullToEmpty(agentResult.getToolContext());
    } else {
      toolSteps.append(
          KnowledgeToolNames.ROUTE,
          "query",
          mode.getValue(),
          routeLatencyMs,
          true);
      toolSteps.append(
          searchTool,
          "topK=" + DEFAULT_TOP_K,
          "hits=" + (citations == null ? 0 : citations.size()),
          searchLatencyMs,
          true);
    }
    if (StrUtil.isNotBlank(agentToolContext)) {
      memoryBlock = StrUtil.isBlank(memoryBlock)
          ? agentToolContext
          : memoryBlock + "\n" + agentToolContext;
    }

    boolean abstained = CollUtil.isEmpty(citations)
        || citations.size() < KnowledgeRagConstants.MIN_CITATIONS_TO_ANSWER;
    KnowledgeAbstainReason abstainReason =
        abstained ? KnowledgeAbstainReason.INSUFFICIENT_EVIDENCE : null;
    String answerText;
    if (abstained) {
      log.info("知识库拒答 sessionId={} reason=insufficient_evidence hits={}",
          sessionId, citations == null ? 0 : citations.size());
      answerText = KnowledgeRagConstants.ABSTAIN_MESSAGE;
      mode = KnowledgeRetrieveMode.ABSTAIN;
    } else {
      String recentBlock = loadRecentDialogue(sessionId);
      String excerptsBody = buildExcerptsBody(citations);
      KnowledgeContextBudget.SliceResult sliced = contextBudget.slice(
          summary, recentBlock, memoryBlock, excerptsBody);
      if (!sliced.truncated().isEmpty()) {
        toolSteps.append(
            KnowledgeToolNames.CONTEXT_BUDGET,
            "tokenBudget",
            "truncated=" + String.join(",", sliced.truncated()),
            0L,
            true);
      }
      String systemPrompt = DocumentExcerptIsolation.buildSystemPrompt(
          KnowledgeRagConstants.ABSTAIN_MESSAGE, sliced.citations());
      VisionCompleteResult completion = aiVisionFacade.complete(VisionCompleteRequest.builder()
          .systemPrompt(systemPrompt)
          .prompt(buildUserPrompt(question, sliced.summary(), sliced.recent(), sliced.memory()))
          .scene(ModelCallScenes.KNOWLEDGE_RAG_CHAT)
          .build());
      answerText = completion == null ? "" : StrUtil.nullToEmpty(completion.getText());
      // passes 内含开关、表面 span 与可选 LLM；关闭 faithfulness 时不强制拒答
      if (StrUtil.isBlank(answerText)
          || !faithfulnessGate.passes(question, answerText, citations, toolSteps)) {
        log.info("知识库拒答 sessionId={} reason=faithfulness_failed", sessionId);
        answerText = KnowledgeRagConstants.ABSTAIN_MESSAGE;
        abstained = true;
        abstainReason = KnowledgeAbstainReason.FAITHFULNESS_FAILED;
        mode = KnowledgeRetrieveMode.ABSTAIN;
      }
    }

    return answerPersistService.persistAssistantAnswer(
        session,
        sessionId,
        answerText,
        citations,
        abstained,
        mode,
        rounds,
        rewritten,
        memoryOn,
        started,
        question,
        degradedReasons,
        abstainReason,
        toolSteps.snapshot());
  }

  /**
   * 收集检索侧降级原因。
   *
   * @return 原因码
   */
  private List<String> collectDegradedReasons() {
    List<String> reasons = new ArrayList<>();
    if (vectorStore != null && vectorStore.degraded()) {
      reasons.add(KnowledgeDegradedReason.VECTOR_FALLBACK_MEMORY.getValue());
    }
    if (graphStore != null && graphStore.enabled() && !graphStore.available()) {
      reasons.add(KnowledgeDegradedReason.GRAPH_UNAVAILABLE.getValue());
    }
    return reasons;
  }

  /**
   * 加载最近若干条对话原文（不含本轮刚插入的用户句之后的空助手）。
   *
   * @param sessionId 会话
   * @return 文本块
   */
  private String loadRecentDialogue(String sessionId) {
    List<KnowledgeChatMessageEntity> rows = messageMapper.selectList(
        new LambdaQueryWrapper<KnowledgeChatMessageEntity>()
            .eq(KnowledgeChatMessageEntity::getSessionId, sessionId)
            .orderByDesc(KnowledgeChatMessageEntity::getCreatedAt)
            .last("LIMIT " + KnowledgeRagConstants.RECENT_TURN_MESSAGES));
    if (CollUtil.isEmpty(rows)) {
      return "";
    }
    List<KnowledgeChatMessageEntity> chronological = new ArrayList<>(rows);
    java.util.Collections.reverse(chronological);
    StringBuilder builder = new StringBuilder();
    for (KnowledgeChatMessageEntity row : chronological) {
      String role = KnowledgeChatRole.USER.matches(row.getRoleKey()) ? "用户" : "助手";
      builder.append(role).append("：")
          .append(StrUtil.maxLength(StrUtil.nullToEmpty(row.getContent()), 400))
          .append('\n');
    }
    return StrUtil.maxLength(builder.toString().trim(), KnowledgeRagConstants.RECENT_CONTEXT_MAX_CHARS);
  }

  /**
   * 是否寒暄短句。
   *
   * @param question 用户输入
   * @return 寒暄
   */
  static boolean isChitchat(String question) {
    String normalized = StrUtil.nullToEmpty(question).trim()
        .replaceAll("[\\s　！!？?。,.，~～]+$", "")
        .toLowerCase(Locale.ROOT);
    return CHITCHAT_PHRASES.contains(normalized);
  }

  /**
   * 去低价值块 → 精排 → 摘要指纹去重。
   *
   * @param query     查询
   * @param citations 候选
   * @return 最终引用
   */
  private List<KnowledgeCitationView> finalizeCitations(
      String query, List<KnowledgeCitationView> citations) {
    List<KnowledgeCitationView> usable = dropLowValueCitations(citations);
    if (CollUtil.isEmpty(usable)) {
      return List.of();
    }
    return dedupeCitations(rerankService.rerank(query, usable, DEFAULT_TOP_K));
  }

  /**
   * 丢掉插图占位/配图说明等对问答几乎无用的块。
   *
   * @param citations 原列表
   * @return 过滤后
   */
  static List<KnowledgeCitationView> dropLowValueCitations(List<KnowledgeCitationView> citations) {
    if (CollUtil.isEmpty(citations)) {
      return List.of();
    }
    List<KnowledgeCitationView> kept = new ArrayList<>();
    for (KnowledgeCitationView citation : citations) {
      if (!isLowValueSnippet(citation.getSnippet())) {
        kept.add(citation);
      }
    }
    return kept;
  }

  /**
   * 是否为插图说明类低价值摘要。
   *
   * @param snippet 摘要
   * @return 低价值
   */
  static boolean isLowValueSnippet(String snippet) {
    String text = StrUtil.nullToEmpty(snippet).trim();
    if (StrUtil.isBlank(text)) {
      return true;
    }
    if (text.contains("插图位置")) {
      return true;
    }
    // 几乎全是配图/封面说明且缺少论述句
    boolean looksLikeImageBrief =
        text.contains("封面图")
            || text.contains("配图")
            || text.contains("/api/images/");
    if (looksLikeImageBrief && !text.contains("。") && !text.contains("？")) {
      return true;
    }
    return false;
  }

  /**
   * 按文档+摘要指纹去重（父子块/相邻标题切到同一插图段时 heading 不同也会重复）。
   *
   * @param citations 原列表
   * @return 去重后
   */
  static List<KnowledgeCitationView> dedupeCitations(List<KnowledgeCitationView> citations) {
    if (CollUtil.isEmpty(citations)) {
      return List.of();
    }
    Map<String, KnowledgeCitationView> unique = new LinkedHashMap<>();
    for (KnowledgeCitationView citation : citations) {
      String snippetFinger = StrUtil.maxLength(
          StrUtil.nullToEmpty(citation.getSnippet()).replaceAll("\\s+", ""),
          120);
      String key = StrUtil.blankToDefault(citation.getDocumentId(), "") + "|" + snippetFinger;
      unique.putIfAbsent(key, citation);
    }
    return new ArrayList<>(unique.values());
  }

  /**
   * 按模式检索。
   *
   * @param mode        模式
   * @param query       查询
   * @param sourceKinds 来源
   * @param mediaKinds  介质
   * @return 引用
   */
  private List<KnowledgeCitationView> retrieveByMode(
      KnowledgeRetrieveMode mode,
      String query,
      List<com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind> sourceKinds,
      List<com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind> mediaKinds) {
    return switch (mode) {
      case GRAPH_LOCAL -> hybridRetriever.retainActiveCitations(
          graphStore.searchLocal(query, DEFAULT_TOP_K));
      case GRAPH_GLOBAL -> hybridRetriever.retainActiveCitations(
          graphStore.searchGlobal(query, DEFAULT_TOP_K));
      default -> hybridRetriever.retrieve(query, DEFAULT_TOP_K, sourceKinds, mediaKinds);
    };
  }

  /**
   * 会话是否启用记忆。
   *
   * @param session 会话
   * @return 启用
   */
  private boolean isMemoryEnabled(KnowledgeChatSessionEntity session) {
    boolean global = properties.getKnowledge() == null
        || properties.getKnowledge().getMemory() == null
        || properties.getKnowledge().getMemory().isEnabled();
    if (!global) {
      return false;
    }
    return session.getMemoryEnabled() == null || Boolean.TRUE.equals(session.getMemoryEnabled());
  }

  /**
   * 从「请记住…」类句子抽出待记内容。
   *
   * @param question 问题
   * @return 记忆正文；非显式记住则空
   */
  static String extractRememberContent(String question) {
    String text = StrUtil.nullToEmpty(question).trim();
    if (StrUtil.isBlank(text)) {
      return "";
    }
    // 不用裸「记住」，避免「如何记住重点」误写入长期记忆
    String[] markers = {"请记住：", "请记住:", "请记住", "记住：", "记住:", "以后都按"};
    for (String marker : markers) {
      int index = text.indexOf(marker);
      if (index < 0) {
        continue;
      }
      String rest = text.substring(index + marker.length()).trim();
      rest = rest.replaceAll("^[，,：:\\s]+", "").trim();
      if (StrUtil.isNotBlank(rest)) {
        return rest;
      }
      // 「以后都按我上次说的来」整句也可记
      return text;
    }
    return "";
  }

  /**
   * 摘要超长时保留尾部。
   *
   * @param summary 摘要
   * @param maxChars 上限
   * @return 截断结果
   */
  static String keepSummaryTail(String summary, int maxChars) {
    String text = StrUtil.nullToEmpty(summary);
    if (maxChars <= 0 || text.length() <= maxChars) {
      return text;
    }
    return text.substring(text.length() - maxChars);
  }

  /**
   * 组装编号摘录正文（供 Context Budget 与注入隔离）。
   *
   * @param citations 引用
   * @return 摘录正文
   */
  private static String buildExcerptsBody(List<KnowledgeCitationView> citations) {
    StringBuilder excerpts = new StringBuilder();
    int index = 1;
    for (KnowledgeCitationView citation : citations) {
      excerpts.append('[').append(index++).append("] 《")
          .append(StrUtil.blankToDefault(citation.getTitle(), "未命名"))
          .append("》");
      if (StrUtil.isNotBlank(citation.getHeadingPath())) {
        excerpts.append(" / ").append(citation.getHeadingPath());
      }
      excerpts.append('\n')
          .append(DocumentExcerptIsolation.sanitizeExcerpt(
              StrUtil.blankToDefault(citation.getSnippet(), "")))
          .append("\n\n");
    }
    return excerpts.toString();
  }

  /**
   * 构建用户提示（摘要 + 记忆 + 最近原文 + 当前问题）。
   *
   * @param question    问题
   * @param summary     摘要
   * @param recentBlock 最近对话
   * @param memoryBlock 长期记忆
   * @return 用户提示
   */
  private static String buildUserPrompt(
      String question, String summary, String recentBlock, String memoryBlock) {
    StringBuilder builder = new StringBuilder();
    if (StrUtil.isNotBlank(summary)) {
      builder.append("会话摘要：\n").append(summary).append("\n\n");
    }
    if (StrUtil.isNotBlank(memoryBlock)) {
      builder.append(memoryBlock).append("\n\n");
    }
    if (StrUtil.isNotBlank(recentBlock)) {
      builder.append("最近对话：\n").append(recentBlock).append("\n\n");
    }
    builder.append("当前问题：\n").append(question);
    return builder.toString();
  }

  /**
   * 列出会话消息。
   *
   * @param sessionId 会话 ID
   * @return 消息视图列表
   */
  public List<KnowledgeChatMessageView> listMessages(String sessionId) {
    KnowledgeChatSessionEntity session = sessionMapper.selectById(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_SESSION_NOT_FOUND, "知识库会话不存在", 404);
    }
    ownerScope.assertCanAccess(session.getOwnerUserId());
    List<KnowledgeChatMessageEntity> rows = messageMapper.selectList(
        new LambdaQueryWrapper<KnowledgeChatMessageEntity>()
            .eq(KnowledgeChatMessageEntity::getSessionId, sessionId)
            .orderByAsc(KnowledgeChatMessageEntity::getCreatedAt));
    return rows.stream()
        .map(row -> {
          List<KnowledgeCitationView> citations = KnowledgeChatRole.ASSISTANT.matches(row.getRoleKey())
              ? RagAnswerPersistService.parseCitations(row.getPayloadJson())
              : List.of();
          return RagAnswerPersistService.toMessageView(row, citations);
        })
        .toList();
  }
}
