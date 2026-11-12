package com.chengwenfang.knowledge.infrastructure.vector;

import com.chengwenfang.infra.config.ChengwenfangProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库向量存储 Bean：按配置选择 Qdrant 或内存实现。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KnowledgeVectorConfig {

  /** 成文坊全局配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 注册 {@link KnowledgeVectorStore}；Qdrant 启用但连接失败时回退内存并打警告日志。
   *
   * @return 向量存储实现
   */
  @Bean
  @ConditionalOnMissingBean(KnowledgeVectorStore.class)
  public KnowledgeVectorStore knowledgeVectorStore() {
    ChengwenfangProperties.QdrantSettings qdrant = properties.getQdrant();
    if (qdrant != null && qdrant.isEnabled()) {
      try {
        log.info(
            "知识库向量存储：使用 Qdrant {}:{}（gRPC {}）集合 {}",
            qdrant.getHost(),
            qdrant.getHttpPort(),
            qdrant.getGrpcPort(),
            qdrant.getCollection());
        return new QdrantKnowledgeVectorStore(qdrant);
      } catch (Exception exception) {
        log.warn("连接 Qdrant 失败，回退内存向量存储：{}", exception.getMessage());
        return new InMemoryKnowledgeVectorStore();
      }
    }
    log.info("知识库向量存储：Qdrant 未启用，使用内存实现");
    return new InMemoryKnowledgeVectorStore();
  }
}
