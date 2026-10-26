package com.workbench.app.golden.stub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.crawler.application.CrawlerUrlGuard;
import com.workbench.crawler.application.FetchFacadeImpl;
import com.workbench.crawler.application.article.LightpandaArticleExtractor;
import com.workbench.crawler.application.article.TrafilaturaArticleExtractor;
import com.workbench.crawler.facade.FetchFacade;
import com.workbench.crawler.facade.dto.request.FetchRequest;
import com.workbench.crawler.facade.dto.view.FetchResult;
import com.workbench.sourcing.application.VideoTranscriptCliRunner;
import com.workbench.infra.config.WorkbenchProperties;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.WorkflowFacade;
import java.util.List;
import org.redisson.api.RedissonClient;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 黄金 IT 替身 Bean：覆盖 CLI / 抽取实现，并导入种子 Skill。
 */
@TestConfiguration
public class GoldenTestConfiguration {
  /**
   * @param skills       Skill 门面
   * @param workflows    工作流门面
   * @param jdbc         JDBC（排版主题）
   * @param objectMapper JSON
   * @return catalog 预热
   */
  @Bean
  public ApplicationRunner goldenCatalogWarmup(
      SkillFacade skills, WorkflowFacade workflows, JdbcTemplate jdbc, ObjectMapper objectMapper) {
    return new GoldenCatalogWarmup(skills, workflows, jdbc, objectMapper);
  }
  /**
   * @return Trafilatura 替身
   */
  @Bean
  @Primary
  public TrafilaturaArticleExtractor goldenTrafilatura() {
    return GoldenTestBeans.fixedTrafilatura();
  }

  /**
   * @return Lightpanda 替身
   */
  @Bean
  @Primary
  public LightpandaArticleExtractor goldenLightpanda() {
    return GoldenTestBeans.fixedLightpanda();
  }

  /**
   * @param properties 配置
   * @return 视频 CLI 替身
   */
  @Bean
  @Primary
  public VideoTranscriptCliRunner goldenVideoTranscriptCliRunner(WorkbenchProperties properties) {
    return GoldenTestBeans.fixtureVideoTranscriptCliRunner(properties);
  }

  /**
   * 黄金场景对本机 WireMock 突发抓取不做主机限流（避免调研多查询打满 5/s）。
   *
   * @param redisson   Redis
   * @param properties 配置
   * @return 替身
   */
  @Bean
  @Primary
  public CrawlerUrlGuard goldenCrawlerUrlGuard(
      RedissonClient redisson, WorkbenchProperties properties) {
    return new CrawlerUrlGuard(redisson, properties) {
      @Override
      public void limit(String url) {
        // 黄金桩：跳过限流
      }
    };
  }

  /**
   * 连通性探测恒成功；真实抓取仍走 {@link FetchFacadeImpl}（G09 雷达依赖外部 WireMock）。
   *
   * @param real 原实现
   * @return 包装门面
   */
  @Bean
  @Primary
  public FetchFacade goldenFetchFacade(FetchFacadeImpl real) {
    return new FetchFacade() {
      @Override
      public FetchResult fetch(FetchRequest request) {
        return real.fetch(request);
      }

      @Override
      public List<FetchResult> fetchBatch(List<FetchRequest> requests) {
        return real.fetchBatch(requests);
      }

      @Override
      public FetchResult testConnection(FetchRequest request) {
        return FetchResult.builder().ok(true).items(List.of()).build();
      }
    };
  }
}
