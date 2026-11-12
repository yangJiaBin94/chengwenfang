package com.chengwenfang.content.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.content.domain.entity.MediaAssetEntity;
import com.chengwenfang.content.domain.entity.WorkOutputEntity;
import com.chengwenfang.content.facade.constant.MediaOrigins;
import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import com.chengwenfang.content.infrastructure.mapper.MediaAssetMapper;
import com.chengwenfang.content.infrastructure.mapper.MediaGenerationMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputMapper;
import com.chengwenfang.file.facade.FileFacade;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link GeneratedImageService#listImages} 版本过滤与全量语义。 */
@ExtendWith(MockitoExtension.class)
class GeneratedImageServiceListImagesTest {

  @Mock MediaAssetMapper mediaAssetMapper;
  @Mock MediaGenerationMapper mediaGenerationMapper;
  @Mock WorkOutputMapper workOutputMapper;
  @Mock WorkContentStoreImpl workContents;
  @Mock FileFacade files;
  @Mock ContentViewMapper views;
  @Mock ThemeVisibilityGuard visibility;

  private GeneratedImageService service;

  @BeforeEach
  void setUp() {
    service = new GeneratedImageService(
        mediaAssetMapper,
        mediaGenerationMapper,
        workOutputMapper,
        workContents,
        files,
        views,
        visibility);
  }

  @Test
  void listImages_withoutVersionNo_returnsFullCatalogForOutput() {
    WorkOutputEntity output = WorkOutputEntity.builder().id("out-1").workId("work-1").build();
    when(workOutputMapper.selectById("out-1")).thenReturn(output);
    MediaAssetEntity older = asset("old-cover", 0);
    MediaAssetEntity newer = asset("new-cover", 0);
    when(mediaAssetMapper.selectList(org.mockito.ArgumentMatchers.<LambdaQueryWrapper<MediaAssetEntity>>any()))
        .thenReturn(List.of(newer, older));
    when(mediaGenerationMapper.selectByIds(anyList())).thenReturn(List.of());
    when(views.generatedMediaAssetView(older, null))
        .thenReturn(GeneratedImageView.builder().id("old-cover").build());
    when(views.generatedMediaAssetView(newer, null))
        .thenReturn(GeneratedImageView.builder().id("new-cover").build());

    List<GeneratedImageView> result = service.listImages("work-1", "out-1", null);

    assertEquals(List.of("new-cover", "old-cover"), result.stream().map(GeneratedImageView::getId).toList());
  }

  @Test
  void listImages_withVersionNo_filtersAndOrdersByMarkdownRefs() {
    WorkOutputEntity output = WorkOutputEntity.builder().id("out-1").workId("work-1").build();
    when(workOutputMapper.selectById("out-1")).thenReturn(output);
    when(workContents.readVersion("out-1", 3))
        .thenReturn("![](/api/images/cover-b)\n\n![](/api/images/inline-2)\n");
    MediaAssetEntity coverB = asset("cover-b", 0);
    MediaAssetEntity inline2 = asset("inline-2", 2);
    MediaAssetEntity orphan = asset("cover-a", 0);
    when(mediaAssetMapper.selectByIds(List.of("cover-b", "inline-2")))
        .thenReturn(List.of(orphan, inline2, coverB));
    when(mediaGenerationMapper.selectByIds(anyList())).thenReturn(List.of());
    when(views.generatedMediaAssetView(coverB, null))
        .thenReturn(GeneratedImageView.builder().id("cover-b").build());
    when(views.generatedMediaAssetView(inline2, null))
        .thenReturn(GeneratedImageView.builder().id("inline-2").build());

    List<GeneratedImageView> result = service.listImages("work-1", "out-1", 3);

    assertEquals(List.of("cover-b", "inline-2"), result.stream().map(GeneratedImageView::getId).toList());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> idsCaptor = ArgumentCaptor.forClass(List.class);
    verify(mediaAssetMapper).selectByIds(idsCaptor.capture());
    assertEquals(List.of("cover-b", "inline-2"), idsCaptor.getValue());
  }

  /**
   * 构造生成类媒资。
   *
   * @param id         资产 id
   * @param sequenceNo 序号
   * @return 实体
   */
  private static MediaAssetEntity asset(String id, int sequenceNo) {
    return MediaAssetEntity.builder()
        .id(id)
        .workId("work-1")
        .outputId("out-1")
        .origin(MediaOrigins.GENERATED)
        .sequenceNo(sequenceNo)
        .build();
  }
}
