package com.chengwenfang.knowledge.infrastructure.vector;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.facade.constant.KnowledgeVectorPayloadKeys;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorFilter;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorHit;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorUpsert;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

/**
 * 基于 Qdrant + LangChain4j 的知识库向量存储实现。
 */
@Slf4j
public class QdrantKnowledgeVectorStore implements KnowledgeVectorStore, DisposableBean {

  /** Qdrant 集合名。 */
  private final String collectionName;
  /** gRPC 客户端（集合管理、健康检查）。 */
  private final QdrantClient qdrantClient;
  /** LangChain4j 嵌入存储封装。 */
  private final QdrantEmbeddingStore embeddingStore;

  /**
   * 按全局配置构造 Qdrant 客户端与嵌入存储。
   *
   * @param settings Qdrant 连接配置
   */
  public QdrantKnowledgeVectorStore(ChengwenfangProperties.QdrantSettings settings) {
    this.collectionName = settings.getCollection();
    QdrantGrpcClient.Builder grpcBuilder =
        QdrantGrpcClient.newBuilder(settings.getHost(), settings.getGrpcPort(), settings.isUseTls());
    if (StrUtil.isNotBlank(settings.getApiKey())) {
      grpcBuilder.withApiKey(settings.getApiKey().trim());
    }
    QdrantGrpcClient grpcClient = grpcBuilder.build();
    this.qdrantClient = new QdrantClient(grpcClient);
    this.embeddingStore = QdrantEmbeddingStore.builder()
        .client(qdrantClient)
        .collectionName(collectionName)
        .payloadTextKey(KnowledgeVectorPayloadKeys.CONTENT)
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public void upsert(List<KnowledgeVectorUpsert> items) {
    if (CollUtil.isEmpty(items)) {
      return;
    }
    List<String> ids = new ArrayList<>();
    List<Embedding> embeddings = new ArrayList<>();
    List<TextSegment> segments = new ArrayList<>();
    for (KnowledgeVectorUpsert item : items) {
      if (item == null || StrUtil.isBlank(item.getChunkId()) || item.getVector() == null) {
        continue;
      }
      ids.add(item.getChunkId());
      embeddings.add(Embedding.from(item.getVector()));
      segments.add(TextSegment.from(StrUtil.nullToEmpty(item.getContent()), buildMetadata(item)));
    }
    if (ids.isEmpty()) {
      return;
    }
    embeddingStore.addAll(ids, embeddings, segments);
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByDocumentId(String documentId) {
    deleteByDocumentId(documentId, null);
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByDocumentId(String documentId, String indexVersion) {
    if (StrUtil.isBlank(documentId)) {
      return;
    }
    try {
      Boolean exists =
          qdrantClient.collectionExistsAsync(collectionName).get(10, TimeUnit.SECONDS);
      if (!Boolean.TRUE.equals(exists)) {
        return;
      }
    } catch (Exception exception) {
      log.warn("检查 Qdrant 集合是否存在失败，跳过删除：{}", exception.getMessage());
      return;
    }
    Filter filter =
        MetadataFilterBuilder.metadataKey(KnowledgeVectorPayloadKeys.DOCUMENT_ID).isEqualTo(documentId);
    if (StrUtil.isNotBlank(indexVersion)) {
      filter = Filter.and(
          filter,
          MetadataFilterBuilder.metadataKey(KnowledgeVectorPayloadKeys.INDEX_VERSION)
              .isEqualTo(indexVersion));
    }
    try {
      embeddingStore.removeAll(filter);
    } catch (RuntimeException exception) {
      if (StrUtil.containsIgnoreCase(exception.getMessage(), "doesn't exist")
          || StrUtil.containsIgnoreCase(exception.getMessage(), "NOT_FOUND")) {
        log.info("Qdrant 集合不存在，跳过按文档删除 documentId={}", documentId);
        return;
      }
      throw exception;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByIndexVersionAndOwner(String indexVersion, String ownerUserId) {
    if (StrUtil.isBlank(indexVersion) || StrUtil.isBlank(ownerUserId)) {
      return;
    }
    try {
      Boolean exists =
          qdrantClient.collectionExistsAsync(collectionName).get(10, TimeUnit.SECONDS);
      if (!Boolean.TRUE.equals(exists)) {
        return;
      }
    } catch (Exception exception) {
      log.warn("检查 Qdrant 集合是否存在失败，跳过按版本删除：{}", exception.getMessage());
      return;
    }
    Filter filter = Filter.and(
        MetadataFilterBuilder.metadataKey(KnowledgeVectorPayloadKeys.INDEX_VERSION)
            .isEqualTo(indexVersion),
        MetadataFilterBuilder.metadataKey(KnowledgeVectorPayloadKeys.OWNER_USER_ID)
            .isEqualTo(ownerUserId));
    try {
      embeddingStore.removeAll(filter);
    } catch (RuntimeException exception) {
      if (StrUtil.containsIgnoreCase(exception.getMessage(), "doesn't exist")
          || StrUtil.containsIgnoreCase(exception.getMessage(), "NOT_FOUND")) {
        log.info(
            "Qdrant 集合不存在，跳过按版本删除 version={} owner={}", indexVersion, ownerUserId);
        return;
      }
      throw exception;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByChunkIds(List<String> chunkIds) {
    if (CollUtil.isEmpty(chunkIds)) {
      return;
    }
    List<String> ids = chunkIds.stream().filter(StrUtil::isNotBlank).distinct().toList();
    if (ids.isEmpty()) {
      return;
    }
    try {
      embeddingStore.removeAll(ids);
    } catch (RuntimeException exception) {
      if (StrUtil.containsIgnoreCase(exception.getMessage(), "doesn't exist")
          || StrUtil.containsIgnoreCase(exception.getMessage(), "NOT_FOUND")) {
        log.info("Qdrant 集合不存在，跳过按切块删除 count={}", ids.size());
        return;
      }
      throw exception;
    }
  }

  /** {@inheritDoc} */
  @Override
  public List<KnowledgeVectorHit> search(float[] queryVector, int limit, KnowledgeVectorFilter filter) {
    if (queryVector == null || queryVector.length == 0 || limit <= 0) {
      return List.of();
    }
    EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(Embedding.from(queryVector))
        .maxResults(limit)
        .filter(buildSearchFilter(filter))
        .build();
    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
    if (result == null || CollUtil.isEmpty(result.matches())) {
      return List.of();
    }
    List<KnowledgeVectorHit> hits = new ArrayList<>();
    for (EmbeddingMatch<TextSegment> match : result.matches()) {
      hits.add(toHit(match));
    }
    return hits;
  }

  /** {@inheritDoc} */
  @Override
  public void ensureCollection(int dimension) {
    if (dimension <= 0) {
      throw new IllegalArgumentException("向量维度必须大于 0");
    }
    try {
      Boolean exists =
          qdrantClient.collectionExistsAsync(collectionName).get(10, TimeUnit.SECONDS);
      if (Boolean.TRUE.equals(exists)) {
        return;
      }
      VectorParams vectorParams = VectorParams.newBuilder()
          .setSize(dimension)
          .setDistance(Distance.Cosine)
          .build();
      qdrantClient.createCollectionAsync(collectionName, vectorParams).get(30, TimeUnit.SECONDS);
      log.info("已创建 Qdrant 集合 {}，维度={}，距离=Cosine", collectionName, dimension);
    } catch (Exception exception) {
      throw new IllegalStateException("创建或检查 Qdrant 集合失败：" + exception.getMessage(), exception);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean degraded() {
    return !healthy();
  }

  /**
   * Qdrant 健康检查。
   *
   * @return true 表示健康
   */
  private boolean healthy() {
    try {
      qdrantClient.healthCheckAsync(Duration.ofSeconds(3)).get(5, TimeUnit.SECONDS);
      return true;
    } catch (Exception exception) {
      log.warn("Qdrant 健康检查失败：{}", exception.getMessage());
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void destroy() {
    try {
      embeddingStore.close();
    } catch (Exception ignored) {
      // 关闭阶段忽略次要异常
    }
    try {
      qdrantClient.close();
    } catch (Exception ignored) {
      // 关闭阶段忽略次要异常
    }
  }

  /**
   * 将 upsert 载荷转为 LangChain4j 元数据。
   *
   * @param item 向量记录
   * @return 元数据
   */
  private static Metadata buildMetadata(KnowledgeVectorUpsert item) {
    Metadata metadata = new Metadata();
    putString(metadata, KnowledgeVectorPayloadKeys.DOCUMENT_ID, item.getDocumentId());
    putString(metadata, KnowledgeVectorPayloadKeys.OWNER_USER_ID, item.getOwnerUserId());
    putString(metadata, KnowledgeVectorPayloadKeys.SOURCE_KIND, item.getSourceKind());
    putString(metadata, KnowledgeVectorPayloadKeys.SOURCE_REF_ID, item.getSourceRefId());
    putString(metadata, KnowledgeVectorPayloadKeys.MEDIA_KIND, item.getMediaKind());
    putString(metadata, KnowledgeVectorPayloadKeys.TITLE, item.getTitle());
    putString(metadata, KnowledgeVectorPayloadKeys.HEADING_PATH, item.getHeadingPath());
    if (item.getChunkIndex() != null) {
      metadata.put(KnowledgeVectorPayloadKeys.CHUNK_INDEX, item.getChunkIndex());
    }
    putString(metadata, KnowledgeVectorPayloadKeys.CONTENT_HASH, item.getContentHash());
    putString(metadata, KnowledgeVectorPayloadKeys.THEME_ID, item.getThemeId());
    putString(metadata, KnowledgeVectorPayloadKeys.DEEP_LINK, item.getDeepLink());
    putString(metadata, KnowledgeVectorPayloadKeys.INDEX_VERSION, item.getIndexVersion());
    putString(metadata, KnowledgeVectorPayloadKeys.EMBEDDING_MODEL, item.getEmbeddingModel());
    return metadata;
  }

  /**
   * 非空字符串写入元数据。
   *
   * @param metadata 目标元数据
   * @param key      键
   * @param value    值
   */
  private static void putString(Metadata metadata, String key, String value) {
    if (StrUtil.isNotBlank(value)) {
      metadata.put(key, value);
    }
  }

  /**
   * 将可选业务过滤转为 LangChain4j Filter。
   *
   * @param filter 业务过滤
   * @return LangChain4j 过滤器；无条件时 null
   */
  private static Filter buildSearchFilter(KnowledgeVectorFilter filter) {
    if (filter == null) {
      return null;
    }
    Filter combined = null;
    combined = mergeEqualFilter(combined, KnowledgeVectorPayloadKeys.DOCUMENT_ID, filter.getDocumentId());
    combined = mergeEqualFilter(
        combined, KnowledgeVectorPayloadKeys.OWNER_USER_ID, filter.getOwnerUserId());
    combined = mergeAnyEqualFilter(
        combined,
        KnowledgeVectorPayloadKeys.SOURCE_KIND,
        resolveKindValues(filter.getSourceKinds(), filter.getSourceKind()));
    combined = mergeAnyEqualFilter(
        combined,
        KnowledgeVectorPayloadKeys.MEDIA_KIND,
        resolveKindValues(filter.getMediaKinds(), filter.getMediaKind()));
    combined = mergeEqualFilter(combined, KnowledgeVectorPayloadKeys.THEME_ID, filter.getThemeId());
    combined = mergeEqualFilter(
        combined, KnowledgeVectorPayloadKeys.INDEX_VERSION, filter.getIndexVersion());
    combined = mergeEqualFilter(
        combined, KnowledgeVectorPayloadKeys.EMBEDDING_MODEL, filter.getEmbeddingModel());
    return combined;
  }

  /**
   * 列表优先；否则单值；皆空则不限。
   *
   * @param kinds 多选
   * @param single 单值
   * @return 非空值列表
   */
  private static List<String> resolveKindValues(List<String> kinds, String single) {
    if (CollUtil.isNotEmpty(kinds)) {
      return kinds.stream().filter(StrUtil::isNotBlank).distinct().toList();
    }
    if (StrUtil.isNotBlank(single)) {
      return List.of(single.trim());
    }
    return List.of();
  }

  /**
   * 合并等值过滤条件。
   *
   * @param current 已有过滤
   * @param key     元数据键
   * @param value   期望值
   * @return 合并后的过滤
   */
  private static Filter mergeEqualFilter(Filter current, String key, String value) {
    if (StrUtil.isBlank(value)) {
      return current;
    }
    Filter next = MetadataFilterBuilder.metadataKey(key).isEqualTo(value);
    return current == null ? next : Filter.and(current, next);
  }

  /**
   * 合并多值 OR 等值过滤。
   *
   * @param current 已有过滤
   * @param key     元数据键
   * @param values  期望值列表
   * @return 合并后的过滤
   */
  private static Filter mergeAnyEqualFilter(Filter current, String key, List<String> values) {
    if (CollUtil.isEmpty(values)) {
      return current;
    }
    Filter any = null;
    for (String value : values) {
      if (StrUtil.isBlank(value)) {
        continue;
      }
      Filter next = MetadataFilterBuilder.metadataKey(key).isEqualTo(value);
      any = any == null ? next : Filter.or(any, next);
    }
    if (any == null) {
      return current;
    }
    return current == null ? any : Filter.and(current, any);
  }

  /**
   * 将 LangChain4j 检索命中转为 SPI 命中。
   *
   * @param match LangChain4j 命中
   * @return SPI 命中
   */
  private static KnowledgeVectorHit toHit(EmbeddingMatch<TextSegment> match) {
    TextSegment segment = match.embedded();
    Metadata metadata = segment == null ? new Metadata() : segment.metadata();
    Double score = match.score() == null ? 0D : match.score();
    return KnowledgeVectorHit.builder()
        .chunkId(match.embeddingId())
        .documentId(metadata.getString(KnowledgeVectorPayloadKeys.DOCUMENT_ID))
        .score(score)
        .build();
  }
}
