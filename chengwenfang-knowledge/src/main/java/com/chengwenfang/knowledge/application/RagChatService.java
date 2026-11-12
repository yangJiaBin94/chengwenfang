package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatMessageEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatSessionEntity;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatMessageRequest;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChatRole;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatMessageMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatSessionMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 知识库 RAG 问答：检索、补全与消息落库。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagChatService {

  /** 默认检索引用条数。 */
  private static final int DEFAULT_TOP_K = 8;

  /** 会话 Mapper。 */
  private final KnowledgeChatSessionMapper sessionMapper;
  /** 消息 Mapper。 */
  private final KnowledgeChatMessageMapper messageMapper;
  /** 混合检索。 */
  private final HybridRetriever hybridRetriever;
  /** 多模态补全门面。 */
  private final AiVisionFacade aiVisionFacade;

  /**
   * 同步问答。
   *
   * @param sessionId 会话 ID
   * @param request   提问
   * @return 回答视图
   */
  @Transactional
  public KnowledgeChatAnswerView ask(String sessionId, KnowledgeChatMessageRequest request) {
    KnowledgeChatSessionEntity session = sessionMapper.selectById(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_SESSION_NOT_FOUND, "知识库会话不存在", 404);
    }
    String question = request == null ? "" : StrUtil.nullToEmpty(request.getContent()).trim();
    if (StrUtil.isBlank(question)) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "问题不能为空", 400);
    }
    LocalDateTime now = LocalDateTime.now();
    KnowledgeChatMessageEntity userMessage = KnowledgeChatMessageEntity.builder()
        .id(IdsUtil.newId())
        .sessionId(sessionId)
        .roleKey(KnowledgeChatRole.USER.getValue())
        .content(question)
        .createdAt(now)
        .build();
    messageMapper.insert(userMessage);
    List<KnowledgeCitationView> citations = hybridRetriever.retrieve(
        question,
        DEFAULT_TOP_K,
        request == null ? null : request.getSourceKinds(),
        request == null ? null : request.getMediaKinds());
    String systemPrompt = buildSystemPrompt(citations);
    String userPrompt = buildUserPrompt(question, citations);
    VisionCompleteResult completion = aiVisionFacade.complete(VisionCompleteRequest.builder()
        .systemPrompt(systemPrompt)
        .prompt(userPrompt)
        .scene(ModelCallScenes.KNOWLEDGE_RAG_CHAT)
        .build());
    String answerText = completion == null ? "" : StrUtil.nullToEmpty(completion.getText());
    Map<String, Object> payload = new HashMap<>();
    payload.put("citations", citations);
    KnowledgeChatMessageEntity assistantMessage = KnowledgeChatMessageEntity.builder()
        .id(IdsUtil.newId())
        .sessionId(sessionId)
        .roleKey(KnowledgeChatRole.ASSISTANT.getValue())
        .content(answerText)
        .payloadJson(JSONUtil.toJsonStr(payload))
        .createdAt(LocalDateTime.now())
        .build();
    messageMapper.insert(assistantMessage);
    session.setUpdatedAt(LocalDateTime.now());
    sessionMapper.updateById(session);
    KnowledgeChatMessageView messageView = toMessageView(assistantMessage, citations);
    log.info("知识库问答完成 sessionId={} citations={}", sessionId, citations.size());
    return KnowledgeChatAnswerView.builder()
        .message(messageView)
        .citations(citations)
        .build();
  }

  /**
   * 构建系统提示词。
   *
   * @param citations 引用列表
   * @return 系统提示
   */
  private static String buildSystemPrompt(List<KnowledgeCitationView> citations) {
    StringBuilder builder = new StringBuilder();
    builder.append("你是成文坊知识库助手。只能根据用户提供的「参考资料摘录」回答问题；");
    builder.append("回答时注明资料标题；若摘录不足以回答，请明确说「根据现有资料无法确定」。");
    builder.append("禁止编造未出现在摘录中的事实。\n\n参考资料摘录：\n");
    if (CollUtil.isEmpty(citations)) {
      builder.append("（暂无相关摘录）");
    } else {
      int index = 1;
      for (KnowledgeCitationView citation : citations) {
        builder.append(index++).append(". 《")
            .append(StrUtil.blankToDefault(citation.getTitle(), "未命名"))
            .append("》");
        if (StrUtil.isNotBlank(citation.getHeadingPath())) {
          builder.append(" / ").append(citation.getHeadingPath());
        }
        builder.append("\n").append(StrUtil.blankToDefault(citation.getSnippet(), "")).append("\n\n");
      }
    }
    return builder.toString();
  }

  /**
   * 构建用户提示（仅问题正文，摘录已在 system）。
   *
   * @param question  问题
   * @param citations 引用（未用，保留扩展）
   * @return 用户提示
   */
  private static String buildUserPrompt(String question, List<KnowledgeCitationView> citations) {
    return question;
  }

  /**
   * 实体转消息视图。
   *
   * @param entity     消息实体
   * @param citations  引用
   * @return 视图
   */
  static KnowledgeChatMessageView toMessageView(
      KnowledgeChatMessageEntity entity, List<KnowledgeCitationView> citations) {
    return KnowledgeChatMessageView.builder()
        .id(entity.getId())
        .role(KnowledgeChatRole.from(entity.getRoleKey()))
        .content(entity.getContent())
        .citations(citations)
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 从 payload 解析引用列表。
   *
   * @param payloadJson JSON
   * @return 引用列表
   */
  static List<KnowledgeCitationView> parseCitations(String payloadJson) {
    if (StrUtil.isBlank(payloadJson)) {
      return List.of();
    }
    Map<?, ?> map = JSONUtil.toBean(payloadJson, Map.class);
    Object raw = map.get("citations");
    if (raw == null) {
      return List.of();
    }
    return JSONUtil.toList(JSONUtil.parseArray(JSONUtil.toJsonStr(raw)), KnowledgeCitationView.class);
  }

  /**
   * 列出会话消息。
   *
   * @param sessionId 会话 ID
   * @return 消息视图列表
   */
  public List<KnowledgeChatMessageView> listMessages(String sessionId) {
    List<KnowledgeChatMessageEntity> rows = messageMapper.selectList(
        new LambdaQueryWrapper<KnowledgeChatMessageEntity>()
            .eq(KnowledgeChatMessageEntity::getSessionId, sessionId)
            .orderByAsc(KnowledgeChatMessageEntity::getCreatedAt));
    return rows.stream()
        .map(row -> {
          List<KnowledgeCitationView> citations = KnowledgeChatRole.ASSISTANT.matches(row.getRoleKey())
              ? parseCitations(row.getPayloadJson())
              : List.of();
          return toMessageView(row, citations);
        })
        .toList();
  }
}
