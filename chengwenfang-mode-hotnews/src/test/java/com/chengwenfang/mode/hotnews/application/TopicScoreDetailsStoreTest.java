package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.mode.hotnews.domain.TopicDimensionScore;
import com.chengwenfang.mode.hotnews.domain.TopicScoreDetails;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsTopicScoreDimensionEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsTopicScoreMetricEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsTopicScoreTagEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotnewsScoreDimensionSets;
import com.chengwenfang.mode.hotnews.facade.constant.HotnewsScoreTagTypes;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsTopicScoreDimensionMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsTopicScoreMetricMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsTopicScoreTagMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link TopicScoreDetailsStore} 空 / 部分 / 完整 往返。
 */
@ExtendWith(MockitoExtension.class)
class TopicScoreDetailsStoreTest {

  @Mock
  private HotnewsTopicScoreMetricMapper metricMapper;
  @Mock
  private HotnewsTopicScoreDimensionMapper dimensionMapper;
  @Mock
  private HotnewsTopicScoreTagMapper tagMapper;

  private TopicScoreDetailsStore store;

  @BeforeEach
  void setUp() {
    store = new TopicScoreDetailsStore(metricMapper, dimensionMapper, tagMapper);
  }

  @Test
  void read_without_metrics_row_returns_empty_details() {
    when(metricMapper.selectById("t1")).thenReturn(null);

    TopicScoreDetails details = store.read("t1");

    assertThat(details.getSourceCount()).isZero();
    assertThat(details.getSourceTypes()).isEmpty();
    assertThat(details.getDimensions()).isEmpty();
    verify(tagMapper, never()).selectList(any());
  }

  @Test
  void write_and_read_partial_metrics_only() {
    when(metricMapper.selectById("t2")).thenReturn(null);

    TopicScoreDetails partial = TopicScoreDetails.builder()
        .sourceCount(3)
        .officialCount(1)
        .metricNote("partial")
        .recommendUse("watch")
        .build();
    store.write("t2", partial);

    ArgumentCaptor<HotnewsTopicScoreMetricEntity> metricCaptor =
        ArgumentCaptor.forClass(HotnewsTopicScoreMetricEntity.class);
    verify(metricMapper).insert(metricCaptor.capture());
    assertThat(metricCaptor.getValue().getTopicId()).isEqualTo("t2");
    assertThat(metricCaptor.getValue().getSourceCount()).isEqualTo(3);
    assertThat(metricCaptor.getValue().getMetricNote()).isEqualTo("partial");
    verify(tagMapper, never()).insert(any(HotnewsTopicScoreTagEntity.class));
    verify(dimensionMapper, never()).insert(any(HotnewsTopicScoreDimensionEntity.class));

    when(metricMapper.selectById("t2")).thenReturn(metricCaptor.getValue());
    when(tagMapper.selectList(any())).thenReturn(List.of());
    when(dimensionMapper.selectList(any())).thenReturn(List.of());

    TopicScoreDetails read = store.read("t2");
    assertThat(read.getSourceCount()).isEqualTo(3);
    assertThat(read.getOfficialCount()).isEqualTo(1);
    assertThat(read.getMetricNote()).isEqualTo("partial");
    assertThat(read.getRecommendUse()).isEqualTo("watch");
    assertThat(read.getSourceTypes()).isEmpty();
    assertThat(read.getDimensions()).isEmpty();
  }

  @Test
  void write_and_read_full_details_round_trip() {
    when(metricMapper.selectById("t3")).thenReturn(null);

    TopicScoreDetails full = TopicScoreDetails.builder()
        .sourceCount(5)
        .officialCount(2)
        .primaryCount(1)
        .secondaryCount(1)
        .communityCount(1)
        .unknownDateCount(0)
        .independentSourceCount(4)
        .recentSourceCount(2)
        .aiHits(1)
        .depthHits(1)
        .sourceGrowthRatio(0.4)
        .freshnessHours(6.5)
        .metricNote("full")
        .rubricVersion(2)
        .recommendUse("lead")
        .riskNote("注意时效")
        .originKind("ai_theme_seed")
        .sourceTypes(List.of("official", "community"))
        .signals(List.of("rising"))
        .dimensions(List.of(TopicDimensionScore.builder()
            .key("heat")
            .label("热度")
            .weight(40)
            .score(8)
            .reason("多源")
            .build()))
        .rubricSnapshot(List.of(TopicDimensionScore.builder()
            .key("heat")
            .label("热度")
            .weight(40)
            .score(null)
            .reason(null)
            .build()))
        .build();
    store.write("t3", full);

    verify(metricMapper).insert(any(HotnewsTopicScoreMetricEntity.class));
    ArgumentCaptor<HotnewsTopicScoreTagEntity> tagCaptor =
        ArgumentCaptor.forClass(HotnewsTopicScoreTagEntity.class);
    verify(tagMapper, org.mockito.Mockito.times(3)).insert(tagCaptor.capture());
    assertThat(tagCaptor.getAllValues())
        .extracting(HotnewsTopicScoreTagEntity::getTagType)
        .containsExactly(
            HotnewsScoreTagTypes.SOURCE_TYPE,
            HotnewsScoreTagTypes.SOURCE_TYPE,
            HotnewsScoreTagTypes.SIGNAL);
    ArgumentCaptor<HotnewsTopicScoreDimensionEntity> dimCaptor =
        ArgumentCaptor.forClass(HotnewsTopicScoreDimensionEntity.class);
    verify(dimensionMapper, org.mockito.Mockito.times(2)).insert(dimCaptor.capture());
    assertThat(dimCaptor.getAllValues())
        .extracting(HotnewsTopicScoreDimensionEntity::getDimensionSet)
        .containsExactly(
            HotnewsScoreDimensionSets.CURRENT,
            HotnewsScoreDimensionSets.RUBRIC_SNAPSHOT);

    HotnewsTopicScoreMetricEntity storedMetric = HotnewsTopicScoreMetricEntity.builder()
        .topicId("t3")
        .sourceCount(5)
        .officialCount(2)
        .primaryCount(1)
        .secondaryCount(1)
        .communityCount(1)
        .unknownDateCount(0)
        .independentSourceCount(4)
        .recentSourceCount(2)
        .aiHits(1)
        .depthHits(1)
        .sourceGrowthRatio(0.4)
        .freshnessHours(6.5)
        .metricNote("full")
        .rubricVersion(2)
        .recommendUse("lead")
        .riskNote("注意时效")
        .originKind("ai_theme_seed")
        .build();
    when(metricMapper.selectById("t3")).thenReturn(storedMetric);
    // 按 tag_type / dimension_set 两次查询各返回对应行
    when(tagMapper.selectList(any()))
        .thenReturn(List.of(
            HotnewsTopicScoreTagEntity.builder()
                .tagType(HotnewsScoreTagTypes.SOURCE_TYPE)
                .seq(0)
                .tagValue("official")
                .build(),
            HotnewsTopicScoreTagEntity.builder()
                .tagType(HotnewsScoreTagTypes.SOURCE_TYPE)
                .seq(1)
                .tagValue("community")
                .build()))
        .thenReturn(List.of(HotnewsTopicScoreTagEntity.builder()
            .tagType(HotnewsScoreTagTypes.SIGNAL)
            .seq(0)
            .tagValue("rising")
            .build()));
    when(dimensionMapper.selectList(any()))
        .thenReturn(List.of(HotnewsTopicScoreDimensionEntity.builder()
            .dimensionSet(HotnewsScoreDimensionSets.CURRENT)
            .seq(0)
            .dimensionKey("heat")
            .label("热度")
            .weight(40)
            .score(8)
            .reason("多源")
            .build()))
        .thenReturn(List.of(HotnewsTopicScoreDimensionEntity.builder()
            .dimensionSet(HotnewsScoreDimensionSets.RUBRIC_SNAPSHOT)
            .seq(0)
            .dimensionKey("heat")
            .label("热度")
            .weight(40)
            .build()));

    TopicScoreDetails read = store.read("t3");
    assertThat(read.getSourceCount()).isEqualTo(5);
    assertThat(read.getSourceTypes()).containsExactly("official", "community");
    assertThat(read.getSignals()).containsExactly("rising");
    assertThat(read.getDimensions()).hasSize(1);
    assertThat(read.getDimensions().get(0).getKey()).isEqualTo("heat");
    assertThat(read.getDimensions().get(0).getScore()).isEqualTo(8);
    assertThat(read.getRubricSnapshot()).hasSize(1);
    assertThat(read.getOriginKind()).isEqualTo("ai_theme_seed");
  }
}
