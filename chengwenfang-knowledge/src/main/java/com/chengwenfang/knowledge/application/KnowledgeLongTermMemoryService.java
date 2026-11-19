package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.facade.AiEmbeddingFacade;
import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.VisionCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.domain.entity.KnowledgeMemoryEntity;
import com.chengwenfang.knowledge.facade.constant.KnowledgeMemoryProvenance;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeMemoryView;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeMemoryMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 长期记忆：显式写入 + 向量召回（Mem0 思路自研，无第三方 Memory SaaS）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeLongTermMemoryService {

  /** 默认作用域（用户体系落地前暂用）。 */
  public static final String DEFAULT_SCOPE = "default";

  /** Mapper。 */
  private final KnowledgeMemoryMapper memoryMapper;
  /** 嵌入。 */
  private final AiEmbeddingFacade embeddingFacade;
  /** 配置。 */
  private final ChengwenfangProperties properties;
  /** 短 LLM（晋升抽取）。 */
  private final AiVisionFacade aiVisionFacade;

  /**
   * 按向量相似度回忆未过期、未暂停记忆；无向量时回退 LIKE。
   *
   * @param query 查询
   * @param limit 上限
   * @return 记忆正文列表
   */
  public List<String> recall(String query, int limit) {
    if (StrUtil.isBlank(query) || limit <= 0) {
      return List.of();
    }
    LocalDateTime now = LocalDateTime.now();
    List<KnowledgeMemoryEntity> rows = memoryMapper.selectList(
        new LambdaQueryWrapper<KnowledgeMemoryEntity>()
            .eq(KnowledgeMemoryEntity::getScopeKey, DEFAULT_SCOPE)
            .and(wrapper -> wrapper
                .isNull(KnowledgeMemoryEntity::getPaused)
                .or()
                .eq(KnowledgeMemoryEntity::getPaused, false))
            .and(wrapper -> wrapper
                .isNull(KnowledgeMemoryEntity::getExpiresAt)
                .or()
                .gt(KnowledgeMemoryEntity::getExpiresAt, now))
            .orderByDesc(KnowledgeMemoryEntity::getCreatedAt)
            .last("LIMIT 200"));
    if (CollUtil.isEmpty(rows)) {
      return List.of();
    }
    float[] queryVector = embedOne(query);
    if (queryVector == null || queryVector.length == 0) {
      return fallbackLike(rows, query, limit);
    }
    List<ScoredMemory> scored = new ArrayList<>();
    for (KnowledgeMemoryEntity row : rows) {
      float[] vector = parseEmbedding(row.getEmbeddingJson());
      if (vector == null || vector.length != queryVector.length) {
        continue;
      }
      scored.add(new ScoredMemory(row.getContent(), cosine(queryVector, vector)));
    }
    if (scored.isEmpty()) {
      return fallbackLike(rows, query, limit);
    }
    scored.sort(Comparator.comparingDouble(ScoredMemory::score).reversed());
    return scored.stream()
        .limit(Math.min(limit, 20))
        .map(ScoredMemory::content)
        .filter(StrUtil::isNotBlank)
        .collect(Collectors.toList());
  }

  /**
   * 写入一条长期记忆并嵌入。
   *
   * @param content     正文
   * @param provenance  出处
   * @param ttlDays     TTL 天数；null 表示不过期
   */
  @Transactional
  public void remember(String content, String provenance, Integer ttlDays) {
    if (StrUtil.isBlank(content)) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime expires = ttlDays == null || ttlDays <= 0 ? null : now.plusDays(ttlDays);
    String trimmed = content.trim();
    float[] vector = embedOne(trimmed);
    KnowledgeMemoryEntity nearDuplicate = findNearDuplicate(vector);
    if (nearDuplicate != null) {
      // Mem0 语义：近重复则 UPDATE 正文与向量
      nearDuplicate.setContent(trimmed);
      nearDuplicate.setEmbeddingJson(vector == null ? null : JSONUtil.toJsonStr(vector));
      nearDuplicate.setProvenance(StrUtil.blankToDefault(provenance, nearDuplicate.getProvenance()));
      nearDuplicate.setExpiresAt(expires);
      nearDuplicate.setPaused(false);
      memoryMapper.updateById(nearDuplicate);
      return;
    }
    memoryMapper.insert(KnowledgeMemoryEntity.builder()
        .id(IdsUtil.newId())
        .scopeKey(DEFAULT_SCOPE)
        .content(trimmed)
        .embeddingJson(vector == null ? null : JSONUtil.toJsonStr(vector))
        .provenance(provenance)
        .paused(false)
        .expiresAt(expires)
        .createdAt(now)
        .build());
  }

  /**
   * 从会话摘要晋升 0–2 条稳定事实到长期记忆。
   *
   * @param summary        摘要
   * @param assistantTurns 本会话助手轮次数（用于 every N）
   */
  @Transactional
  public void maybePromoteFromSummary(String summary, int assistantTurns) {
    if (StrUtil.isBlank(summary)) {
      return;
    }
    var memorySettings = properties.getKnowledge() == null
        ? null
        : properties.getKnowledge().getMemory();
    if (memorySettings == null || !memorySettings.isPromoteEnabled()) {
      return;
    }
    int every = Math.max(1, memorySettings.getPromoteEveryNTurns());
    if (assistantTurns <= 0 || assistantTurns % every != 0) {
      return;
    }
    try {
      VisionCompleteResult result = aiVisionFacade.complete(VisionCompleteRequest.builder()
          .systemPrompt(
              "从会话摘要中抽出 0 到 2 条稳定用户偏好/事实。只输出 JSON 数组，"
                  + "元素为字符串；无则 []。不要解释。")
          .prompt(StrUtil.maxLength(summary, 1500))
          .scene(ModelCallScenes.KNOWLEDGE_RAG_CHAT)
          .build());
      String text = result == null ? "" : StrUtil.nullToEmpty(result.getText()).trim();
      int start = text.indexOf('[');
      int end = text.lastIndexOf(']');
      if (start < 0 || end <= start) {
        return;
      }
      List<String> facts = JSONUtil.toList(text.substring(start, end + 1), String.class);
      if (CollUtil.isEmpty(facts)) {
        return;
      }
      for (String fact : facts.stream().limit(2).toList()) {
        if (StrUtil.isNotBlank(fact)) {
          remember(
              fact.trim(),
              KnowledgeMemoryProvenance.SUMMARY_PROMOTE,
              KnowledgeRagConstants.MEMORY_DEFAULT_TTL_DAYS);
        }
      }
    } catch (Exception exception) {
      log.warn("摘要晋升记忆失败：{}", exception.getMessage());
    }
  }

  /**
   * 查找近重复记忆。
   *
   * @param vector 新向量
   * @return 近重复行
   */
  private KnowledgeMemoryEntity findNearDuplicate(float[] vector) {
    if (vector == null || vector.length == 0) {
      return null;
    }
    double threshold = 0.88;
    if (properties.getKnowledge() != null
        && properties.getKnowledge().getMemory() != null) {
      threshold = properties.getKnowledge().getMemory().getMergeThreshold();
    }
    List<KnowledgeMemoryEntity> rows = memoryMapper.selectList(
        new LambdaQueryWrapper<KnowledgeMemoryEntity>()
            .eq(KnowledgeMemoryEntity::getScopeKey, DEFAULT_SCOPE)
            .and(wrapper -> wrapper
                .isNull(KnowledgeMemoryEntity::getPaused)
                .or()
                .eq(KnowledgeMemoryEntity::getPaused, false))
            .orderByDesc(KnowledgeMemoryEntity::getCreatedAt)
            .last("LIMIT 100"));
    KnowledgeMemoryEntity best = null;
    double bestScore = -1;
    for (KnowledgeMemoryEntity row : rows) {
      float[] existing = parseEmbedding(row.getEmbeddingJson());
      if (existing == null || existing.length != vector.length) {
        continue;
      }
      double score = cosine(vector, existing);
      if (score >= threshold && score > bestScore) {
        bestScore = score;
        best = row;
      }
    }
    return best;
  }

  /**
   * 列出记忆（管理 UI）。
   *
   * @param limit 上限
   * @return 视图
   */
  public List<KnowledgeMemoryView> listMemories(int limit) {
    int cap = Math.min(Math.max(limit, 1), 200);
    return memoryMapper.selectList(new LambdaQueryWrapper<KnowledgeMemoryEntity>()
            .eq(KnowledgeMemoryEntity::getScopeKey, DEFAULT_SCOPE)
            .orderByDesc(KnowledgeMemoryEntity::getCreatedAt)
            .last("LIMIT " + cap))
        .stream()
        .map(this::toView)
        .toList();
  }

  /**
   * 删除记忆。
   *
   * @param memoryId 记忆 ID
   */
  @Transactional
  public void deleteMemory(String memoryId) {
    if (StrUtil.isBlank(memoryId)) {
      return;
    }
    memoryMapper.deleteById(memoryId);
  }

  /**
   * 暂停或恢复召回。
   *
   * @param memoryId 记忆 ID
   * @param paused   是否暂停
   */
  @Transactional
  public void setPaused(String memoryId, boolean paused) {
    if (StrUtil.isBlank(memoryId)) {
      return;
    }
    KnowledgeMemoryEntity row = memoryMapper.selectById(memoryId);
    if (row == null) {
      return;
    }
    row.setPaused(paused);
    memoryMapper.updateById(row);
  }

  /**
   * 嵌入单段文本。
   *
   * @param text 文本
   * @return 向量
   */
  private float[] embedOne(String text) {
    try {
      List<float[]> vectors = embeddingFacade.embed(List.of(text));
      if (CollUtil.isEmpty(vectors) || vectors.get(0) == null) {
        return null;
      }
      return vectors.get(0);
    } catch (Exception exception) {
      log.warn("记忆嵌入失败：{}", exception.getMessage());
      return null;
    }
  }

  /**
   * 解析嵌入 JSON。
   *
   * @param json JSON
   * @return 向量
   */
  private static float[] parseEmbedding(String json) {
    if (StrUtil.isBlank(json)) {
      return null;
    }
    try {
      List<Float> values = JSONUtil.toList(JSONUtil.parseArray(json), Float.class);
      if (CollUtil.isEmpty(values)) {
        return null;
      }
      float[] vector = new float[values.size()];
      for (int index = 0; index < values.size(); index++) {
        vector[index] = values.get(index) == null ? 0f : values.get(index);
      }
      return vector;
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 余弦相似度。
   *
   * @param left  左
   * @param right 右
   * @return 分数
   */
  static double cosine(float[] left, float[] right) {
    double dot = 0;
    double leftNorm = 0;
    double rightNorm = 0;
    for (int index = 0; index < left.length; index++) {
      dot += left[index] * right[index];
      leftNorm += left[index] * left[index];
      rightNorm += right[index] * right[index];
    }
    if (leftNorm == 0 || rightNorm == 0) {
      return 0;
    }
    return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
  }

  /**
   * LIKE 回退。
   *
   * @param rows  候选
   * @param query 查询
   * @param limit 上限
   * @return 正文
   */
  private static List<String> fallbackLike(List<KnowledgeMemoryEntity> rows, String query, int limit) {
    String keyword = query.trim();
    return rows.stream()
        .map(KnowledgeMemoryEntity::getContent)
        .filter(StrUtil::isNotBlank)
        .filter(content -> content.contains(keyword))
        .limit(Math.min(limit, 20))
        .collect(Collectors.toList());
  }

  /**
   * 实体转视图。
   *
   * @param entity 实体
   * @return 视图
   */
  private KnowledgeMemoryView toView(KnowledgeMemoryEntity entity) {
    return KnowledgeMemoryView.builder()
        .id(entity.getId())
        .content(entity.getContent())
        .provenance(entity.getProvenance())
        .paused(Boolean.TRUE.equals(entity.getPaused()))
        .expiresAt(entity.getExpiresAt())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * 打分记忆。
   *
   * @param content 正文
   * @param score   分
   */
  private record ScoredMemory(String content, double score) {}
}
