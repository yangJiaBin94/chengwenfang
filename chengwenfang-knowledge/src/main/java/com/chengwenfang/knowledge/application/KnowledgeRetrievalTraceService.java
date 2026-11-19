package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.knowledge.domain.entity.KnowledgeRetrievalTraceEntity;
import com.chengwenfang.knowledge.facade.dto.KnowledgeRetrievalTracePayload;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeRetrievalTraceView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeToolStepView;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeRetrievalTraceMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 按 session 落检索观测轨迹。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalTraceService {

  /** Mapper。 */
  private final KnowledgeRetrievalTraceMapper traceMapper;

  /**
   * 写入一条轨迹。
   *
   * @param sessionId     会话
   * @param roundNo       轮次
   * @param modeKey       模式
   * @param queryText     查询
   * @param chunkIds      命中块
   * @param reranked      是否 rerank
   * @param abstainReason 拒答原因
   * @param latencyMs     耗时
   * @param extra         附加字段（question/tools 等）
   */
  public void record(
      String sessionId,
      int roundNo,
      String modeKey,
      String queryText,
      List<String> chunkIds,
      boolean reranked,
      String abstainReason,
      int latencyMs,
      KnowledgeRetrievalTracePayload extra) {
    KnowledgeRetrievalTracePayload payload = KnowledgeRetrievalTracePayload.builder()
        .chunkIds(chunkIds)
        .reranked(reranked)
        .abstainReason(abstainReason)
        .question(extra == null ? null : extra.getQuestion())
        .degradedReasons(extra == null ? null : extra.getDegradedReasons())
        .tools(extra == null ? null : extra.getTools())
        .build();
    KnowledgeRetrievalTraceEntity row = KnowledgeRetrievalTraceEntity.builder()
        .id(IdsUtil.newId())
        .sessionId(sessionId)
        .roundNo(roundNo)
        .modeKey(modeKey)
        .queryText(queryText)
        .payloadJson(JSONUtil.toJsonStr(payload))
        .latencyMs(latencyMs)
        .createdAt(LocalDateTime.now())
        .build();
    traceMapper.insert(row);
  }

  /**
   * 删除会话下全部检索轨迹。
   *
   * @param sessionId 会话 ID
   */
  public void deleteBySession(String sessionId) {
    if (StrUtil.isBlank(sessionId)) {
      return;
    }
    traceMapper.delete(new LambdaQueryWrapper<KnowledgeRetrievalTraceEntity>()
        .eq(KnowledgeRetrievalTraceEntity::getSessionId, sessionId));
  }

  /**
   * 按会话列出检索轨迹（调试用）。
   *
   * @param sessionId 会话 ID
   * @param limit     上限
   * @return 轨迹视图
   */
  public List<KnowledgeRetrievalTraceView> listBySession(String sessionId, int limit) {
    int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
    List<KnowledgeRetrievalTraceEntity> rows = traceMapper.selectList(
        new LambdaQueryWrapper<KnowledgeRetrievalTraceEntity>()
            .eq(KnowledgeRetrievalTraceEntity::getSessionId, sessionId)
            .orderByDesc(KnowledgeRetrievalTraceEntity::getCreatedAt)
            .last("LIMIT " + safeLimit));
    return rows.stream().map(this::toView).toList();
  }

  /**
   * 实体转视图。
   *
   * @param entity 实体
   * @return 视图
   */
  private KnowledgeRetrievalTraceView toView(KnowledgeRetrievalTraceEntity entity) {
    List<KnowledgeToolStepView> tools = List.of();
    if (StrUtil.isNotBlank(entity.getPayloadJson())) {
      try {
        KnowledgeRetrievalTracePayload payload =
            JSONUtil.toBean(entity.getPayloadJson(), KnowledgeRetrievalTracePayload.class);
        if (payload != null && payload.getTools() != null) {
          tools = payload.getTools();
        }
      } catch (Exception ignored) {
        tools = List.of();
      }
    }
    return KnowledgeRetrievalTraceView.builder()
        .id(entity.getId())
        .sessionId(entity.getSessionId())
        .roundNo(entity.getRoundNo())
        .modeKey(entity.getModeKey())
        .queryText(entity.getQueryText())
        .latencyMs(entity.getLatencyMs())
        .toolSteps(tools)
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
