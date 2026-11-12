package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiEmbeddingFacade;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.model.KnowledgeVectorHit;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 混合检索：向量 + FULLTEXT/LIKE，RRF 融合后返回引用视图。 */
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

  /**
   * 检索引用片段。
   *
   * @param query         用户问题
   * @param topK          返回条数
   * @param sourceKinds   来源过滤；空表示不限
   * @param mediaKinds    介质过滤；空表示不限
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
    float[] queryVector = embeddingFacade.embedOne(trimmedQuery);
    List<KnowledgeVectorHit> vectorHits = vectorStore.search(queryVector, CHANNEL_LIMIT);
    List<KnowledgeChunkEntity> keywordHits = loadKeywordHits(trimmedQuery);
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
        .collect(Collectors.toList());
    Map<String, KnowledgeChunkEntity> chunkById = loadChunks(orderedChunkIds);
    Map<String, KnowledgeDocumentEntity> documentById = loadDocuments(chunkById.values());
    List<KnowledgeCitationView> citations = new ArrayList<>();
    for (String chunkId : orderedChunkIds) {
      KnowledgeChunkEntity chunk = chunkById.get(chunkId);
      if (chunk == null) {
        continue;
      }
      KnowledgeDocumentEntity document = documentById.get(chunk.getDocumentId());
      if (document == null) {
        continue;
      }
      if (!matchesSourceFilter(document, sourceKinds) || !matchesMediaFilter(document, mediaKinds)) {
        continue;
      }
      citations.add(toCitation(document, chunk));
      if (citations.size() >= topK) {
        break;
      }
    }
    return citations;
  }

  /**
   * FULLTEXT 检索，空结果时 LIKE 回退。
   *
   * @param query 查询词
   * @return 切块列表
   */
  private List<KnowledgeChunkEntity> loadKeywordHits(String query) {
    List<KnowledgeChunkEntity> hits;
    try {
      hits = chunkMapper.searchFullText(query, CHANNEL_LIMIT);
    } catch (Exception ignored) {
      hits = List.of();
    }
    if (CollUtil.isEmpty(hits)) {
      hits = chunkMapper.searchKeyword(query, CHANNEL_LIMIT);
    }
    return hits == null ? List.of() : hits;
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
   * 来源种类过滤。
   *
   * @param document    文档
   * @param sourceKinds 过滤集
   * @return 通过时 true
   */
  private static boolean matchesSourceFilter(
      KnowledgeDocumentEntity document, List<KnowledgeSourceKind> sourceKinds) {
    if (CollUtil.isEmpty(sourceKinds)) {
      return true;
    }
    for (KnowledgeSourceKind kind : sourceKinds) {
      if (kind != null && kind.matches(document.getSourceKind())) {
        return true;
      }
    }
    return false;
  }

  /**
   * 介质种类过滤。
   *
   * @param document   文档
   * @param mediaKinds 过滤集
   * @return 通过时 true
   */
  private static boolean matchesMediaFilter(
      KnowledgeDocumentEntity document, List<KnowledgeMediaKind> mediaKinds) {
    if (CollUtil.isEmpty(mediaKinds)) {
      return true;
    }
    for (KnowledgeMediaKind kind : mediaKinds) {
      if (kind != null && kind.matches(document.getMediaKind())) {
        return true;
      }
    }
    return false;
  }

  /**
   * 组装引用视图。
   *
   * @param document 文档
   * @param chunk    切块
   * @return 引用视图
   */
  private static KnowledgeCitationView toCitation(KnowledgeDocumentEntity document, KnowledgeChunkEntity chunk) {
    String snippet = chunk.getContent();
    if (snippet != null && snippet.length() > 400) {
      snippet = snippet.substring(0, 400) + "…";
    }
    return KnowledgeCitationView.builder()
        .documentId(document.getId())
        .sourceKind(KnowledgeSourceKind.from(document.getSourceKind()))
        .mediaKind(KnowledgeMediaKind.from(document.getMediaKind()))
        .title(document.getTitle())
        .snippet(snippet)
        .deepLink(document.getDeepLink())
        .headingPath(chunk.getHeadingPath())
        .build();
  }
}
