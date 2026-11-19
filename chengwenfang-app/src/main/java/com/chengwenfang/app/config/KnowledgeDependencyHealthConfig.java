package com.chengwenfang.app.config;

import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库依赖就绪探针（Qdrant / Neo4j）。
 */
@Configuration
public class KnowledgeDependencyHealthConfig {

  /**
   * 向量库健康：未降级视为 UP。
   *
   * @param vectorStore 向量 SPI
   * @return Indicator
   */
  @Bean
  public HealthIndicator knowledgeVectorHealthIndicator(KnowledgeVectorStore vectorStore) {
    return () -> {
      boolean degraded = vectorStore != null && vectorStore.degraded();
      if (degraded) {
        return Health.down()
            .withDetail("degradedReason", "vector_fallback_memory")
            .build();
      }
      return Health.up().withDetail("store", "vector").build();
    };
  }

  /**
   * 图库健康：available 为 UP；未启用时为 UNKNOWN。
   *
   * @param graphStore 图 SPI
   * @return Indicator
   */
  @Bean
  public HealthIndicator knowledgeGraphHealthIndicator(KnowledgeGraphStore graphStore) {
    return () -> {
      if (graphStore == null) {
        return Health.unknown().withDetail("store", "graph").build();
      }
      if (graphStore.available()) {
        return Health.up().withDetail("store", "graph").build();
      }
      return Health.down()
          .withDetail("degradedReason", "graph_unavailable")
          .build();
    };
  }
}
