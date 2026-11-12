package com.chengwenfang.knowledge.infrastructure.vector;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.facade.constant.KnowledgeVectorPayloadKeys;
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

  /**
   * {@inheritDoc}
   */
  @Override
  public void upsert(List<KnowledgeVectorPoint> points) {
    if (CollUtil.isEmpty(points)) {
      return;
    }
    List<String> ids = new ArrayList<>();
    List<Embedding> embeddings = new ArrayList<>();
    List<TextSegment> segments = new ArrayList<>();
    for (KnowledgeVectorPoint point : points) {
      if (point == null || StrUtil.isBlank(point.getId()) || point.getEmbedding() == null) {
        continue;
      }
      ids.add(point.getId());
      embeddings.add(Embedding.from(point.getEmbedding()));
      segments.add(TextSegment.from(StrUtil.nullToEmpty(point.getText()), buildMetadata(point)));
    }
    if (ids.isEmpty()) {
      return;
    }
    embeddingStore.addAll(ids, embeddings, segments);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteByDocumentId(String documentId) {
    if (StrUtil.isBlank(documentId)) {
      return;
    }
    Filter filter =
        MetadataFilterBuilder.metadataKey(KnowledgeVectorPayloadKeys.DOCUMENT_ID).isEqualTo(documentId);
    embeddingStore.removeAll(filter);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<KnowledgeVectorMatch> search(float[] query, int topK, KnowledgeVectorFilter filter) {
    if (query == null || query.length == 0 || topK <= 0) {
      return List.of();
    }
    EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(Embedding.from(query))
        .maxResults(topK)
        .filter(buildSearchFilter(filter))
        .build();
    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
    if (result == null || CollUtil.isEmpty(result.matches())) {
      return List.of();
    }
    List<KnowledgeVectorMatch> matches = new ArrayList<>();
    for (EmbeddingMatch<TextSegment> match : result.matches()) {
      matches.add(toMatch(match));
    }
    return matches;
  }

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean healthy() {
    try {
      qdrantClient.healthCheckAsync(Duration.ofSeconds(3)).get(5, TimeUnit.SECONDS);
      return true;
    } catch (Exception exception) {
      log.warn("Qdrant 健康检查失败：{}", exception.getMessage());
      return false;
    }
  }

  /**
   * 关闭 gRPC 客户端。
   */
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
   * 将业务点转为 LangChain4j 元数据。
   *
   * @param point 向量点
   * @return 元数据
   */
  private static Metadata buildMetadata(KnowledgeVectorPoint point) {
    Metadata metadata = new Metadata();
    putString(metadata, KnowledgeVectorPayloadKeys.DOCUMENT_ID, point.getDocumentId());
    putString(metadata, KnowledgeVectorPayloadKeys.SOURCE_KIND, point.getSourceKind());
    putString(metadata, KnowledgeVectorPayloadKeys.SOURCE_REF_ID, point.getSourceRefId());
    putString(metadata, KnowledgeVectorPayloadKeys.MEDIA_KIND, point.getMediaKind());
    putString(metadata, KnowledgeVectorPayloadKeys.TITLE, point.getTitle());
    putString(metadata, KnowledgeVectorPayloadKeys.HEADING_PATH, point.getHeadingPath());
    if (point.getChunkIndex() != null) {
      metadata.put(KnowledgeVectorPayloadKeys.CHUNK_INDEX, point.getChunkIndex());
    }
    putString(metadata, KnowledgeVectorPayloadKeys.CONTENT_HASH, point.getContentHash());
    putString(metadata, KnowledgeVectorPayloadKeys.THEME_ID, point.getThemeId());
    putString(metadata, KnowledgeVectorPayloadKeys.DEEP_LINK, point.getDeepLink());
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
    combined = mergeEqualFilter(combined, KnowledgeVectorPayloadKeys.SOURCE_KIND, filter.getSourceKind());
    combined = mergeEqualFilter(combined, KnowledgeVectorPayloadKeys.MEDIA_KIND, filter.getMediaKind());
    combined = mergeEqualFilter(combined, KnowledgeVectorPayloadKeys.THEME_ID, filter.getThemeId());
    return combined;
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
   * 将 LangChain4j 检索命中转为业务命中视图。
   *
   * @param match LangChain4j 命中
   * @return 业务命中
   */
  private static KnowledgeVectorMatch toMatch(EmbeddingMatch<TextSegment> match) {
    TextSegment segment = match.embedded();
    Metadata metadata = segment == null ? new Metadata() : segment.metadata();
    Double score = match.score() == null ? 0D : match.score();
    Integer chunkIndex = metadata.containsKey(KnowledgeVectorPayloadKeys.CHUNK_INDEX)
        ? metadata.getInteger(KnowledgeVectorPayloadKeys.CHUNK_INDEX)
        : null;
    return KnowledgeVectorMatch.builder()
        .score(score)
        .id(match.embeddingId())
        .documentId(metadata.getString(KnowledgeVectorPayloadKeys.DOCUMENT_ID))
        .sourceKind(metadata.getString(KnowledgeVectorPayloadKeys.SOURCE_KIND))
        .sourceRefId(metadata.getString(KnowledgeVectorPayloadKeys.SOURCE_REF_ID))
        .mediaKind(metadata.getString(KnowledgeVectorPayloadKeys.MEDIA_KIND))
        .title(metadata.getString(KnowledgeVectorPayloadKeys.TITLE))
        .headingPath(metadata.getString(KnowledgeVectorPayloadKeys.HEADING_PATH))
        .chunkIndex(chunkIndex)
        .contentHash(metadata.getString(KnowledgeVectorPayloadKeys.CONTENT_HASH))
        .themeId(metadata.getString(KnowledgeVectorPayloadKeys.THEME_ID))
        .deepLink(metadata.getString(KnowledgeVectorPayloadKeys.DEEP_LINK))
        .text(segment == null ? "" : segment.text())
        .build();
  }
}
