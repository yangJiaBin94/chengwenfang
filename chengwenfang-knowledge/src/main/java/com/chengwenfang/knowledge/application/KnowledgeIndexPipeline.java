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
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.domain.model.KnowledgeVectorUpsert;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.domain.rule.MarkdownChunker;
import com.chengwenfang.knowledge.domain.rule.PlainTextChunker;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
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
  /** Markdown 切块器。 */
  private final MarkdownChunker markdownChunker = new MarkdownChunker();
  /** 纯文本切块器。 */
  private final PlainTextChunker plainTextChunker = new PlainTextChunker();

  /**
   * 对指定文档执行索引（幂等跳过、失败写 FAILED）。
   *
   * @param documentId 文档主键
   */
  @Transactional
  public void indexDocument(String documentId) {
    KnowledgeDocumentEntity document = documentMapper.selectById(documentId);
    if (document == null) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_DOCUMENT_NOT_FOUND,
          "知识库文档不存在",
          404);
    }
    try {
      KnowledgeIngestDraft draft = buildDraft(document);
      KnowledgeMediaKind mediaKind = KnowledgeMediaKind.from(document.getMediaKind());
      KnowledgeTextExtractor extractor = resolveExtractor(mediaKind);
      NormalizedText normalized = extractor.extract(draft);
      String contentHash = resolveContentHash(normalized, draft);
      if (shouldSkip(document, contentHash)) {
        log.info("知识库索引跳过 documentId={}（内容未变且已就绪）", documentId);
        return;
      }
      markIndexing(document);
      List<KnowledgeChunkPiece> pieces = chunk(mediaKind, normalized.getTitle(), normalized.getText());
      List<String> embeddingTexts = new ArrayList<>();
      for (KnowledgeChunkPiece piece : pieces) {
        embeddingTexts.add(StrUtil.blankToDefault(piece.getEmbeddingText(), piece.getContent()));
      }
      List<float[]> vectors = embeddingFacade.embed(embeddingTexts);
      if (vectors.size() != pieces.size()) {
        throw new IllegalStateException("嵌入结果数量与切块不一致");
      }
      vectorStore.deleteByDocumentId(documentId);
      chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
          .eq(KnowledgeChunkEntity::getDocumentId, documentId));
      int dimension = vectors.get(0).length;
      vectorStore.ensureCollection(dimension);
      String embeddingModel = embeddingFacade.embeddingModelName();
      List<KnowledgeVectorUpsert> upserts = new ArrayList<>();
      LocalDateTime now = LocalDateTime.now();
      for (int index = 0; index < pieces.size(); index++) {
        KnowledgeChunkPiece piece = pieces.get(index);
        String chunkId = IdsUtil.newId();
        KnowledgeChunkEntity chunk = KnowledgeChunkEntity.builder()
            .id(chunkId)
            .documentId(documentId)
            .chunkIndex(ObjectUtil.defaultIfNull(piece.getChunkIndex(), index))
            .headingPath(piece.getHeadingPath())
            .content(piece.getContent())
            .tokenEstimate(ObjectUtil.defaultIfNull(piece.getTokenEstimate(), 0))
            .qdrantPointId(chunkId)
            .embeddingModel(embeddingModel)
            .createdAt(now)
            .build();
        chunkMapper.insert(chunk);
        upserts.add(KnowledgeVectorUpsert.builder()
            .chunkId(chunkId)
            .documentId(documentId)
            .vector(vectors.get(index))
            .build());
      }
      vectorStore.upsert(upserts);
      document.setStatus(KnowledgeDocumentStatus.READY.getValue());
      document.setChunkCount(pieces.size());
      document.setContentHash(contentHash);
      document.setErrorMessage(null);
      document.setTitle(StrUtil.blankToDefault(normalized.getTitle(), document.getTitle()));
      document.setUpdatedAt(LocalDateTime.now());
      documentMapper.updateById(document);
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
   * 解析正文哈希。
   *
   * @param normalized 归一化正文
   * @param draft      草稿
   * @return 哈希
   */
  private static String resolveContentHash(NormalizedText normalized, KnowledgeIngestDraft draft) {
    if (draft != null && StrUtil.isNotBlank(draft.getContentHash())) {
      return draft.getContentHash();
    }
    String text = normalized == null ? "" : StrUtil.nullToEmpty(normalized.getText());
    return DigestUtil.sha256Hex(text);
  }

  /**
   * 是否可跳过索引。
   *
   * @param document    文档
   * @param contentHash 内容哈希
   * @return 跳过时 true
   */
  private static boolean shouldSkip(KnowledgeDocumentEntity document, String contentHash) {
    if (!KnowledgeDocumentStatus.READY.matches(document.getStatus())) {
      return false;
    }
    int chunkCount = ObjectUtil.defaultIfNull(document.getChunkCount(), 0);
    if (chunkCount <= 0) {
      return false;
    }
    return StrUtil.equals(contentHash, document.getContentHash());
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
   * 标记失败。
   *
   * @param document 文档
   * @param message  失败原因
   */
  private void markFailed(KnowledgeDocumentEntity document, String message) {
    if (document == null) {
      return;
    }
    document.setStatus(KnowledgeDocumentStatus.FAILED.getValue());
    document.setErrorMessage(message != null && message.length() > 1000 ? message.substring(0, 1000) : message);
    document.setUpdatedAt(LocalDateTime.now());
    documentMapper.updateById(document);
  }

  /**
   * 按介质切块。
   *
   * @param mediaKind 介质
   * @param title     标题
   * @param text      正文
   * @return 切块列表
   */
  private List<KnowledgeChunkPiece> chunk(KnowledgeMediaKind mediaKind, String title, String text) {
    if (KnowledgeMediaKind.ARTICLE_MARKDOWN == mediaKind) {
      return markdownChunker.chunk(title, text);
    }
    return plainTextChunker.chunk(title, text);
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
