package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchRunMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceChannelMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceFetchChannelRunMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceFetchRunMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceItemMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link HotNewsFetchService#hasActiveJobsForTheme}：Job 活跃时闸门为 true。
 */
class HotNewsFetchServiceHasActiveJobsTest {
  private SourceFetchRunMapper runMapper;
  private TopicClusterMapper topicMapper;
  private TopicResearchRunMapper researchRunMapper;
  private HotNewsWorkStore workStore;
  private JobFacade jobs;
  private HotNewsFetchService service;

  @BeforeEach
  void setUp() {
    runMapper = mock(SourceFetchRunMapper.class);
    topicMapper = mock(TopicClusterMapper.class);
    researchRunMapper = mock(TopicResearchRunMapper.class);
    workStore = mock(HotNewsWorkStore.class);
    jobs = mock(JobFacade.class);
    service = new HotNewsFetchService(
        runMapper,
        mock(SourceFetchChannelRunMapper.class),
        mock(SourceItemMapper.class),
        mock(SourceChannelMapper.class),
        topicMapper,
        researchRunMapper,
        workStore,
        jobs,
        mock(VideoParseRunService.class),
        mock(HotNewsTopicService.class),
        mock(OwnerScope.class));
  }

  @Test
  void hasActiveJobsForTheme_trueWhenJobActiveDespiteSucceededFetchRun() {
    // FINALIZE 后 fetch_run 已空闲时，仍须靠 Job 活跃态拦住
    when(jobs.hasActiveJobsForTheme("theme-1")).thenReturn(true);

    assertThat(service.hasActiveJobsForTheme("theme-1")).isTrue();

    verify(jobs).hasActiveJobsForTheme("theme-1");
    verifyNoInteractions(runMapper, topicMapper, researchRunMapper, workStore);
  }

  @Test
  void hasActiveJobsForTheme_falseWhenThemeIdBlank() {
    assertThat(service.hasActiveJobsForTheme("  ")).isFalse();
    verifyNoInteractions(jobs, runMapper);
  }
}
