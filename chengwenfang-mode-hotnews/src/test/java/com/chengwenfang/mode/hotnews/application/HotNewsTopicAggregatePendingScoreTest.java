package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import org.junit.jupiter.api.Test;

/** 日限额对待写作评分选题的保护判定。 */
class HotNewsTopicAggregatePendingScoreTest {

  @Test
  void isPendingWritingScore_onlyDiscoveredWithZeroWriting() {
    TopicClusterEntity pending = new TopicClusterEntity();
    pending.setStatus(HotNewsTopicStatuses.DISCOVERED);
    pending.setWritingScore(0);
    assertThat(HotNewsTopicAggregateService.isPendingWritingScore(pending)).isTrue();

    TopicClusterEntity scoredCandidate = new TopicClusterEntity();
    scoredCandidate.setStatus(HotNewsTopicStatuses.CANDIDATE);
    scoredCandidate.setWritingScore(80);
    assertThat(HotNewsTopicAggregateService.isPendingWritingScore(scoredCandidate)).isFalse();

    TopicClusterEntity discoveredScored = new TopicClusterEntity();
    discoveredScored.setStatus(HotNewsTopicStatuses.DISCOVERED);
    discoveredScored.setWritingScore(55);
    assertThat(HotNewsTopicAggregateService.isPendingWritingScore(discoveredScored)).isFalse();

    assertThat(HotNewsTopicAggregateService.isPendingWritingScore(null)).isFalse();
  }
}
