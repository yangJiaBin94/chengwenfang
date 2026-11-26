package com.chengwenfang.knowledge.application.connector;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.identity.facade.OwnerScope;
import com.chengwenfang.knowledge.application.ingest.KnowledgeUploadMediaResolver;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeSourceConnector;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 手动上传来源连接器：优先读归一化 text_file，否则读 origin 文件。
 */
@Component
@RequiredArgsConstructor
public class ManualUploadSourceConnector implements KnowledgeSourceConnector {

  /** 文档 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;
  /** 文件门面。 */
  private final FileFacade fileFacade;
  /** 用户数据范围。 */
  private final OwnerScope ownerScope;

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeSourceKind sourceKind) {
    return KnowledgeSourceKind.MANUAL_UPLOAD == sourceKind;
  }

  /** {@inheritDoc} */
  @Override
  public KnowledgeIngestDraft load(String sourceRefId) {
    if (StrUtil.isBlank(sourceRefId)) {
      return null;
    }
    LambdaQueryWrapper<KnowledgeDocumentEntity> query = new LambdaQueryWrapper<KnowledgeDocumentEntity>()
        .eq(KnowledgeDocumentEntity::getSourceKind, KnowledgeSourceKind.MANUAL_UPLOAD.getValue())
        .eq(KnowledgeDocumentEntity::getSourceRefId, sourceRefId)
        .last("LIMIT 1");
    applyDocumentOwnerFilter(query);
    KnowledgeDocumentEntity document = documentMapper.selectOne(query);
    if (document == null) {
      return loadFromFileOnly(sourceRefId);
    }
    return toDraft(document);
  }

  /** {@inheritDoc} */
  @Override
  public List<String> listForReindex() {
    LambdaQueryWrapper<KnowledgeDocumentEntity> query = new LambdaQueryWrapper<KnowledgeDocumentEntity>()
        .eq(KnowledgeDocumentEntity::getSourceKind, KnowledgeSourceKind.MANUAL_UPLOAD.getValue())
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

  private KnowledgeIngestDraft toDraft(KnowledgeDocumentEntity document) {
    KnowledgeMediaKind mediaKind = KnowledgeMediaKind.from(document.getMediaKind());
    String rawText = null;
    if (StrUtil.isNotBlank(document.getTextFileId())) {
      rawText = fileFacade.readText(document.getTextFileId());
    } else if (isPlainFile(mediaKind) && StrUtil.isNotBlank(document.getOriginFileId())) {
      byte[] bytes = fileFacade.getBytes(document.getOriginFileId());
      rawText = new String(bytes, StandardCharsets.UTF_8);
    }
    return KnowledgeIngestDraft.builder()
        .sourceKind(KnowledgeSourceKind.MANUAL_UPLOAD)
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

  private KnowledgeIngestDraft loadFromFileOnly(String fileId) {
    FileView meta = fileFacade.get(fileId);
    KnowledgeMediaKind mediaKind = KnowledgeUploadMediaResolver.resolveMediaKind(
        meta.getFileName(),
        meta.getSizeBytes() == null ? 1L : meta.getSizeBytes());
    String rawText = null;
    if (isPlainFile(mediaKind)) {
      rawText = new String(fileFacade.getBytes(fileId), StandardCharsets.UTF_8);
    }
    return KnowledgeIngestDraft.builder()
        .sourceKind(KnowledgeSourceKind.MANUAL_UPLOAD)
        .sourceRefId(fileId)
        .mediaKind(mediaKind)
        .title(StrUtil.blankToDefault(meta.getFileName(), fileId))
        .rawText(rawText)
        .originFileId(fileId)
        .build();
  }

  private static boolean isPlainFile(KnowledgeMediaKind mediaKind) {
    return KnowledgeMediaKind.ARTICLE_MARKDOWN == mediaKind
        || KnowledgeMediaKind.PLAIN_TEXT == mediaKind
        || KnowledgeMediaKind.WEB_PAGE == mediaKind
        || KnowledgeMediaKind.VIDEO == mediaKind;
  }
}
