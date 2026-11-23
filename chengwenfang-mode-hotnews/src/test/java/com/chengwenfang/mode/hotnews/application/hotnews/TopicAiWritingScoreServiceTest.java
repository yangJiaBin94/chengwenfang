package com.chengwenfang.mode.hotnews.application.hotnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.ai.facade.AiVisionFacade;
import com.chengwenfang.ai.facade.dto.view.VisionCompleteResult;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link TopicAiWritingScoreService} 有界并行评分。 */
class TopicAiWritingScoreServiceTest {

  private ContentFacade content;
  private HotNewsFacade hotNews;
  private AiVisionFacade ai;
  private ExecutorService pool;
  private TopicAiWritingScoreService service;

  @BeforeEach
  void setUp() {
    content = mock(ContentFacade.class);
    hotNews = mock(HotNewsFacade.class);
    ai = mock(AiVisionFacade.class);
    pool = Executors.newFixedThreadPool(4);
    service = new TopicAiWritingScoreService(content, hotNews, ai, pool);
  }

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
  }

  @Test
  void scoreRadarBatch_scoresTopicsInParallel() throws Exception {
    when(content.getTheme("theme-1")).thenReturn(ContentThemeView.builder()
        .id("theme-1")
        .name("测试主题")
        .briefingJson("""
            {"scoring_rubric":{"version":1,"score_guidance":"按价值",
              "dimensions":[
                {"key":"timeliness","label":"时效","weight":40,"description":"近"},
                {"key":"evidence","label":"证据","weight":30,"description":"证"},
                {"key":"payoff","label":"收益","weight":30,"description":"益"}
              ]}}
            """)
        .build());
    when(hotNews.listRadarBatchTopics("theme-1", "batch-1")).thenReturn(List.of(
        HotNewsTopicView.builder().id("t1").title("一").summary("s1").sourceCount(1L).build(),
        HotNewsTopicView.builder().id("t2").title("二").summary("s2").sourceCount(1L).build(),
        HotNewsTopicView.builder().id("t3").title("三").summary("s3").sourceCount(1L).build()));
    when(hotNews.listTopicSources(any())).thenReturn(List.of());
    when(hotNews.applyWritingScores(anyList())).thenAnswer(invocation -> {
      List<?> applies = invocation.getArgument(0);
      return applies.size();
    });

    CountDownLatch started = new CountDownLatch(3);
    when(ai.complete(any())).thenAnswer(invocation -> {
      started.countDown();
      assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
      return VisionCompleteResult.builder()
          .text("""
              {"dimensions":[
                {"key":"timeliness","score":8,"reason":"新"},
                {"key":"evidence","score":7,"reason":"够"},
                {"key":"payoff","score":6,"reason":"可"}
              ],"editorReason":"可写","recommendUse":"lead","riskNote":""}
              """)
          .build();
    });

    AtomicInteger progressHits = new AtomicInteger();
    int written = service.scoreRadarBatch(
        "theme-1",
        "batch-1",
        (done, total) -> {
          progressHits.incrementAndGet();
          assertThat(total).isEqualTo(3);
          assertThat(done).isBetween(1, 3);
        });

    assertThat(written).isEqualTo(3);
    assertThat(progressHits.get()).isEqualTo(3);
    verify(ai, times(3)).complete(any());
    verify(hotNews).applyWritingScores(anyList());
    verify(hotNews).listRadarBatchTopics(eq("theme-1"), eq("batch-1"));
  }
}
