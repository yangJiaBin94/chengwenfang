package com.chengwenfang.knowledge.infrastructure.graph;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.application.KnowledgeIndexVersionService;
import com.chengwenfang.knowledge.facade.constant.KnowledgeGraphConstants;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeCitationView;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Neo4j 图检索实现（需 feature flag 与 Docker profile）。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "chengwenfang.knowledge.graph", name = "enabled", havingValue = "true")
public class Neo4jKnowledgeGraphStore implements KnowledgeGraphStore {

  /** Bolt Driver。 */
  private final Driver driver;
  /** 活跃版本。 */
  private final KnowledgeIndexVersionService indexVersionService;

  /**
   * 按配置建立 Driver。
   *
   * @param properties           全局配置
   * @param indexVersionService  版本服务
   */
  public Neo4jKnowledgeGraphStore(
      ChengwenfangProperties properties, KnowledgeIndexVersionService indexVersionService) {
    this.indexVersionService = indexVersionService;
    ChengwenfangProperties.Neo4jSettings settings = properties.getNeo4j();
    this.driver = GraphDatabase.driver(
        settings.getUri(),
        AuthTokens.basic(settings.getUsername(), settings.getPassword()));
  }

  /** {@inheritDoc} */
  @Override
  public boolean enabled() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean available() {
    try (Session session = driver.session()) {
      session.run("RETURN 1").consume();
      return true;
    } catch (Exception exception) {
      log.warn("Neo4j 不可用：{}", exception.getMessage());
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<KnowledgeCitationView> searchLocal(String query, int topK) {
    if (StrUtil.isBlank(query) || topK <= 0 || !available()) {
      return List.of();
    }
    String version = indexVersionService.activeVersion();
    String cypher = """
        MATCH (c:Concept)-[r:MENTIONS|RELATED_TO]->(n)
        WHERE c.indexVersion = $version AND coalesce(r.enabled, true) = true
          AND (c.name CONTAINS $q OR n.name CONTAINS $q)
        RETURN coalesce(r.sourceChunkId, c.sourceChunkId) AS chunkId,
               coalesce(c.name, '') AS title,
               coalesce(r.snippet, n.name, '') AS snippet
        LIMIT $limit
        """;
    return runCitationQuery(cypher, query.trim(), version, topK);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<KnowledgeCitationView> searchGlobal(String query, int topK) {
    if (StrUtil.isBlank(query) || topK <= 0 || !available()) {
      return List.of();
    }
    String version = indexVersionService.activeVersion();
    String cypher = """
        MATCH (com:Community)
        WHERE com.indexVersion = $version
          AND (com.summary CONTAINS $q OR com.name CONTAINS $q)
        RETURN com.id AS chunkId, com.name AS title, com.summary AS snippet
        LIMIT $limit
        """;
    return runCitationQuery(cypher, query.trim(), version, topK);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteEdgesBySourceChunkId(String sourceChunkId) {
    if (StrUtil.isBlank(sourceChunkId) || !available()) {
      return;
    }
    try (Session session = driver.session()) {
      session.run(
          "MATCH ()-[r]->() WHERE r.sourceChunkId = $chunkId DELETE r",
          Values.parameters("chunkId", sourceChunkId));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deleteByDocumentId(String documentId, List<String> chunkIds) {
    if (!available() || CollUtil.isEmpty(chunkIds)) {
      return;
    }
    try (Session session = driver.session()) {
      session.run(
          """
              MATCH ()-[r]->() WHERE r.sourceChunkId IN $chunkIds DELETE r
              """,
          Values.parameters("chunkIds", chunkIds));
      session.run(
          """
              MATCH (c:Concept) WHERE c.sourceChunkId IN $chunkIds DETACH DELETE c
              """,
          Values.parameters("chunkIds", chunkIds));
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Neo4j 按文档删边失败 documentId=" + documentId + "：" + exception.getMessage(),
          exception);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void upsertChunkMentions(
      String chunkId,
      String documentTitle,
      String headingPath,
      String snippet,
      String indexVersion,
      double confidence) {
    if (StrUtil.isBlank(chunkId) || !available()) {
      return;
    }
    String conceptName = StrUtil.blankToDefault(headingPath, documentTitle);
    if (StrUtil.isBlank(conceptName)) {
      conceptName = "chunk:" + chunkId;
    }
    boolean enabled = confidence >= KnowledgeGraphConstants.LOW_CONFIDENCE;
    String edgeUid = KnowledgeGraphConstants.mentionsEdgeUid(chunkId);
    // 按切块合并，避免同 headingPath 多块互相覆盖 sourceChunkId
    String cypher = """
        MERGE (c:Concept {sourceChunkId: $chunkId, indexVersion: $version})
        SET c.name = $name
        MERGE (w:Work {name: $title, indexVersion: $version})
        MERGE (c)-[r:MENTIONS {uid: $edgeUid}]->(w)
        SET r.sourceChunkId = $chunkId,
            r.confidence = $confidence,
            r.enabled = $enabled,
            r.snippet = $snippet,
            r.indexVersion = $version
        """;
    try (Session session = driver.session()) {
      session.run(
          cypher,
          Values.parameters(Map.of(
              "name", conceptName,
              "title", StrUtil.blankToDefault(documentTitle, conceptName),
              "chunkId", chunkId,
              "version", StrUtil.blankToDefault(indexVersion, "v1"),
              "edgeUid", edgeUid,
              "confidence", confidence,
              "enabled", enabled,
              "snippet", StrUtil.maxLength(StrUtil.nullToEmpty(snippet), 400))));
    } catch (Exception exception) {
      throw new IllegalStateException(
          "Neo4j 写边失败 chunkId=" + chunkId + "：" + exception.getMessage(), exception);
    }
  }

  /**
   * 执行返回引用的 Cypher。
   *
   * @param cypher  语句
   * @param query   查询词
   * @param version 版本
   * @param topK    上限
   * @return 引用列表
   */
  private List<KnowledgeCitationView> runCitationQuery(
      String cypher, String query, String version, int topK) {
    List<KnowledgeCitationView> citations = new ArrayList<>();
    try (Session session = driver.session()) {
      Result result = session.run(
          cypher,
          Values.parameters(Map.of("q", query, "version", version, "limit", topK)));
      while (result.hasNext()) {
        Record record = result.next();
        citations.add(KnowledgeCitationView.builder()
            .chunkId(record.get("chunkId").asString(""))
            .title(record.get("title").asString(""))
            .snippet(record.get("snippet").asString(""))
            .build());
      }
    } catch (Exception exception) {
      log.warn("Neo4j 检索失败：{}", exception.getMessage());
    }
    return citations;
  }

  /** {@inheritDoc} */
  @Override
  public void setEdgeEnabled(String edgeUid, boolean enabled) {
    if (StrUtil.isBlank(edgeUid) || !available()) {
      return;
    }
    try (Session session = driver.session()) {
      session.run(
          "MATCH ()-[r]->() WHERE r.uid = $edgeUid SET r.enabled = $enabled",
          Values.parameters("edgeUid", edgeUid, "enabled", enabled));
    } catch (Exception exception) {
      log.warn("Neo4j 更新边 enabled 失败 edgeUid={} err={}", edgeUid, exception.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void deleteByIndexVersion(String indexVersion) {
    if (StrUtil.isBlank(indexVersion) || !available()) {
      return;
    }
    try (Session session = driver.session()) {
      session.run(
          "MATCH ()-[r]->() WHERE r.indexVersion = $version DELETE r",
          Values.parameters("version", indexVersion));
      session.run(
          """
              MATCH (n)
              WHERE n.indexVersion = $version AND (n:Concept OR n:Work OR n:Community)
              DETACH DELETE n
              """,
          Values.parameters("version", indexVersion));
    } catch (Exception exception) {
      log.warn("Neo4j 按版本 GC 失败 version={} err={}", indexVersion, exception.getMessage());
    }
  }

  /**
   * 关闭 Driver。
   */
  @PreDestroy
  public void close() {
    driver.close();
  }
}
