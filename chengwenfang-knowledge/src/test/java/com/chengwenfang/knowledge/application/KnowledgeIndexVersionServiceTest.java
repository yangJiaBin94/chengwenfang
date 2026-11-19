package com.chengwenfang.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.knowledge.domain.entity.KnowledgeIndexStateEntity;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeIndexStateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 索引版本 next / promote 单测。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeIndexVersionServiceTest {

  @Mock
  private KnowledgeIndexStateMapper indexStateMapper;

  private KnowledgeIndexVersionService versionService;

  @BeforeEach
  void setUp() {
    versionService = new KnowledgeIndexVersionService(indexStateMapper);
  }

  @Test
  void nextVersionOfIncrementsNumericSuffix() {
    assertThat(versionService.nextVersionOf("v1")).isEqualTo("v2");
    assertThat(versionService.nextVersionOf("v9")).isEqualTo("v10");
  }

  @Test
  void allocateNextVersionUsesActive() {
    when(indexStateMapper.selectById(1)).thenReturn(
        KnowledgeIndexStateEntity.builder().id(1).activeVersion("v3").build());
    assertThat(versionService.allocateNextVersion()).isEqualTo("v4");
  }

  @Test
  void beginNextWritingVersionPersistsAndReuses() {
    when(indexStateMapper.selectById(1)).thenReturn(
        KnowledgeIndexStateEntity.builder().id(1).activeVersion("v1").writingVersion(null).build());
    assertThat(versionService.beginNextWritingVersion()).isEqualTo("v2");
    ArgumentCaptor<KnowledgeIndexStateEntity> captor =
        ArgumentCaptor.forClass(KnowledgeIndexStateEntity.class);
    verify(indexStateMapper).updateById(captor.capture());
    assertThat(captor.getValue().getWritingVersion()).isEqualTo("v2");

    when(indexStateMapper.selectById(1)).thenReturn(
        KnowledgeIndexStateEntity.builder().id(1).activeVersion("v1").writingVersion("v2").build());
    assertThat(versionService.beginNextWritingVersion()).isEqualTo("v2");
  }

  @Test
  void promoteActiveVersionClearsWriting() {
    when(indexStateMapper.selectById(1)).thenReturn(
        KnowledgeIndexStateEntity.builder().id(1).activeVersion("v1").writingVersion("v2").build());
    versionService.promoteActiveVersion("v2");
    ArgumentCaptor<KnowledgeIndexStateEntity> captor =
        ArgumentCaptor.forClass(KnowledgeIndexStateEntity.class);
    verify(indexStateMapper).updateById(captor.capture());
    assertThat(captor.getValue().getActiveVersion()).isEqualTo("v2");
    assertThat(captor.getValue().getWritingVersion()).isNull();
  }

  @Test
  void promoteActiveVersionUpdatesRow() {
    when(indexStateMapper.selectById(1)).thenReturn(
        KnowledgeIndexStateEntity.builder().id(1).activeVersion("v1").build());
    versionService.promoteActiveVersion("v2");
    ArgumentCaptor<KnowledgeIndexStateEntity> captor =
        ArgumentCaptor.forClass(KnowledgeIndexStateEntity.class);
    verify(indexStateMapper).updateById(captor.capture());
    assertThat(captor.getValue().getActiveVersion()).isEqualTo("v2");
  }

  @Test
  void promoteInsertsWhenMissing() {
    when(indexStateMapper.selectById(1)).thenReturn(null);
    versionService.promoteActiveVersion("v5");
    verify(indexStateMapper).insert(any(KnowledgeIndexStateEntity.class));
  }

  @Test
  void activeAndNextDifferForBlueGreen() {
    when(indexStateMapper.selectById(1)).thenReturn(
        KnowledgeIndexStateEntity.builder().id(1).activeVersion("v1").build());
    assertThat(versionService.activeVersion()).isEqualTo("v1");
    assertThat(versionService.allocateNextVersion()).isEqualTo("v2");
  }

  @Test
  void promoteActiveVersionRejectsBlank() {
    assertThatThrownBy(() -> versionService.promoteActiveVersion("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }
}
