package com.chengwenfang.knowledge.infrastructure.assembler;

import com.chengwenfang.knowledge.domain.entity.KnowledgeChatSessionEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatSessionView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeDocumentView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;

/**
 * 知识库实体 ↔ 视图组装。
 */
public final class KnowledgeViewAssembler {

  private KnowledgeViewAssembler() {}

  /**
   * 文档实体转视图。
   *
   * @param entity 实体
   * @return 视图
   */
  public static KnowledgeDocumentView toDocumentView(KnowledgeDocumentEntity entity) {
    if (entity == null) {
      return null;
    }
    return KnowledgeDocumentView.builder()
        .id(entity.getId())
        .sourceKind(KnowledgeSourceKind.from(entity.getSourceKind()))
        .sourceRefId(entity.getSourceRefId())
        .mediaKind(KnowledgeMediaKind.from(entity.getMediaKind()))
        .title(entity.getTitle())
        .themeId(entity.getThemeId())
        .status(KnowledgeDocumentStatus.from(entity.getStatus()))
        .chunkCount(entity.getChunkCount())
        .contentHash(entity.getContentHash())
        .sourceVersionStamp(entity.getSourceVersionStamp())
        .deepLink(entity.getDeepLink())
        .originFileId(entity.getOriginFileId())
        .textFileId(entity.getTextFileId())
        .errorMessage(entity.getErrorMessage())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /**
   * 会话实体转视图。
   *
   * @param entity 实体
   * @return 视图
   */
  public static KnowledgeChatSessionView toSessionView(KnowledgeChatSessionEntity entity) {
    if (entity == null) {
      return null;
    }
    return KnowledgeChatSessionView.builder()
        .id(entity.getId())
        .title(entity.getTitle())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
