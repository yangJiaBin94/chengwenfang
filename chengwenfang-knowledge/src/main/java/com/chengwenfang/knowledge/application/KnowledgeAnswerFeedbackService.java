package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.knowledge.domain.entity.KnowledgeAnswerFeedbackEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatMessageEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatSessionEntity;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeAnswerFeedbackRequest;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeAnswerFeedbackView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChatRole;
import com.chengwenfang.knowledge.facade.enums.KnowledgeFeedbackRating;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeAnswerFeedbackMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatMessageMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatSessionMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 答案赞踩与难例候选导出。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeAnswerFeedbackService {

  /** 反馈 Mapper。 */
  private final KnowledgeAnswerFeedbackMapper feedbackMapper;
  /** 消息 Mapper。 */
  private final KnowledgeChatMessageMapper messageMapper;
  /** 会话 Mapper。 */
  private final KnowledgeChatSessionMapper sessionMapper;
  /** 用户数据范围。 */
  private final OwnerScope ownerScope;

  /**
   * 提交反馈。
   *
   * @param messageId 助手消息 ID
   * @param request   请求
   * @return 视图
   */
  @Transactional
  public KnowledgeAnswerFeedbackView submit(String messageId, KnowledgeAnswerFeedbackRequest request) {
    if (StrUtil.isBlank(messageId)) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "消息 ID 不能为空", 400);
    }
    KnowledgeFeedbackRating rating = KnowledgeFeedbackRating.from(
        request == null ? null : request.getRating());
    if (rating == null) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "rating 须为 up 或 down", 400);
    }
    KnowledgeChatMessageEntity message = messageMapper.selectById(messageId);
    if (message == null || !KnowledgeChatRole.ASSISTANT.matches(message.getRoleKey())) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "助手消息不存在", 404);
    }
    // 经会话归属校验，避免跨用户对他人回答点赞踩
    KnowledgeChatSessionEntity session = sessionMapper.selectById(message.getSessionId());
    if (session == null) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_SESSION_NOT_FOUND, "知识库会话不存在", 404);
    }
    ownerScope.assertCanAccess(session.getOwnerUserId());
    KnowledgeAnswerFeedbackEntity row = KnowledgeAnswerFeedbackEntity.builder()
        .id(IdsUtil.newId())
        .ownerUserId(ownerScope.requireOwnerUserIdForWrite().get())
        .messageId(messageId)
        .sessionId(message.getSessionId())
        .rating(rating.getValue())
        .note(request == null ? null : StrUtil.maxLength(StrUtil.nullToEmpty(request.getNote()), 1000))
        .createdAt(LocalDateTime.now())
        .build();
    feedbackMapper.insert(row);
    return toView(row, message);
  }

  /**
   * 组装视图与难例 JSON。
   *
   * @param row     反馈
   * @param message 消息
   * @return 视图
   */
  private KnowledgeAnswerFeedbackView toView(
      KnowledgeAnswerFeedbackEntity row, KnowledgeChatMessageEntity message) {
    String goldenJson = null;
    if (KnowledgeFeedbackRating.DOWN.matches(row.getRating())) {
      Map<String, Object> candidate = new LinkedHashMap<>();
      candidate.put("id", "hard-" + row.getId());
      candidate.put("question", findPrecedingUserQuestion(message));
      candidate.put("expected", Map.of(
          "abstain", true,
          "note", StrUtil.nullToEmpty(row.getNote())));
      candidate.put("answerPreview", StrUtil.maxLength(StrUtil.nullToEmpty(message.getContent()), 200));
      candidate.put("messageId", message.getId());
      candidate.put("sessionId", message.getSessionId());
      goldenJson = JSONUtil.toJsonPrettyStr(candidate);
    }
    return KnowledgeAnswerFeedbackView.builder()
        .id(row.getId())
        .messageId(row.getMessageId())
        .sessionId(row.getSessionId())
        .rating(row.getRating())
        .note(row.getNote())
        .createdAt(row.getCreatedAt())
        .goldenCandidateJson(goldenJson)
        .build();
  }

  /**
   * 取该助手消息之前最近一条用户问题。
   *
   * @param assistantMessage 助手消息
   * @return 问题正文
   */
  private String findPrecedingUserQuestion(KnowledgeChatMessageEntity assistantMessage) {
    List<KnowledgeChatMessageEntity> prior = messageMapper.selectList(
        new LambdaQueryWrapper<KnowledgeChatMessageEntity>()
            .eq(KnowledgeChatMessageEntity::getSessionId, assistantMessage.getSessionId())
            .eq(KnowledgeChatMessageEntity::getRoleKey, KnowledgeChatRole.USER.getValue())
            .lt(KnowledgeChatMessageEntity::getCreatedAt, assistantMessage.getCreatedAt())
            .orderByDesc(KnowledgeChatMessageEntity::getCreatedAt)
            .last("LIMIT 1"));
    if (prior.isEmpty()) {
      return "";
    }
    return StrUtil.nullToEmpty(prior.get(0).getContent());
  }
}
