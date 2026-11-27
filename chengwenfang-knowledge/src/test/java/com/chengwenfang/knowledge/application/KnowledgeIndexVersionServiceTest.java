package com.chengwenfang.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.knowledge.domain.entity.KnowledgeIndexStateEntity;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeIndexStateMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 索引版本 next / promote 单测（按用户）。 */
@ExtendWith(MockitoExtension.class)
class KnowledgeIndexVersionServiceTest {

  private static final String OWNER_A = "owner-a";

  @Mock
  private KnowledgeIndexStateMapper indexStateMapper;

  @Mock
  private OwnerScope ownerScope;

  private KnowledgeIndexVersionService versionService;

  @BeforeEach
  void setUp() {
    versionService = new KnowledgeIndexVersionService(indexStateMapper, ownerScope);
    lenient().when(ownerScope.requireOwnerUserIdForWrite()).thenReturn(Optional.of(OWNER_A));
  }

  @Test
  void nextVersionOfIncrementsNumericSuffix() {
    assertThat(versionService.nextVersionOf("v1")).isEqualTo("v2");
    assertThat(versionService.nextVersionOf("v9")).isEqualTo("v10");
  }

  @Test
  void beginNextWritingVersionPersistsAndReuses() {
    when(indexStateMapper.selectById(OWNER_A)).thenReturn(
        KnowledgeIndexStateEntity.builder()
            .ownerUserId(OWNER_A)
            .activeVersion("v1")
            .writingVersion(null)
            .build());
    assertThat(versionService.beginNextWritingVersion()).isEqualTo("v2");
    ArgumentCaptor<KnowledgeIndexStateEntity> captor =
        ArgumentCaptor.forClass(KnowledgeIndexStateEntity.class);
    verify(indexStateMapper).updateById(captor.capture());
    assertThat(captor.getValue().getWritingVersion()).isEqualTo("v2");
    assertThat(captor.getValue().getOwnerUserId()).isEqualTo(OWNER_A);

    when(indexStateMapper.selectById(OWNER_A)).thenReturn(
        KnowledgeIndexStateEntity.builder()
            .ownerUserId(OWNER_A)
            .activeVersion("v1")
            .writingVersion("v2")
            .build());
    assertThat(versionService.beginNextWritingVersion()).isEqualTo("v2");
  }

  @Test
  void promoteActiveVersionClearsWriting() {
    when(indexStateMapper.selectById(OWNER_A)).thenReturn(
        KnowledgeIndexStateEntity.builder()
            .ownerUserId(OWNER_A)
            .activeVersion("v1")
            .writingVersion("v2")
            .build());
    versionService.promoteActiveVersion("v2");
    ArgumentCaptor<KnowledgeIndexStateEntity> captor =
        ArgumentCaptor.forClass(KnowledgeIndexStateEntity.class);
    verify(indexStateMapper).updateById(captor.capture());
    assertThat(captor.getValue().getActiveVersion()).isEqualTo("v2");
    assertThat(captor.getValue().getWritingVersion()).isNull();
  }

  @Test
  void promoteActiveVersionUpdatesRow() {
    when(indexStateMapper.selectById(OWNER_A)).thenReturn(
        KnowledgeIndexStateEntity.builder().ownerUserId(OWNER_A).activeVersion("v1").build());
    versionService.promoteActiveVersion("v2");
    ArgumentCaptor<KnowledgeIndexStateEntity> captor =
        ArgumentCaptor.forClass(KnowledgeIndexStateEntity.class);
    verify(indexStateMapper).updateById(captor.capture());
    assertThat(captor.getValue().getActiveVersion()).isEqualTo("v2");
  }

  @Test
  void promoteInsertsWhenMissing() {
    when(indexStateMapper.selectById(OWNER_A)).thenReturn(null);
    versionService.promoteActiveVersion("v5");
    ArgumentCaptor<KnowledgeIndexStateEntity> captor =
        ArgumentCaptor.forClass(KnowledgeIndexStateEntity.class);
    verify(indexStateMapper).insert(captor.capture());
    assertThat(captor.getValue().getOwnerUserId()).isEqualTo(OWNER_A);
    assertThat(captor.getValue().getActiveVersion()).isEqualTo("v5");
  }

  @Test
  void activeAndNextDifferForBlueGreen() {
    when(indexStateMapper.selectById(OWNER_A)).thenReturn(
        KnowledgeIndexStateEntity.builder().ownerUserId(OWNER_A).activeVersion("v1").build());
    assertThat(versionService.activeVersion()).isEqualTo("v1");
    assertThat(versionService.nextVersionOf(versionService.activeVersion())).isEqualTo("v2");
  }

  @Test
  void promoteActiveVersionRejectsBlank() {
    assertThatThrownBy(() -> versionService.promoteActiveVersion("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @Test
  void resolveOwnerIdUsesOwnerScope() {
    assertThat(versionService.resolveOwnerId()).isEqualTo(OWNER_A);
  }
}
