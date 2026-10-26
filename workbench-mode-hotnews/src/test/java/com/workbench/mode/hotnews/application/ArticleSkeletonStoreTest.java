package com.workbench.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.StoredFileView;
import com.workbench.mode.hotnews.domain.entity.HotnewsArticleSkeletonEntity;
import com.workbench.mode.hotnews.domain.entity.HotnewsArticleSkeletonItemEntity;
import com.workbench.mode.hotnews.facade.constant.HotnewsSkeletonItemTypes;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.workbench.mode.hotnews.infrastructure.mapper.HotnewsArticleSkeletonItemMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.HotnewsArticleSkeletonMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ArticleSkeletonStore} 列覆盖 + 列表组装。
 */
@ExtendWith(MockitoExtension.class)
class ArticleSkeletonStoreTest {

  @Mock
  private HotnewsArticleSkeletonMapper skeletonMapper;
  @Mock
  private HotnewsArticleSkeletonItemMapper itemMapper;
  @Mock
  private FileFacade files;

  private ArticleSkeletonStore store;
  private HotNewsCodec codec;

  @BeforeEach
  void setUp() {
    codec = new HotNewsCodec(new ObjectMapper());
    store = new ArticleSkeletonStore(skeletonMapper, itemMapper, files, codec);
  }

  @Test
  void write_persists_raw_file_and_list_items() {
    when(skeletonMapper.selectById("t1")).thenReturn(null);
    when(files.writeText(anyString(), anyString(), anyString()))
        .thenReturn(StoredFileView.builder().id("file-1").build());

    HotNewsArticleSkeletonView skeleton = HotNewsArticleSkeletonView.builder()
        .topicId("t1")
        .workingTitle("工作标题")
        .scope("范围")
        .coreThesis("论点")
        .coreEvent("事件")
        .keyInsights(List.of("洞察1"))
        .impacts(List.of("影响1"))
        .readerSignals(List.of("信号1"))
        .narrativeShape("叙事")
        .sectionPlan(List.of("第一节"))
        .illustrationStyle("示意图")
        .coverBrief("封面")
        .imagePromptRequirements("生图要求")
        .recommendedWordCountOption("2500-4000")
        .recommendedImageCountOption("2-3")
        .architecture(List.of("架构"))
        .flow(List.of("开场"))
        .referenceHints(List.of("参考"))
        .build();

    store.write("t1", skeleton);

    verify(files).writeText(
        eq("hotnews/topics/t1/skeleton-raw.json"),
        eq("skeleton-raw.json"),
        anyString());
    ArgumentCaptor<HotnewsArticleSkeletonEntity> rowCaptor =
        ArgumentCaptor.forClass(HotnewsArticleSkeletonEntity.class);
    verify(skeletonMapper).insert(rowCaptor.capture());
    assertThat(rowCaptor.getValue().getRawFileId()).isEqualTo("file-1");
    assertThat(rowCaptor.getValue().getWorkingTitle()).isEqualTo("工作标题");

    ArgumentCaptor<HotnewsArticleSkeletonItemEntity> itemCaptor =
        ArgumentCaptor.forClass(HotnewsArticleSkeletonItemEntity.class);
    verify(itemMapper, org.mockito.Mockito.atLeast(7)).insert(itemCaptor.capture());
    assertThat(itemCaptor.getAllValues())
        .extracting(HotnewsArticleSkeletonItemEntity::getItemType)
        .contains(
            HotnewsSkeletonItemTypes.KEY_INSIGHT,
            HotnewsSkeletonItemTypes.IMPACT,
            HotnewsSkeletonItemTypes.READER_SIGNAL,
            HotnewsSkeletonItemTypes.SECTION_PLAN,
            HotnewsSkeletonItemTypes.ARCHITECTURE,
            HotnewsSkeletonItemTypes.FLOW,
            HotnewsSkeletonItemTypes.REFERENCE_HINT);
  }

  @Test
  void read_overlays_columns_and_lists_over_raw_file() {
    when(skeletonMapper.selectById("t1")).thenReturn(HotnewsArticleSkeletonEntity.builder()
        .topicId("t1")
        .rawFileId("file-1")
        .workingTitle("列标题")
        .scope("列范围")
        .coreThesis("列论点")
        .coreEvent("列事件")
        .narrativeShape("列叙事")
        .illustrationStyle("列风格")
        .coverBrief("列封面")
        .imagePromptRequirements("列生图")
        .recommendedWordCountOption("2500-4000")
        .recommendedImageCountOption("2-3")
        .updatedAtText("2026-01-01T00:00:00")
        .build());
    when(files.readText("file-1")).thenReturn(
        "{\"workingTitle\":\"文件标题\",\"modules\":[{\"name\":\"事实\"}],\"keyInsights\":[\"旧洞察\"]}");
    when(itemMapper.selectList(any()))
        .thenReturn(List.of(item(HotnewsSkeletonItemTypes.KEY_INSIGHT, "新洞察")))
        .thenReturn(List.of(item(HotnewsSkeletonItemTypes.IMPACT, "影响")))
        .thenReturn(List.of())
        .thenReturn(List.of(item(HotnewsSkeletonItemTypes.SECTION_PLAN, "计划")))
        .thenReturn(List.of(item(HotnewsSkeletonItemTypes.ARCHITECTURE, "架构")))
        .thenReturn(List.of(item(HotnewsSkeletonItemTypes.FLOW, "流程")))
        .thenReturn(List.of(item(HotnewsSkeletonItemTypes.REFERENCE_HINT, "参考")));

    HotNewsArticleSkeletonView view = store.read("t1");

    assertThat(view).isNotNull();
    assertThat(view.getWorkingTitle()).isEqualTo("列标题");
    assertThat(view.getScope()).isEqualTo("列范围");
    assertThat(view.getKeyInsights()).containsExactly("新洞察");
    assertThat(view.getImpacts()).containsExactly("影响");
    assertThat(view.getModules()).hasSize(1);
    assertThat(view.getRecommendedWordCountOption()).isEqualTo("2500-4000");
  }

  private static HotnewsArticleSkeletonItemEntity item(String type, String content) {
    return HotnewsArticleSkeletonItemEntity.builder()
        .topicId("t1")
        .itemType(type)
        .seq(0)
        .content(content)
        .build();
  }
}
