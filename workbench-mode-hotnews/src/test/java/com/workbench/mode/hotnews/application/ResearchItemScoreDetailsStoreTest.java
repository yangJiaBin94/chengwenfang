package com.workbench.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workbench.mode.hotnews.domain.ResearchItemScoreDetails;
import com.workbench.mode.hotnews.domain.TopicDimensionScore;
import com.workbench.mode.hotnews.domain.entity.HotnewsResearchItemDimensionEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.workbench.mode.hotnews.infrastructure.mapper.HotnewsResearchItemDimensionMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ResearchItemScoreDetailsStore} 无明细 / 有明细往返。
 */
@ExtendWith(MockitoExtension.class)
class ResearchItemScoreDetailsStoreTest {

  @Mock
  private TopicResearchItemMapper itemMapper;
  @Mock
  private HotnewsResearchItemDimensionMapper dimensionMapper;

  private ResearchItemScoreDetailsStore store;

  @BeforeEach
  void setUp() {
    store = new ResearchItemScoreDetailsStore(itemMapper, dimensionMapper);
  }

  @Test
  void read_without_details_flag_returns_null() {
    when(itemMapper.selectById("i1")).thenReturn(TopicResearchItemEntity.builder()
        .id("i1")
        .hasScoreDetails(false)
        .build());

    assertThat(store.read("i1")).isNull();
    verify(dimensionMapper, never()).selectList(any());
  }

  @Test
  void write_null_clears_details() {
    when(itemMapper.selectById("i2")).thenReturn(TopicResearchItemEntity.builder()
        .id("i2")
        .hasScoreDetails(true)
        .rubricVersion(1)
        .build());

    store.write("i2", null);

    ArgumentCaptor<TopicResearchItemEntity> itemCaptor =
        ArgumentCaptor.forClass(TopicResearchItemEntity.class);
    verify(itemMapper).updateById(itemCaptor.capture());
    assertThat(itemCaptor.getValue().getHasScoreDetails()).isFalse();
    assertThat(itemCaptor.getValue().getRubricVersion()).isNull();
    verify(dimensionMapper).delete(any());
    verify(dimensionMapper, never()).insert(any(HotnewsResearchItemDimensionEntity.class));
  }

  @Test
  void write_and_read_roundtrip_with_dimensions() {
    TopicResearchItemEntity row = TopicResearchItemEntity.builder()
        .id("i3")
        .hasScoreDetails(true)
        .rubricVersion(2)
        .recommendUse("priority")
        .riskNote("note")
        .build();
    when(itemMapper.selectById("i3")).thenReturn(row);
    when(dimensionMapper.selectList(any())).thenReturn(List.of(
        HotnewsResearchItemDimensionEntity.builder()
            .id("d1")
            .itemId("i3")
            .seq(0)
            .dimensionKey("relevance")
            .label("相关性")
            .weight(20)
            .score(9)
            .reason("贴近选题")
            .build()));

    ResearchItemScoreDetails written = ResearchItemScoreDetails.builder()
        .rubricVersion(2)
        .recommendUse("priority")
        .riskNote("note")
        .dimensions(List.of(TopicDimensionScore.builder()
            .key("relevance")
            .label("相关性")
            .weight(20)
            .score(9)
            .reason("贴近选题")
            .build()))
        .build();
    store.write("i3", written);

    ArgumentCaptor<HotnewsResearchItemDimensionEntity> dimCaptor =
        ArgumentCaptor.forClass(HotnewsResearchItemDimensionEntity.class);
    verify(dimensionMapper).insert(dimCaptor.capture());
    assertThat(dimCaptor.getValue().getDimensionKey()).isEqualTo("relevance");
    assertThat(dimCaptor.getValue().getScore()).isEqualTo(9);

    ResearchItemScoreDetails read = store.read("i3");
    assertThat(read).isNotNull();
    assertThat(read.getRubricVersion()).isEqualTo(2);
    assertThat(read.getRecommendUse()).isEqualTo("priority");
    assertThat(read.getRiskNote()).isEqualTo("note");
    assertThat(read.getDimensions()).hasSize(1);
    assertThat(read.getDimensions().get(0).getKey()).isEqualTo("relevance");
    assertThat(read.getDimensions().get(0).getScore()).isEqualTo(9);
  }

  @Test
  void isBlankDetails_null_and_empty() {
    assertThat(ResearchItemScoreDetailsStore.isBlankDetails(null)).isTrue();
    assertThat(ResearchItemScoreDetailsStore.isBlankDetails(new ResearchItemScoreDetails())).isTrue();
    assertThat(ResearchItemScoreDetailsStore.isBlankDetails(ResearchItemScoreDetails.builder()
        .recommendUse("supplement")
        .build())).isFalse();
  }
}
