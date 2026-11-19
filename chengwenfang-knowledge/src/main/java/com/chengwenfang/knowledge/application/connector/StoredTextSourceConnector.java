package com.chengwenfang.knowledge.application.connector;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeSourceConnector;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 已落库归一化文本的来源连接器基类（雷达条目 / 调研 / 转写登记后读 text_file）。
 */
@RequiredArgsConstructor
public abstract class StoredTextSourceConnector implements KnowledgeSourceConnector {

  /** 文档 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;
  /** 文件门面。 */
  private final FileFacade fileFacade;
  /** 本连接器支持的来源种类。 */
  private final KnowledgeSourceKind supportedKind;

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeSourceKind sourceKind) {
    return supportedKind == sourceKind;
  }

  /** {@inheritDoc} */
  @Override
  public KnowledgeIngestDraft load(String sourceRefId) {
    if (StrUtil.isBlank(sourceRefId)) {
      return null;
    }
    KnowledgeDocumentEntity document = documentMapper.selectOne(
        new LambdaQueryWrapper<KnowledgeDocumentEntity>()
            .eq(KnowledgeDocumentEntity::getSourceKind, supportedKind.getValue())
            .eq(KnowledgeDocumentEntity::getSourceRefId, sourceRefId)
            .last("LIMIT 1"));
    if (document == null) {
      return null;
    }
    String rawText = null;
    if (StrUtil.isNotBlank(document.getTextFileId())) {
      rawText = fileFacade.readText(document.getTextFileId());
    }
    KnowledgeMediaKind mediaKind = KnowledgeMediaKind.from(document.getMediaKind());
    return KnowledgeIngestDraft.builder()
        .sourceKind(supportedKind)
        .sourceRefId(document.getSourceRefId())
        .mediaKind(mediaKind)
        .title(StrUtil.nullToEmpty(document.getTitle()))
        .themeId(document.getThemeId())
        .contentHash(document.getContentHash())
        .sourceVersionStamp(document.getSourceVersionStamp())
        .deepLink(document.getDeepLink())
        .rawText(rawText)
        .originFileId(document.getOriginFileId())
        .build();
  }

  /** {@inheritDoc} */
  @Override
  public List<String> listForReindex() {
    List<KnowledgeDocumentEntity> rows = documentMapper.selectList(
        new LambdaQueryWrapper<KnowledgeDocumentEntity>()
            .eq(KnowledgeDocumentEntity::getSourceKind, supportedKind.getValue())
            .select(KnowledgeDocumentEntity::getSourceRefId));
    List<String> sourceRefIds = new ArrayList<>();
    for (KnowledgeDocumentEntity row : rows) {
      if (row != null && StrUtil.isNotBlank(row.getSourceRefId())) {
        sourceRefIds.add(row.getSourceRefId());
      }
    }
    return sourceRefIds;
  }
}
