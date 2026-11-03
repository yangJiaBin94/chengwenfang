package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.mode.hotnews.application.model.ResearchCandidate;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchRunEntity;
import com.chengwenfang.mode.hotnews.facade.enums.ResearchItemKind;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchConflictMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchRunMapper;
import com.chengwenfang.sourcing.domain.entity.VideoTranscriptEntity;
import com.chengwenfang.sourcing.facade.constant.VideoParseOwnerTypes;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 资料池替换时按 URL remount 视频解析行。 */
@ExtendWith(MockitoExtension.class)
class HotNewsTopicResearchPoolTxRemountTest {

  @Mock
  private TopicResearchItemMapper researchItemMapper;
  @Mock
  private TopicResearchConflictMapper researchConflictMapper;
  @Mock
  private TopicResearchRunMapper researchRunMapper;
  @Mock
  private VideoParseRunService videoParseRuns;
  @Mock
  private ResearchItemScoreDetailsStore scoreDetailsStore;

  @InjectMocks
  private HotNewsTopicResearchPoolTx poolTx;

  @Test
  void remounts_succeeded_transcript_to_new_item_id() {
    String oldItemId = "old-item";
    String url = "https://www.youtube.com/watch?v=abc123";
    TopicResearchItemEntity oldItem = TopicResearchItemEntity.builder()
        .id(oldItemId)
        .topicId("topic-1")
        .title("旧视频")
        .url(url)
        .summary("已解析摘要")
        .kind(ResearchItemKind.WEB.getValue())
        .build();
    VideoTranscriptEntity transcript = VideoTranscriptEntity.builder()
        .id("tx-1")
        .researchItemId(oldItemId)
        .url(url)
        .status(VideoParseStatus.SUCCEEDED.getValue())
        .durationSeconds(120)
        .build();

    when(researchItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(oldItem));
    when(videoParseRuns.mapByOwnerIds(
            eq(VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM), anyList()))
        .thenReturn(Map.of(oldItemId, transcript));
    when(researchConflictMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
    when(researchItemMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

    TopicResearchRunEntity run = TopicResearchRunEntity.builder()
        .id("run-1")
        .topicId("topic-1")
        .build();
    ResearchCandidate candidate = ResearchCandidate.builder()
        .title("新标题同链")
        .url(url)
        .summary("搜索摘要")
        .kind(ResearchItemKind.WEB)
        .score(80)
        .build();

    poolTx.replacePool("topic-1", run, List.of(candidate), false, false, LocalDateTime.now());

    ArgumentCaptor<TopicResearchItemEntity> itemCaptor =
        ArgumentCaptor.forClass(TopicResearchItemEntity.class);
    verify(researchItemMapper).insert(itemCaptor.capture());
    TopicResearchItemEntity inserted = itemCaptor.getValue();
    assertThat(inserted.getSummary()).isEqualTo("已解析摘要");
    assertThat(inserted.getId()).isNotEqualTo(oldItemId);

    verify(videoParseRuns).rebindResearchItem(
        eq(transcript), eq(inserted.getId()), eq(url), eq(120));
    verify(videoParseRuns, never()).ensureNone(
        eq(VideoParseOwnerTypes.TOPIC_RESEARCH_ITEM),
        any(),
        any(),
        any(),
        any());
    verify(videoParseRuns, never()).deleteByIds(any());
  }
}
