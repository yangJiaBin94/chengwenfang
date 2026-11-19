package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiEmbeddingFacade;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.rule.KnowledgeCitationTitles;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorFilter;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorHit;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 混合检索：向量 + FULLTEXT/LIKE，检索侧过滤后 RRF 融合。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetriever {

  /** RRF 常数 k。 */
  private static final int RRF_K = 60;
  /** 单路检索上限。 */
  private static final int CHANNEL_LIMIT = 20;

  /** 嵌入门面。 */
  private final AiEmbeddingFacade embeddingFacade;
  /** 向量库。 */
  private final KnowledgeVectorStore vectorStore;
  /** 切块 Mapper。 */
  private final KnowledgeChunkMapper chunkMapper;
  /** 文档 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;
  /** 活跃索引版本。 */
  private final KnowledgeIndexVersionService indexVersionService;

  /**
   * 检索引用片段。
   *
   * @param query       用户问题
   * @param topK        返回条数
   * @param sourceKinds 来源过滤；空表示不限
   * @param mediaKinds  介质过滤；空表示不限
   * @return 引用列表
   */
  public List<KnowledgeCitationView> retrieve(
      String query,
      int topK,
      List<KnowledgeSourceKind> sourceKinds,
      List<KnowledgeMediaKind> mediaKinds) {
    if (StrUtil.isBlank(query)) {
      return List.of();
    }
    String trimmedQuery = query.trim();
    String activeVersion = indexVersionService.activeVersion();
    String embeddingModel = embeddingFacade.embeddingModelName();
    List<String> sourceKindValues = toSourceKindValues(sourceKinds);
    List<String> mediaKindValues = toMediaKindValues(mediaKinds);
    KnowledgeVectorFilter vectorFilter = KnowledgeVectorFilter.builder()
        .indexVersion(activeVersion)
        .embeddingModel(StrUtil.blankToDefault(embeddingModel, null))
        .sourceKinds(sourceKindValues.isEmpty() ? null : sourceKindValues)
        .mediaKinds(mediaKindValues.isEmpty() ? null : mediaKindValues)
        .build();
    float[] queryVector = embeddingFacade.embedOne(trimmedQuery);
    List<KnowledgeVectorHit> vectorHits =
        vectorStore.search(queryVector, CHANNEL_LIMIT, vectorFilter);
    List<KnowledgeChunkEntity> keywordHits = loadKeywordHits(
        trimmedQuery,
        activeVersion,
        embeddingModel,
        joinCsv(sourceKindValues),
        joinCsv(mediaKindValues));
    Map<String, Double> scores = new HashMap<>();
    for (int rank = 0; rank < vectorHits.size(); rank++) {
      String chunkId = vectorHits.get(rank).getChunkId();
      scores.merge(chunkId, 1.0 / (RRF_K + rank + 1), Double::sum);
    }
    for (int rank = 0; rank < keywordHits.size(); rank++) {
      String chunkId = keywordHits.get(rank).getId();
      scores.merge(chunkId, 1.0 / (RRF_K + rank + 1), Double::sum);
    }
    if (scores.isEmpty()) {
      return List.of();
    }
    List<String> orderedChunkIds = scores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .map(Map.Entry::getKey)
        .toList();
    Map<String, KnowledgeChunkEntity> chunkById = loadChunks(orderedChunkIds);
    List<String> parentIds = chunkById.values().stream()
        .map(KnowledgeChunkEntity::getParentChunkId)
        .filter(StrUtil::isNotBlank)
        .distinct()
        .toList();
    if (CollUtil.isNotEmpty(parentIds)) {
      chunkById.putAll(loadChunks(parentIds));
    }
    Map<String, KnowledgeDocumentEntity> documentById = loadDocuments(chunkById.values());
    List<KnowledgeCitationView> citations = new ArrayList<>();
    for (String chunkId : orderedChunkIds) {
      KnowledgeChunkEntity chunk = chunkById.get(chunkId);
      if (chunk == null) {
        continue;
      }
      if (!activeVersion.equals(chunk.getIndexVersion())) {
        continue;
      }
      KnowledgeDocumentEntity document = documentById.get(chunk.getDocumentId());
      if (document == null) {
        continue;
      }
      // 仅同章节路径父块可进窗；错绑邻块/插图父块时保留命中块正文
      KnowledgeChunkEntity windowChunk = resolveWindowChunk(chunk, chunkById);
      Double score = scores.get(chunkId);
      citations.add(toCitation(document, chunk, windowChunk, score));
      if (citations.size() >= topK) {
        break;
      }
    }
    return citations;
  }

  /**
   * FULLTEXT 检索，空结果时 LIKE 回退（版本与来源在 SQL 侧过滤）。
   *
   * @param query           查询词
   * @param indexVersion    版本
   * @param embeddingModel  嵌入模型
   * @param sourceKindsCsv  来源 CSV；空不限
   * @param mediaKindsCsv   介质 CSV；空不限
   * @return 切块列表
   */
  private List<KnowledgeChunkEntity> loadKeywordHits(
      String query,
      String indexVersion,
      String embeddingModel,
      String sourceKindsCsv,
      String mediaKindsCsv) {
    String sources = StrUtil.nullToEmpty(sourceKindsCsv);
    String medias = StrUtil.nullToEmpty(mediaKindsCsv);
    List<KnowledgeChunkEntity> hits;
    try {
      hits = chunkMapper.searchFullText(query, indexVersion, sources, medias, CHANNEL_LIMIT);
    } catch (Exception exception) {
      log.warn("知识库 FULLTEXT 失败，回退 LIKE：{}", exception.getMessage());
      hits = List.of();
    }
    if (CollUtil.isEmpty(hits)) {
      hits = chunkMapper.searchKeyword(query, indexVersion, sources, medias, CHANNEL_LIMIT);
    }
    if (hits == null) {
      return List.of();
    }
    if (StrUtil.isBlank(embeddingModel)) {
      return hits;
    }
    // 关键词路也按当前嵌入模型过滤，避免混模块进入 RRF
    return hits.stream()
        .filter(chunk -> embeddingModel.equals(chunk.getEmbeddingModel())
            || StrUtil.isBlank(chunk.getEmbeddingModel()))
        .toList();
  }

  /**
   * 种类值列表拼 CSV（空 → 空串）。
   *
   * @param values 值
   * @return CSV
   */
  private static String joinCsv(List<String> values) {
    if (CollUtil.isEmpty(values)) {
      return "";
    }
    return String.join(",", values);
  }

  /**
   * 来源枚举转字符串列表。
   *
   * @param sourceKinds 来源
   * @return 值列表
   */
  private static List<String> toSourceKindValues(List<KnowledgeSourceKind> sourceKinds) {
    if (CollUtil.isEmpty(sourceKinds)) {
      return List.of();
    }
    return sourceKinds.stream()
        .filter(kind -> kind != null)
        .map(KnowledgeSourceKind::getValue)
        .filter(StrUtil::isNotBlank)
        .distinct()
        .toList();
  }

  /**
   * 介质枚举转字符串列表。
   *
   * @param mediaKinds 介质
   * @return 值列表
   */
  private static List<String> toMediaKindValues(List<KnowledgeMediaKind> mediaKinds) {
    if (CollUtil.isEmpty(mediaKinds)) {
      return List.of();
    }
    return mediaKinds.stream()
        .filter(kind -> kind != null)
        .map(KnowledgeMediaKind::getValue)
        .filter(StrUtil::isNotBlank)
        .distinct()
        .toList();
  }

  /**
   * 仅保留 active 版本且仍存在于 MySQL 的引用（图检索结果回表校验）。
   *
   * @param citations 原始引用
   * @return 过滤后引用
   */
  public List<KnowledgeCitationView> retainActiveCitations(List<KnowledgeCitationView> citations) {
    if (CollUtil.isEmpty(citations)) {
      return List.of();
    }
    String activeVersion = indexVersionService.activeVersion();
    List<String> chunkIds = citations.stream()
        .filter(citation -> citation != null && StrUtil.isNotBlank(citation.getChunkId()))
        .map(KnowledgeCitationView::getChunkId)
        .distinct()
        .toList();
    Map<String, KnowledgeChunkEntity> chunkById = loadChunks(chunkIds);
    List<KnowledgeCitationView> retained = new ArrayList<>();
    for (KnowledgeCitationView citation : citations) {
      if (citation == null || StrUtil.isBlank(citation.getChunkId())) {
        continue;
      }
      KnowledgeChunkEntity chunk = chunkById.get(citation.getChunkId());
      if (chunk == null || !activeVersion.equals(chunk.getIndexVersion())) {
        continue;
      }
      retained.add(citation);
    }
    return retained;
  }

  /**
   * 批量加载切块。
   *
   * @param chunkIds 主键列表
   * @return id → 实体
   */
  private Map<String, KnowledgeChunkEntity> loadChunks(List<String> chunkIds) {
    if (CollUtil.isEmpty(chunkIds)) {
      return Map.of();
    }
    List<KnowledgeChunkEntity> rows = chunkMapper.selectBatchIds(chunkIds);
    Map<String, KnowledgeChunkEntity> map = new LinkedHashMap<>();
    for (KnowledgeChunkEntity row : rows) {
      map.put(row.getId(), row);
    }
    return map;
  }

  /**
   * 批量加载文档。
   *
   * @param chunks 切块集合
   * @return documentId → 实体
   */
  private Map<String, KnowledgeDocumentEntity> loadDocuments(Iterable<KnowledgeChunkEntity> chunks) {
    Set<String> documentIds = new java.util.HashSet<>();
    for (KnowledgeChunkEntity chunk : chunks) {
      if (chunk != null && StrUtil.isNotBlank(chunk.getDocumentId())) {
        documentIds.add(chunk.getDocumentId());
      }
    }
    if (documentIds.isEmpty()) {
      return Map.of();
    }
    List<KnowledgeDocumentEntity> rows = documentMapper.selectBatchIds(documentIds);
    Map<String, KnowledgeDocumentEntity> map = new HashMap<>();
    for (KnowledgeDocumentEntity row : rows) {
      map.put(row.getId(), row);
    }
    return map;
  }

  /**
   * 选择进窗正文：父块须与命中块同章节路径，且不能是插图类低价值块。
   *
   * @param hitChunk  命中块
   * @param chunkById 已加载切块
   * @return 进窗块
   */
  static KnowledgeChunkEntity resolveWindowChunk(
      KnowledgeChunkEntity hitChunk, Map<String, KnowledgeChunkEntity> chunkById) {
    if (hitChunk == null || StrUtil.isBlank(hitChunk.getParentChunkId()) || chunkById == null) {
      return hitChunk;
    }
    KnowledgeChunkEntity parent = chunkById.get(hitChunk.getParentChunkId());
    if (parent == null) {
      return hitChunk;
    }
    if (!sameHeadingPath(hitChunk.getHeadingPath(), parent.getHeadingPath())) {
      return hitChunk;
    }
    if (RagChatService.isLowValueSnippet(parent.getContent())) {
      return hitChunk;
    }
    return parent;
  }

  /**
   * 是否同章节路径（仅完全相同才共用父窗，避免跨节错绑）。
   *
   * @param left  路径 A
   * @param right 路径 B
   * @return 相同
   */
  static boolean sameHeadingPath(String left, String right) {
    String normalizedLeft = StrUtil.nullToEmpty(left).trim();
    String normalizedRight = StrUtil.nullToEmpty(right).trim();
    if (StrUtil.isBlank(normalizedLeft) || StrUtil.isBlank(normalizedRight)) {
      return false;
    }
    return normalizedLeft.equals(normalizedRight);
  }

  /**
   * 组装引用视图。
   *
   * @param document    文档
   * @param hitChunk    命中子块（保留 chunkId / span）
   * @param windowChunk 进窗正文块（可为父块）
   * @param score       融合分
   * @return 引用视图
   */
  private static KnowledgeCitationView toCitation(
      KnowledgeDocumentEntity document,
      KnowledgeChunkEntity hitChunk,
      KnowledgeChunkEntity windowChunk,
      Double score) {
    String snippet = windowChunk.getContent();
    if (snippet != null && snippet.length() > 400) {
      snippet = snippet.substring(0, 400) + "…";
    }
    return KnowledgeCitationView.builder()
        .documentId(document.getId())
        .chunkId(hitChunk.getId())
        .sourceKind(KnowledgeSourceKind.from(document.getSourceKind()))
        .mediaKind(KnowledgeMediaKind.from(document.getMediaKind()))
        .title(KnowledgeCitationTitles.resolve(document.getTitle(), hitChunk.getHeadingPath()))
        .snippet(snippet)
        .deepLink(enrichDeepLink(
            document.getDeepLink(),
            hitChunk.getHeadingPath(),
            hitChunk.getCharSpanStart()))
        .headingPath(hitChunk.getHeadingPath())
        .charSpanStart(hitChunk.getCharSpanStart())
        .charSpanEnd(hitChunk.getCharSpanEnd())
        .score(score)
        .build();
  }

  /**
   * 深链附加章节与偏移，便于打开原文定位。
   *
   * @param deepLink        原深链
   * @param headingPath     标题路径
   * @param charSpanStart   起始偏移
   * @return 深链
   */
  static String enrichDeepLink(String deepLink, String headingPath, Integer charSpanStart) {
    String base = StrUtil.nullToEmpty(deepLink).trim();
    if (StrUtil.isBlank(base)) {
      return base;
    }
    StringBuilder query = new StringBuilder();
    if (StrUtil.isNotBlank(headingPath)) {
      query.append("citeHeading=")
          .append(java.net.URLEncoder.encode(headingPath.trim(), java.nio.charset.StandardCharsets.UTF_8));
    }
    if (charSpanStart != null && charSpanStart >= 0) {
      if (!query.isEmpty()) {
        query.append('&');
      }
      query.append("citeStart=").append(charSpanStart);
    }
    if (query.isEmpty()) {
      return base;
    }
    return base.contains("?") ? base + "&" + query : base + "?" + query;
  }
}
