package com.chengwenfang.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsBriefItemEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsBriefSourceEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicBriefEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotnewsBriefItemTypes;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefSourceRow;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsBriefItemMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsBriefSourceMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link BriefStructuredStore} summary + sources 往返。
 */
@ExtendWith(MockitoExtension.class)
class BriefStructuredStoreTest {

  @Mock
  private HotnewsBriefItemMapper itemMapper;
  @Mock
  private HotnewsBriefSourceMapper sourceMapper;

  private BriefStructuredStore store;

  @BeforeEach
  void setUp() {
    store = new BriefStructuredStore(itemMapper, sourceMapper, new HotNewsCodec(new ObjectMapper()));
  }

  @Test
  void write_children_then_read_summary_and_sources() {
    TopicBriefEntity brief = TopicBriefEntity.builder()
        .id("b1")
        .topicId("t1")
        .themeId("theme-1")
        .status("draft")
        .title("简报标题")
        .build();

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("overview", "概览");
    summary.put("coreThesis", "论点");
    summary.put("coreEvent", "事件");
    summary.put("confirmedFacts", List.of("事实A"));
    summary.put("readerImpact", List.of("影响B"));
    summary.put("recommendedAngles", List.of("角度C"));
    summary.put("unknowns", List.of("未知D"));
    summary.put("narrativeShape", "叙事");
    summary.put("confirmationNote", "备注");

    List<HotNewsBriefSourceRow> sources = List.of(HotNewsBriefSourceRow.builder()
        .id("row-1")
        .topicId("t1")
        .sourceItemId("si-1")
        .role("evidence")
        .title("来源标题")
        .sourceName("媒体")
        .url("https://example.com/a")
        .summary("摘要")
        .build());

    store.writeChildren(brief, summary, sources);

    assertThat(brief.getOverview()).isEqualTo("概览");
    assertThat(brief.getCoreThesis()).isEqualTo("论点");
    assertThat(brief.getCoreEvent()).isEqualTo("事件");
    assertThat(brief.getNarrativeShape()).isEqualTo("叙事");
    assertThat(brief.getConfirmationNote()).isEqualTo("备注");

    ArgumentCaptor<HotnewsBriefItemEntity> itemCaptor =
        ArgumentCaptor.forClass(HotnewsBriefItemEntity.class);
    verify(itemMapper, org.mockito.Mockito.times(4)).insert(itemCaptor.capture());
    assertThat(itemCaptor.getAllValues())
        .extracting(HotnewsBriefItemEntity::getItemType)
        .containsExactly(
            HotnewsBriefItemTypes.CONFIRMED_FACT,
            HotnewsBriefItemTypes.READER_IMPACT,
            HotnewsBriefItemTypes.RECOMMENDED_ANGLE,
            HotnewsBriefItemTypes.UNKNOWN);
    assertThat(itemCaptor.getAllValues())
        .extracting(HotnewsBriefItemEntity::getContent)
        .containsExactly("事实A", "影响B", "角度C", "未知D");

    ArgumentCaptor<HotnewsBriefSourceEntity> sourceCaptor =
        ArgumentCaptor.forClass(HotnewsBriefSourceEntity.class);
    verify(sourceMapper).insert(sourceCaptor.capture());
    assertThat(sourceCaptor.getValue().getRowId()).isEqualTo("row-1");
    assertThat(sourceCaptor.getValue().getUrl()).isEqualTo("https://example.com/a");

    when(itemMapper.selectList(any()))
        .thenReturn(List.of(item("confirmed_fact", "事实A")))
        .thenReturn(List.of(item("reader_impact", "影响B")))
        .thenReturn(List.of(item("recommended_angle", "角度C")))
        .thenReturn(List.of(item("unknown", "未知D")));
    when(sourceMapper.selectList(any()))
        .thenReturn(List.of(sourceCaptor.getValue()));

    Map<String, Object> readSummary = store.readSummary(brief);
    assertThat(readSummary.get("overview")).isEqualTo("概览");
    assertThat(readSummary.get("confirmedFacts")).isEqualTo(List.of("事实A"));
    assertThat(readSummary.get("unknowns")).isEqualTo(List.of("未知D"));
    assertThat(readSummary.get("narrativeShape")).isEqualTo("叙事");

    List<HotNewsBriefSourceRow> readSources = store.readSources("b1");
    assertThat(readSources).hasSize(1);
    assertThat(readSources.get(0).getTitle()).isEqualTo("来源标题");
    assertThat(readSources.get(0).getSourceItemId()).isEqualTo("si-1");
  }

  private static HotnewsBriefItemEntity item(String type, String content) {
    return HotnewsBriefItemEntity.builder()
        .briefId("b1")
        .itemType(type)
        .seq(0)
        .content(content)
        .build();
  }
}
