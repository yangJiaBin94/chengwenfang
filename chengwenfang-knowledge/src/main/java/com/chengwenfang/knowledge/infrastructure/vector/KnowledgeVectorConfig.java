package com.chengwenfang.knowledge.infrastructure.vector;

import com.chengwenfang.infra.config.ChengwenfangProperties;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库向量存储 Bean：Qdrant 启用时必须连上，失败不回退内存。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class KnowledgeVectorConfig {

  /** 成文坊全局配置。 */
  private final ChengwenfangProperties properties;

  /**
   * 注册 facade SPI {@link KnowledgeVectorStore}。
   * <p>Qdrant 已启用但健康检查失败时抛出，禁止静默改用内存向量库。
   *
   * @return 向量存储实现
   */
  @Bean
  @ConditionalOnMissingBean(KnowledgeVectorStore.class)
  public KnowledgeVectorStore knowledgeVectorStore() {
    return open(properties.getQdrant());
  }

  /**
   * 按开关打开向量库。仅在明确关闭 Qdrant 时使用内存实现（测试用，不计为就绪）。
   *
   * @param qdrant Qdrant 配置；可空
   * @return 向量存储
   */
  static KnowledgeVectorStore open(ChengwenfangProperties.QdrantSettings qdrant) {
    if (qdrant == null || !qdrant.isEnabled()) {
      log.info("知识库向量存储：Qdrant 未启用，使用内存实现（系统就绪不计为已连接）");
      return new InMemoryKnowledgeVectorStore();
    }
    log.info(
        "知识库向量存储：使用 Qdrant {}:{}（gRPC {}）集合 {}",
        qdrant.getHost(),
        qdrant.getHttpPort(),
        qdrant.getGrpcPort(),
        qdrant.getCollection());
    QdrantKnowledgeVectorStore store;
    try {
      store = new QdrantKnowledgeVectorStore(qdrant);
    } catch (Exception exception) {
      throw refuseMemoryFallback(qdrant, exception);
    }
    if (store.degraded()) {
      closeQuietly(store);
      throw refuseMemoryFallback(qdrant, null);
    }
    return store;
  }

  /**
   * 拒绝把连不上的 Qdrant 当成内存库成功启动。
   *
   * @param qdrant    连接配置
   * @param cause     建连异常；健康检查失败时为空
   * @return 启动失败异常
   */
  private static IllegalStateException refuseMemoryFallback(
      ChengwenfangProperties.QdrantSettings qdrant, Exception cause) {
    String message = "Qdrant 已启用但未连通 "
        + qdrant.getHost()
        + ":"
        + qdrant.getGrpcPort()
        + "，拒绝回退内存向量库。请先执行 docker compose up -d --wait（容器内主机名应为 qdrant）。";
    log.error(message);
    if (cause == null) {
      return new IllegalStateException(message);
    }
    return new IllegalStateException(message, cause);
  }

  /**
   * 健康检查失败时关掉半开客户端。
   *
   * @param store 已创建的 Qdrant 存储
   */
  private static void closeQuietly(QdrantKnowledgeVectorStore store) {
    try {
      store.destroy();
    } catch (Exception ignored) {
      // 关闭失败不影响启动失败原因
    }
  }
}
