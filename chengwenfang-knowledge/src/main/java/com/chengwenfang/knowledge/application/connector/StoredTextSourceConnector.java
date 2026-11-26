package com.chengwenfang.knowledge.application.connector;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeSourceConnector;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 已落库归一化文本的来源连接器基类（雷达条目 / 调研 / 转写登记后读 text_file）。
 */
public abstract class StoredTextSourceConnector implements KnowledgeSourceConnector {

  /** 文档 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;
  /** 文件门面。 */
  private final FileFacade fileFacade;
  /** 用户数据范围。 */
  private final OwnerScope ownerScope;
  /** 本连接器支持的来源种类。 */
  private final KnowledgeSourceKind supportedKind;

  /**
   * @param documentMapper 文档 Mapper
   * @param fileFacade     文件门面
   * @param ownerScope     用户数据范围
   * @param supportedKind  来源种类
   */
  protected StoredTextSourceConnector(
      KnowledgeDocumentMapper documentMapper,
      FileFacade fileFacade,
      OwnerScope ownerScope,
      KnowledgeSourceKind supportedKind) {
    this.documentMapper = documentMapper;
    this.fileFacade = fileFacade;
    this.ownerScope = ownerScope;
    this.supportedKind = supportedKind;
  }

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
    LambdaQueryWrapper<KnowledgeDocumentEntity> query = new LambdaQueryWrapper<KnowledgeDocumentEntity>()
        .eq(KnowledgeDocumentEntity::getSourceKind, supportedKind.getValue())
        .eq(KnowledgeDocumentEntity::getSourceRefId, sourceRefId)
        .last("LIMIT 1");
    applyDocumentOwnerFilter(query);
    KnowledgeDocumentEntity document = documentMapper.selectOne(query);
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
    LambdaQueryWrapper<KnowledgeDocumentEntity> query = new LambdaQueryWrapper<KnowledgeDocumentEntity>()
        .eq(KnowledgeDocumentEntity::getSourceKind, supportedKind.getValue())
        .select(KnowledgeDocumentEntity::getSourceRefId);
    applyDocumentOwnerFilter(query);
    List<KnowledgeDocumentEntity> rows = documentMapper.selectList(query);
    List<String> sourceRefIds = new ArrayList<>();
    for (KnowledgeDocumentEntity row : rows) {
      if (row != null && StrUtil.isNotBlank(row.getSourceRefId())) {
        sourceRefIds.add(row.getSourceRefId());
      }
    }
    return sourceRefIds;
  }

  /**
   * 非管理员按当前用户过滤文档；无身份时失败关闭（0 行）。
   *
   * @param query 文档查询
   */
  private void applyDocumentOwnerFilter(LambdaQueryWrapper<KnowledgeDocumentEntity> query) {
    if (ownerScope.currentUserIsAdmin()) {
      return;
    }
    String ownerId = ownerScope.currentOwnerUserId().orElse(null);
    if (StrUtil.isBlank(ownerId)) {
      query.apply("1 = 0");
      return;
    }
    query.eq(KnowledgeDocumentEntity::getOwnerUserId, ownerId);
  }
}
