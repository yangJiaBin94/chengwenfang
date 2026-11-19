package com.chengwenfang.knowledge.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.facade.AiEmbeddingFacade;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkParams;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorUpsert;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.domain.rule.ChunkParamsResolver;
import com.chengwenfang.knowledge.domain.rule.KnowledgeIndexSkipPolicy;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkStrategyVersions;
import com.chengwenfang.knowledge.facade.constant.KnowledgeGraphConstants;
import com.chengwenfang.knowledge.facade.constant.KnowledgeIngestConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeChunker;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import com.chengwenfang.knowledge.facade.spi.KnowledgeSourceConnector;
import com.chengwenfang.knowledge.facade.spi.KnowledgeTextExtractor;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 单文档索引流水线：抽取 → 切块 → 嵌入 → 落库与向量索引。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexPipeline {

  /** 文档表 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;
  /** 切块表 Mapper。 */
  private final KnowledgeChunkMapper chunkMapper;
  /** 嵌入门面。 */
  private final AiEmbeddingFacade embeddingFacade;
  /** 向量存储。 */
  private final KnowledgeVectorStore vectorStore;
  /** 来源连接器列表。 */
  private final List<KnowledgeSourceConnector> sourceConnectors;
  /** 正文抽取器列表。 */
  private final List<KnowledgeTextExtractor> textExtractors;
  /** 切块策略列表（按 chunkProfile 路由）。 */
  private final List<KnowledgeChunker> chunkers;
  /** 切块滑窗参数解析。 */
  private final ChunkParamsResolver chunkParamsResolver;
  /** 文件门面（回写归一化正文）。 */
  private final FileFacade fileFacade;
  /** 活跃/写入索引版本。 */
  private final KnowledgeIndexVersionService indexVersionService;
  /** 图存储（flag 关闭时为 NoOp）。 */
  private final KnowledgeGraphStore graphStore;
  /** 失败状态独立落库。 */
  private final KnowledgeDocumentStatusWriter documentStatusWriter;
  /** 图边旁路按 chunkIndex 回放。 */
  private final KnowledgeGraphEdgeOverrideReplay edgeOverrideReplay;

  /**
   * 对指定文档执行索引（默认写 active；全量蓝绿窗口内改写 writing，避免切流回退）。
   *
   * @param documentId 文档主键
   */
  @Transactional
  public void indexDocument(String documentId) {
    indexDocument(documentId, resolveIncrementalWriteVersion());
  }

  /**
   * 增量索引用版本：状态表有 writing_version 时改写 writing（覆盖全量首包窗口）。
   *
   * @return 写入版本
   */
  private String resolveIncrementalWriteVersion() {
    String writing = indexVersionService.writingVersion();
    if (StrUtil.isNotBlank(writing)) {
      return writing;
    }
    return indexVersionService.activeVersion();
  }

  /**
   * 对指定文档执行索引（幂等跳过、失败写 FAILED）。
   *
   * @param documentId   文档主键
   * @param indexVersion 写入的索引版本（增量用 active；全量重建用 writing）
   */
  @Transactional
  public void indexDocument(String documentId, String indexVersion) {
    KnowledgeDocumentEntity document = documentMapper.selectById(documentId);
    if (document == null) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_DOCUMENT_NOT_FOUND,
          "知识库文档不存在",
          404);
    }
    String writeVersion = StrUtil.blankToDefault(indexVersion, indexVersionService.activeVersion());
    String activeVersion = indexVersionService.activeVersion();
    boolean blueGreenWrite = !StrUtil.equals(writeVersion, activeVersion);
    try {
      KnowledgeIngestDraft draft = buildDraft(document);
      KnowledgeMediaKind mediaKind = KnowledgeMediaKind.from(document.getMediaKind());
      KnowledgeTextExtractor extractor = resolveExtractor(mediaKind);
      NormalizedText normalized = extractor.extract(draft);
      assertExtractedText(normalized);
      // 以归一化正文为准计算哈希，避免解析器升级后被 origin 哈希误 skip
      String contentHash = DigestUtil.sha256Hex(StrUtil.nullToEmpty(normalized.getText()));
      // 蓝绿写下一版时不可 skip：否则切流后文档在新 active 下无切块
      if (!blueGreenWrite) {
        Long persisted = chunkMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeChunkEntity>()
                .eq(KnowledgeChunkEntity::getDocumentId, documentId)
                .eq(KnowledgeChunkEntity::getIndexVersion, activeVersion));
        int persistedChunkCount = persisted == null ? 0 : persisted.intValue();
        if (KnowledgeIndexSkipPolicy.shouldSkip(document, contentHash, persistedChunkCount)) {
          log.info("知识库索引跳过 documentId={}（内容与切块策略未变且切块表一致）", documentId);
          return;
        }
      }
      markIndexing(document);
      persistNormalizedText(document, normalized);
      List<KnowledgeChunkPiece> pieces = chunk(
          mediaKind,
          normalized.getChunkProfile(),
          normalized.getTitle(),
          normalized.getText());
      List<String> embeddingTexts = new ArrayList<>();
      for (KnowledgeChunkPiece piece : pieces) {
        embeddingTexts.add(StrUtil.blankToDefault(piece.getEmbeddingText(), piece.getContent()));
      }
      List<float[]> vectors = embeddingFacade.embed(embeddingTexts);
      if (vectors.size() != pieces.size()) {
        throw new IllegalStateException("嵌入结果数量与切块不一致");
      }
      int dimension = vectors.get(0).length;
      // 只清目标 writeVersion，保留其它版本（读路径仍用 active）
      vectorStore.ensureCollection(dimension);
      // 变更前快照旁路，供写完后按 chunkIndex 回放
      KnowledgeGraphEdgeOverrideReplay.Snapshot overrideSnapshot =
          edgeOverrideReplay.capture(documentId);
      List<KnowledgeChunkEntity> previousSameVersion = chunkMapper.selectList(
          new LambdaQueryWrapper<KnowledgeChunkEntity>()
              .eq(KnowledgeChunkEntity::getDocumentId, documentId)
              .eq(KnowledgeChunkEntity::getIndexVersion, writeVersion));
      List<String> oldChunkIds = CollUtil.isEmpty(previousSameVersion)
          ? List.of()
          : previousSameVersion.stream()
              .map(KnowledgeChunkEntity::getId)
              .filter(StrUtil::isNotBlank)
              .toList();
      chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
          .eq(KnowledgeChunkEntity::getDocumentId, documentId)
          .eq(KnowledgeChunkEntity::getIndexVersion, writeVersion));
      String embeddingModel = embeddingFacade.embeddingModelName();
      String indexedTitle = StrUtil.blankToDefault(normalized.getTitle(), document.getTitle());
      List<KnowledgeVectorUpsert> upserts = new ArrayList<>();
      List<GraphMentionDraft> graphMentions = new ArrayList<>();
      LocalDateTime now = LocalDateTime.now();
      List<String> chunkIdsByIndex = new ArrayList<>();
      for (int index = 0; index < pieces.size(); index++) {
        chunkIdsByIndex.add(IdsUtil.newId());
      }
      for (int index = 0; index < pieces.size(); index++) {
        KnowledgeChunkPiece piece = pieces.get(index);
        String chunkId = chunkIdsByIndex.get(index);
        String parentChunkId = null;
        if (piece.getParentChunkIndex() != null
            && piece.getParentChunkIndex() >= 0
            && piece.getParentChunkIndex() < chunkIdsByIndex.size()) {
          parentChunkId = chunkIdsByIndex.get(piece.getParentChunkIndex());
        }
        KnowledgeChunkEntity chunk = KnowledgeChunkEntity.builder()
            .id(chunkId)
            .documentId(documentId)
            .parentChunkId(parentChunkId)
            .chunkIndex(ObjectUtil.defaultIfNull(piece.getChunkIndex(), index))
            .headingPath(piece.getHeadingPath())
            .charSpanStart(piece.getCharSpanStart())
            .charSpanEnd(piece.getCharSpanEnd())
            .content(piece.getContent())
            .tokenEstimate(ObjectUtil.defaultIfNull(piece.getTokenEstimate(), 0))
            .qdrantPointId(chunkId)
            .embeddingModel(embeddingModel)
            .indexVersion(writeVersion)
            .indexedAt(now)
            .createdAt(now)
            .build();
        chunkMapper.insert(chunk);
        // 图边事务内只攒载荷，提交后再写，避免 MySQL 回滚后 Neo4j 残留
        graphMentions.add(new GraphMentionDraft(
            chunkId,
            indexedTitle,
            piece.getHeadingPath(),
            piece.getContent(),
            writeVersion,
            KnowledgeGraphConstants.DEFAULT_MENTION_CONFIDENCE));
        upserts.add(KnowledgeVectorUpsert.builder()
            .chunkId(chunkId)
            .documentId(documentId)
            .vector(vectors.get(index))
            .sourceKind(document.getSourceKind())
            .sourceRefId(document.getSourceRefId())
            .mediaKind(document.getMediaKind())
            .title(indexedTitle)
            .headingPath(piece.getHeadingPath())
            .chunkIndex(ObjectUtil.defaultIfNull(piece.getChunkIndex(), index))
            .contentHash(contentHash)
            .themeId(document.getThemeId())
            .deepLink(document.getDeepLink())
            .indexVersion(writeVersion)
            .embeddingModel(embeddingModel)
            .content(piece.getContent())
            .build());
      }
      // 旁路表映射在事务内；图 enabled 同步放到提交后（边已存在）
      edgeOverrideReplay.replay(documentId, writeVersion, overrideSnapshot);
      document.setStatus(KnowledgeDocumentStatus.READY.getValue());
      document.setChunkCount(pieces.size());
      document.setContentHash(contentHash);
      document.setChunkStrategyVersion(KnowledgeChunkStrategyVersions.CURRENT);
      document.setErrorMessage(null);
      document.setTitle(indexedTitle);
      document.setUpdatedAt(LocalDateTime.now());
      documentMapper.updateById(document);
      // 向量与图在事务提交后再替换，避免 MySQL 回滚后外部存储已写
      scheduleExternalReplace(documentId, writeVersion, upserts, graphMentions, oldChunkIds);
      log.info("知识库索引完成 documentId={} chunks={}", documentId, pieces.size());
    } catch (BusinessException businessException) {
      markFailed(document, businessException.getMessage());
      throw businessException;
    } catch (Exception exception) {
      String message = StrUtil.blankToDefault(exception.getMessage(), "未知错误");
      markFailed(document, message);
      log.error("知识库索引失败 documentId={} err={}", documentId, message, exception);
      throw new BusinessException(ErrorCodes.KNOWLEDGE_INDEX_FAILED, "知识库索引失败：" + message, 500);
    }
  }

  /**
   * 合并文档行与连接器草稿。
   *
   * @param document 文档实体
   * @return 入库草稿
   */
  private KnowledgeIngestDraft buildDraft(KnowledgeDocumentEntity document) {
    KnowledgeSourceKind sourceKind = KnowledgeSourceKind.from(document.getSourceKind());
    KnowledgeIngestDraft fromConnector = null;
    KnowledgeSourceConnector connector = resolveConnector(sourceKind);
    if (connector != null) {
      fromConnector = connector.load(document.getSourceRefId());
    }
    KnowledgeMediaKind mediaKind = KnowledgeMediaKind.from(document.getMediaKind());
    KnowledgeIngestDraft.KnowledgeIngestDraftBuilder builder = KnowledgeIngestDraft.builder()
        .sourceKind(sourceKind)
        .sourceRefId(document.getSourceRefId())
        .mediaKind(mediaKind)
        .title(document.getTitle())
        .themeId(document.getThemeId())
        .contentHash(document.getContentHash())
        .sourceVersionStamp(document.getSourceVersionStamp())
        .deepLink(document.getDeepLink())
        .originFileId(document.getOriginFileId());
    if (fromConnector != null) {
      if (StrUtil.isNotBlank(fromConnector.getTitle())) {
        builder.title(fromConnector.getTitle());
      }
      if (StrUtil.isNotBlank(fromConnector.getRawText())) {
        builder.rawText(fromConnector.getRawText());
      }
      if (StrUtil.isNotBlank(fromConnector.getContentHash())) {
        builder.contentHash(fromConnector.getContentHash());
      }
      if (StrUtil.isNotBlank(fromConnector.getSourceVersionStamp())) {
        builder.sourceVersionStamp(fromConnector.getSourceVersionStamp());
      }
      if (StrUtil.isNotBlank(fromConnector.getDeepLink())) {
        builder.deepLink(fromConnector.getDeepLink());
      }
      if (StrUtil.isNotBlank(fromConnector.getThemeId())) {
        builder.themeId(fromConnector.getThemeId());
      }
    }
    return builder.build();
  }

  /**
   * 归一化正文过短则失败。
   *
   * @param normalized 归一化结果
   */
  private static void assertExtractedText(NormalizedText normalized) {
    String text = normalized == null ? "" : StrUtil.nullToEmpty(normalized.getText()).trim();
    if (text.length() < KnowledgeIngestConstants.MIN_EXTRACTED_CHARS) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_EXTRACT_EMPTY,
          "抽取正文过短或为空（扫描件暂不支持）",
          400);
    }
  }

  /**
   * 将归一化正文写入 text_file_id，便于重建与引用预览。
   *
   * @param document   文档行
   * @param normalized 归一化正文
   */
  private void persistNormalizedText(KnowledgeDocumentEntity document, NormalizedText normalized) {
    String text = StrUtil.nullToEmpty(normalized.getText());
    String objectKey = "knowledge/normalized/" + document.getId() + ".txt";
    StoredFileView stored = fileFacade.writeText(objectKey, document.getId() + ".txt", text);
    document.setTextFileId(stored.getId());
    documentMapper.updateById(document);
  }

  /**
   * 标记索引中。
   *
   * @param document 文档
   */
  private void markIndexing(KnowledgeDocumentEntity document) {
    document.setStatus(KnowledgeDocumentStatus.INDEXING.getValue());
    document.setErrorMessage(null);
    document.setUpdatedAt(LocalDateTime.now());
    documentMapper.updateById(document);
  }

  /**
   * 标记失败（新事务，避免外层回滚冲掉 FAILED）。
   *
   * @param document 文档
   * @param message  失败原因
   */
  private void markFailed(KnowledgeDocumentEntity document, String message) {
    if (document == null || StrUtil.isBlank(document.getId())) {
      return;
    }
    documentStatusWriter.markFailed(document.getId(), message);
  }

  /**
   * MySQL 提交后再写向量与图；提交前失败则不碰外部存储。
   *
   * @param documentId    文档 ID
   * @param writeVersion  写入版本
   * @param upserts       新向量
   * @param graphMentions 图提及载荷
   * @param oldChunkIds   旧切块点 ID
   */
  private void scheduleExternalReplace(
      String documentId,
      String writeVersion,
      List<KnowledgeVectorUpsert> upserts,
      List<GraphMentionDraft> graphMentions,
      List<String> oldChunkIds) {
    List<KnowledgeVectorUpsert> newUpserts = upserts == null ? List.of() : List.copyOf(upserts);
    List<GraphMentionDraft> mentions =
        graphMentions == null ? List.of() : List.copyOf(graphMentions);
    List<String> removeIds = oldChunkIds == null ? List.of() : List.copyOf(oldChunkIds);
    Runnable replace = () -> replaceExternalAfterCommit(
        documentId, writeVersion, newUpserts, mentions, removeIds);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          replace.run();
        }
      });
      return;
    }
    replace.run();
  }

  /**
   * 提交后：向量 upsert → 删旧点 → 图 upsert → 删旧边 → 旁路 enabled 同步。
   *
   * @param documentId    文档 ID
   * @param writeVersion  写入版本
   * @param upserts       新向量
   * @param graphMentions 图提及
   * @param oldChunkIds   旧点 ID
   */
  private void replaceExternalAfterCommit(
      String documentId,
      String writeVersion,
      List<KnowledgeVectorUpsert> upserts,
      List<GraphMentionDraft> graphMentions,
      List<String> oldChunkIds) {
    try {
      vectorStore.upsert(upserts);
      if (CollUtil.isNotEmpty(oldChunkIds)) {
        vectorStore.deleteByChunkIds(oldChunkIds);
      }
      // 图关闭（NoOp）则跳过；开启但瞬时不可用则标 FAILED，避免 READY 下残留旧边
      if (graphStore.enabled()) {
        if (!graphStore.available()) {
          String message = "图存储已开启但不可用，拒绝留下旧边";
          log.error("索引事务已提交但图不可用 documentId={}", documentId);
          documentStatusWriter.markFailed(documentId, message);
          return;
        }
        for (GraphMentionDraft mention : graphMentions) {
          graphStore.upsertChunkMentions(
              mention.chunkId(),
              mention.documentTitle(),
              mention.headingPath(),
              mention.snippet(),
              mention.indexVersion(),
              mention.confidence());
        }
        if (CollUtil.isNotEmpty(oldChunkIds)) {
          graphStore.deleteByDocumentId(documentId, oldChunkIds);
        }
        edgeOverrideReplay.syncEnabledToGraph(documentId, writeVersion);
      }
    } catch (Exception exception) {
      String message = StrUtil.blankToDefault(exception.getMessage(), "向量或图清理失败");
      log.error("索引事务已提交但外部存储替换失败 documentId={} err={}", documentId, message, exception);
      documentStatusWriter.markFailed(documentId, "向量或图清理失败：" + message);
    }
  }

  /**
   * 事务内攒下的图提及写载荷。
   *
   * @param chunkId        切块 ID
   * @param documentTitle  文档标题
   * @param headingPath    标题路径
   * @param snippet        摘要
   * @param indexVersion   索引版本
   * @param confidence     置信度
   */
  private record GraphMentionDraft(
      String chunkId,
      String documentTitle,
      String headingPath,
      String snippet,
      String indexVersion,
      double confidence) {}

  /**
   * 按归一化切块形态路由到注册的 {@link KnowledgeChunker}。
   *
   * @param mediaKind    介质
   * @param chunkProfile 切块形态
   * @param title        标题
   * @param text         正文
   * @return 切块列表
   */
  private List<KnowledgeChunkPiece> chunk(
      KnowledgeMediaKind mediaKind,
      KnowledgeChunkProfile chunkProfile,
      String title,
      String text) {
    KnowledgeChunkProfile profile = chunkProfile;
    if (profile == null) {
      profile = KnowledgeMediaKind.ARTICLE_MARKDOWN == mediaKind
          ? KnowledgeChunkProfile.MARKDOWN_STRUCTURED
          : KnowledgeChunkProfile.PLAIN;
    }
    KnowledgeChunkParams params = chunkParamsResolver.resolve(profile, mediaKind);
    KnowledgeChunkRequest request = KnowledgeChunkRequest.builder()
        .title(title)
        .text(text)
        .mediaKind(mediaKind)
        .chunkProfile(profile)
        .params(params)
        .build();
    KnowledgeChunker chunker = resolveChunker(profile);
    return chunker.chunk(request);
  }

  /**
   * 按形态解析切块器；同一形态只允许一个实现。
   *
   * @param chunkProfile 切块形态
   * @return 切块器
   */
  private KnowledgeChunker resolveChunker(KnowledgeChunkProfile chunkProfile) {
    if (CollUtil.isEmpty(chunkers)) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_INDEX_FAILED,
          "未注册任何切块策略",
          500);
    }
    KnowledgeChunker matched = null;
    for (KnowledgeChunker chunker : chunkers) {
      if (!chunker.supports(chunkProfile)) {
        continue;
      }
      if (matched != null) {
        throw new BusinessException(
            ErrorCodes.KNOWLEDGE_INDEX_FAILED,
            "切块形态重复注册：" + chunkProfile.getValue(),
            500);
      }
      matched = chunker;
    }
    if (matched == null) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_INDEX_FAILED,
          "不支持的切块形态：" + chunkProfile.getValue(),
          400);
    }
    return matched;
  }

  /**
   * 解析来源连接器。
   *
   * @param sourceKind 来源种类
   * @return 连接器或 null
   */
  private KnowledgeSourceConnector resolveConnector(KnowledgeSourceKind sourceKind) {
    if (CollUtil.isEmpty(sourceConnectors)) {
      return null;
    }
    for (KnowledgeSourceConnector connector : sourceConnectors) {
      if (connector.supports(sourceKind)) {
        return connector;
      }
    }
    return null;
  }

  /**
   * 解析正文抽取器。
   *
   * @param mediaKind 介质种类
   * @return 抽取器
   */
  private KnowledgeTextExtractor resolveExtractor(KnowledgeMediaKind mediaKind) {
    for (KnowledgeTextExtractor extractor : textExtractors) {
      if (extractor.supports(mediaKind)) {
        return extractor;
      }
    }
    throw new BusinessException(
        ErrorCodes.KNOWLEDGE_INDEX_FAILED,
        "不支持的介质种类：" + mediaKind.getValue(),
        400);
  }
}
