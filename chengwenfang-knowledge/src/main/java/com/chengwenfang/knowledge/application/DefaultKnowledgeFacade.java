package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.PageQuery;
import com.chengwenfang.common.PageResult;
import com.chengwenfang.crawler.facade.ArticleContentFacade;
import com.chengwenfang.crawler.facade.dto.request.ArticleContentRequest;
import com.chengwenfang.crawler.facade.dto.view.ArticleContentResult;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.KnowledgeIndexDocumentJobInput;
import com.chengwenfang.job.facade.dto.job.KnowledgeReindexAllJobInput;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatMessageEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatSessionEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.facade.constant.KnowledgeJobEntityTypes;
import com.chengwenfang.knowledge.facade.constant.KnowledgeRagConstants;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeGraphEdgeCorrectRequest;
import com.chengwenfang.knowledge.infrastructure.assembler.KnowledgeViewAssembler;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatMessageMapper;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeAnswerFeedbackRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatCreateRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatMessageRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeReindexRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeDocumentListRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeWebPageIngestRequest;
import com.chengwenfang.knowledge.facade.dto.request.RegisterKnowledgeDocumentRequest;
import com.chengwenfang.knowledge.application.ingest.KnowledgeUploadMediaResolver;
import cn.hutool.crypto.digest.DigestUtil;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeAnswerFeedbackView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatSessionView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeDocumentView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeIndexStatsView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeMemoryView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeReconcileResultView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeRetrievalTraceView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeSourceCountView;
import com.chengwenfang.job.facade.dto.job.KnowledgeReconcileJobInput;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.domain.rule.KnowledgeCitationTitles;
import com.chengwenfang.knowledge.facade.spi.KnowledgeGraphStore;
import com.chengwenfang.knowledge.facade.spi.KnowledgeSourceConnector;
import com.chengwenfang.knowledge.facade.spi.KnowledgeVectorStore;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatSessionMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link KnowledgeFacade} 默认实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultKnowledgeFacade implements KnowledgeFacade {

  /** 文档 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;
  /** 切块 Mapper。 */
  private final KnowledgeChunkMapper chunkMapper;
  /** 会话 Mapper。 */
  private final KnowledgeChatSessionMapper sessionMapper;
  /** 消息 Mapper。 */
  private final KnowledgeChatMessageMapper messageMapper;
  /** 任务门面。 */
  private final JobFacade jobFacade;
  /** 索引流水线。 */
  private final KnowledgeIndexPipeline indexPipeline;
  /** RAG 问答。 */
  private final RagChatService ragChatService;
  /** 来源连接器。 */
  private final List<KnowledgeSourceConnector> sourceConnectors;
  /** 图边纠错。 */
  private final KnowledgeGraphEdgeCorrectionService graphEdgeCorrectionService;
  /** 索引版本。 */
  private final KnowledgeIndexVersionService indexVersionService;
  /** 长期记忆。 */
  private final KnowledgeLongTermMemoryService longTermMemoryService;
  /** 检索轨迹。 */
  private final KnowledgeRetrievalTraceService retrievalTraceService;
  /** 三存储对账。 */
  private final KnowledgeReconcileService reconcileService;
  /** 答案反馈。 */
  private final KnowledgeAnswerFeedbackService answerFeedbackService;
  /** 图存储（切流后 GC 旧版）。 */
  private final KnowledgeGraphStore graphStore;
  /** 文件门面。 */
  private final FileFacade fileFacade;
  /** 文章正文门面（网页入库）。 */
  private final ArticleContentFacade articleContentFacade;
  /** 向量存储（删除文档时清理）。 */
  private final KnowledgeVectorStore vectorStore;

  /** {@inheritDoc} */
  @Override
  @Transactional
  public KnowledgeDocumentView upsertSourceDocument(RegisterKnowledgeDocumentRequest request) {
    KnowledgeDocumentEntity existing = findExistingSourceDocument(request);
    // 已可检索且指纹未变：跳过写文件与索引入队，避免确认选题等路径重复刷任务
    if (existing != null
        && KnowledgeDocumentStatus.READY.matches(existing.getStatus())
        && !contentChanged(existing, request)) {
      return KnowledgeViewAssembler.toDocumentView(existing);
    }
    // 登记时若已带正文，先落入 text_file 供连接器重建
    maybePersistRawTextFile(request);
    KnowledgeDocumentEntity saved = persistSourceDocument(request);
    enqueueIndexDocument(saved.getId());
    return KnowledgeViewAssembler.toDocumentView(saved);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public KnowledgeDocumentView uploadManualDocument(String fileName, String contentType, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_UPLOAD_REJECTED, "上传文件为空", 400);
    }
    KnowledgeMediaKind mediaKind = KnowledgeUploadMediaResolver.resolveMediaKind(fileName, bytes.length);
    String objectKey = "knowledge/upload/" + IdsUtil.newId() + "/"
        + StrUtil.blankToDefault(fileName, "upload.bin");
    FileView stored = fileFacade.saveLocalBytes(
        objectKey,
        StrUtil.blankToDefault(fileName, "upload.bin"),
        StrUtil.blankToDefault(contentType, "application/octet-stream"),
        bytes);
    RegisterKnowledgeDocumentRequest request = RegisterKnowledgeDocumentRequest.builder()
        .sourceKind(KnowledgeSourceKind.MANUAL_UPLOAD)
        .sourceRefId(stored.getId())
        .mediaKind(mediaKind)
        .title(StrUtil.blankToDefault(fileName, stored.getId()))
        .originFileId(stored.getId())
        .contentHash(DigestUtil.sha256Hex(bytes))
        .sourceVersionStamp(stored.getContentHash())
        .build();
    if (KnowledgeMediaKind.ARTICLE_MARKDOWN == mediaKind
        || KnowledgeMediaKind.PLAIN_TEXT == mediaKind) {
      request.setRawText(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }
    return upsertSourceDocument(request);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public KnowledgeDocumentView ingestWebPage(KnowledgeWebPageIngestRequest request) {
    if (request == null || StrUtil.isBlank(request.getUrl())) {
      throw new BusinessException(ErrorCodes.INVALID_URL, "网页地址不能为空", 400);
    }
    String url = request.getUrl().trim();
    ArticleContentResult result = articleContentFacade.resolve(
        ArticleContentRequest.builder().url(url).build());
    if (result == null || !result.isUsable() || StrUtil.isBlank(result.getText())) {
      String message = result == null
          ? "网页正文抓取失败"
          : StrUtil.blankToDefault(result.getErrorMessage(), "网页正文不可用");
      throw new BusinessException(ErrorCodes.KNOWLEDGE_EXTRACT_EMPTY, message, 400);
    }
    String title = StrUtil.blankToDefault(request.getTitle(), url);
    String sourceRefId = DigestUtil.sha256Hex(url);
    RegisterKnowledgeDocumentRequest register = RegisterKnowledgeDocumentRequest.builder()
        .sourceKind(KnowledgeSourceKind.MANUAL_UPLOAD)
        .sourceRefId(sourceRefId)
        .mediaKind(KnowledgeMediaKind.WEB_PAGE)
        .title(title)
        .rawText(result.getText())
        .deepLink(url)
        .contentHash(DigestUtil.sha256Hex(result.getText()))
        .sourceVersionStamp(String.valueOf(System.currentTimeMillis()))
        .build();
    return upsertSourceDocument(register);
  }

  /** {@inheritDoc} */
  @Override
  public PageResult<KnowledgeDocumentView> listDocuments(KnowledgeDocumentListRequest request) {
    KnowledgeDocumentListRequest safe = request == null
        ? KnowledgeDocumentListRequest.builder().build()
        : request;
    PageQuery pageQuery = PageQuery.builder()
        .page(safe.getPage())
        .pageSize(safe.getPageSize() <= 0 ? 10 : safe.getPageSize())
        .keyword(safe.getKeyword())
        .build()
        .normalized();
    LambdaQueryWrapper<KnowledgeDocumentEntity> query = new LambdaQueryWrapper<>();
    if (safe.getSourceKind() != null) {
      query.eq(KnowledgeDocumentEntity::getSourceKind, safe.getSourceKind().getValue());
    }
    if (safe.getMediaKind() != null) {
      query.eq(KnowledgeDocumentEntity::getMediaKind, safe.getMediaKind().getValue());
    }
    if (StrUtil.isNotBlank(safe.getKeyword())) {
      query.like(KnowledgeDocumentEntity::getTitle, safe.getKeyword().trim());
    }
    if (safe.getCreatedFrom() != null) {
      query.ge(KnowledgeDocumentEntity::getCreatedAt, safe.getCreatedFrom().atStartOfDay());
    }
    if (safe.getCreatedTo() != null) {
      query.le(
          KnowledgeDocumentEntity::getCreatedAt,
          safe.getCreatedTo().atTime(23, 59, 59, 999_000_000));
    }
    // count 与分页列表分拆：先统计再挂 ORDER/LIMIT，避免 last 污染 count
    long total = ObjectUtil.defaultIfNull(documentMapper.selectCount(query), 0L);
    List<KnowledgeDocumentView> views = new ArrayList<>();
    if (total > 0) {
      PageQuery normalized = pageQuery;
      int offset = normalized.offset();
      // 页码超出时夹到最后一页，避免空页
      if (offset >= total) {
        int lastPage = (int) Math.max(1, (total + normalized.getPageSize() - 1) / normalized.getPageSize());
        normalized = PageQuery.builder()
            .page(lastPage)
            .pageSize(normalized.getPageSize())
            .keyword(normalized.getKeyword())
            .build()
            .normalized();
        offset = normalized.offset();
      }
      query.orderByDesc(KnowledgeDocumentEntity::getCreatedAt)
          .orderByDesc(KnowledgeDocumentEntity::getId)
          .last("LIMIT " + normalized.getPageSize() + " OFFSET " + offset);
      List<KnowledgeDocumentEntity> rows = documentMapper.selectList(query);
      for (KnowledgeDocumentEntity row : rows) {
        KnowledgeDocumentView view = KnowledgeViewAssembler.toDocumentView(row);
        views.add(withFriendlyTitle(view, row));
      }
      return PageResult.of(normalized, views, total);
    }
    return PageResult.of(pageQuery, views, total);
  }

  /**
   * 工作台技术 ID 标题换成文章可读名（列表展示，不回写库）。
   *
   * @param view 原视图
   * @param row  文档行
   * @return 标题已友好化的视图
   */
  private KnowledgeDocumentView withFriendlyTitle(
      KnowledgeDocumentView view, KnowledgeDocumentEntity row) {
    if (view == null || row == null) {
      return view;
    }
    if (!KnowledgeCitationTitles.looksTechnical(view.getTitle())) {
      return view;
    }
    String friendly = null;
    if (KnowledgeSourceKind.WORK_OUTPUT.matches(row.getSourceKind())) {
      for (KnowledgeSourceConnector connector : sourceConnectors) {
        if (!connector.supports(KnowledgeSourceKind.WORK_OUTPUT)) {
          continue;
        }
        KnowledgeIngestDraft draft = connector.load(row.getSourceRefId());
        if (draft != null && StrUtil.isNotBlank(draft.getTitle())) {
          friendly = draft.getTitle();
        }
        break;
      }
    }
    if (StrUtil.isBlank(friendly) || KnowledgeCitationTitles.looksTechnical(friendly)) {
      KnowledgeChunkEntity firstChunk = chunkMapper.selectOne(
          new LambdaQueryWrapper<KnowledgeChunkEntity>()
              .eq(KnowledgeChunkEntity::getDocumentId, row.getId())
              .orderByAsc(KnowledgeChunkEntity::getChunkIndex)
              .last("LIMIT 1"));
      friendly = KnowledgeCitationTitles.resolve(
          view.getTitle(),
          firstChunk == null ? null : firstChunk.getHeadingPath());
    }
    view.setTitle(friendly);
    return view;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void deleteDocument(String documentId) {
    if (StrUtil.isBlank(documentId)) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_DOCUMENT_NOT_FOUND, "文档 ID 不能为空", 400);
    }
    KnowledgeDocumentEntity document = documentMapper.selectById(documentId);
    if (document == null) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_DOCUMENT_NOT_FOUND, "知识库文档不存在", 404);
    }
    // 先收集切块 ID，再清外部存储，最后删 MySQL（对齐对账清理顺序）
    List<KnowledgeChunkEntity> chunks = chunkMapper.selectList(
        new LambdaQueryWrapper<KnowledgeChunkEntity>()
            .eq(KnowledgeChunkEntity::getDocumentId, documentId)
            .select(KnowledgeChunkEntity::getId));
    List<String> chunkIds = new ArrayList<>();
    for (KnowledgeChunkEntity chunk : chunks) {
      if (chunk != null && StrUtil.isNotBlank(chunk.getId())) {
        chunkIds.add(chunk.getId());
      }
    }
    vectorStore.deleteByDocumentId(documentId);
    graphStore.deleteByDocumentId(documentId, chunkIds);
    chunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunkEntity>()
        .eq(KnowledgeChunkEntity::getDocumentId, documentId));
    documentMapper.deleteById(documentId);
    deleteFileQuietly(document.getTextFileId());
    if (KnowledgeSourceKind.MANUAL_UPLOAD.matches(document.getSourceKind())) {
      deleteFileQuietly(document.getOriginFileId());
    }
    log.info("已删除知识库文档 documentId={}", documentId);
  }

  /**
   * 登记请求携带 rawText 时写入 text 文件并回填 textFileId。
   *
   * @param request 登记请求
   */
  private void maybePersistRawTextFile(RegisterKnowledgeDocumentRequest request) {
    if (request == null || StrUtil.isBlank(request.getRawText())) {
      return;
    }
    if (StrUtil.isNotBlank(request.getTextFileId())) {
      return;
    }
    String ref = StrUtil.blankToDefault(request.getSourceRefId(), IdsUtil.newId());
    String objectKey = "knowledge/raw/" + request.getSourceKind().getValue() + "/" + ref + ".txt";
    StoredFileView stored = fileFacade.writeText(objectKey, ref + ".txt", request.getRawText());
    request.setTextFileId(stored.getId());
    if (StrUtil.isBlank(request.getSourceVersionStamp())) {
      request.setSourceVersionStamp(stored.getContentHash());
    }
  }

  /**
   * 忽略删除文件失败。
   *
   * @param fileId 文件 ID
   */
  private void deleteFileQuietly(String fileId) {
    if (StrUtil.isBlank(fileId)) {
      return;
    }
    try {
      fileFacade.delete(fileId);
    } catch (RuntimeException ignored) {
      log.warn("删除知识库关联文件失败 fileId={}", fileId);
    }
  }

  /** {@inheritDoc} */
  @Override
  public JobView enqueueIndexDocument(String documentId) {
    if (StrUtil.isBlank(documentId)) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_DOCUMENT_NOT_FOUND, "文档 ID 不能为空", 400);
    }
    if (documentMapper.selectById(documentId) == null) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_DOCUMENT_NOT_FOUND, "知识库文档不存在", 404);
    }
    KnowledgeIndexDocumentJobInput input = KnowledgeIndexDocumentJobInput.builder()
        .documentId(documentId)
        .build();
    JobView job = jobFacade.create(
        JobTypes.KNOWLEDGE_INDEX_DOCUMENT,
        KnowledgeJobEntityTypes.DOCUMENT,
        documentId,
        input);
    log.info("已入队知识库索引 documentId={} jobId={}", documentId, job.getId());
    return job;
  }

  /** {@inheritDoc} */
  @Override
  public JobView reindexAll(KnowledgeReindexRequest request) {
    KnowledgeSourceKind sourceKind = request == null ? null : request.getSourceKind();
    KnowledgeReindexAllJobInput input = KnowledgeReindexAllJobInput.builder()
        .sourceKind(sourceKind == null ? null : sourceKind.getValue())
        .build();
    JobView job = jobFacade.create(
        JobTypes.KNOWLEDGE_REINDEX_ALL,
        KnowledgeJobEntityTypes.DOCUMENT,
        "all",
        input);
    log.info("已入队知识库全量重建 jobId={}", job.getId());
    return job;
  }

  /** {@inheritDoc} */
  @Override
  public KnowledgeIndexStatsView stats() {
    long total = ObjectUtil.defaultIfNull(documentMapper.selectCount(null), 0L);
    long ready = ObjectUtil.defaultIfNull(documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
        .eq(KnowledgeDocumentEntity::getStatus, KnowledgeDocumentStatus.READY.getValue())), 0L);
    long failed = ObjectUtil.defaultIfNull(documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
        .eq(KnowledgeDocumentEntity::getStatus, KnowledgeDocumentStatus.FAILED.getValue())), 0L);
    List<KnowledgeSourceCountView> bySource = new ArrayList<>();
    for (com.chengwenfang.knowledge.domain.model.KnowledgeSourceKindCountRow row
        : documentMapper.countGroupBySourceKind()) {
      if (row == null) {
        continue;
      }
      bySource.add(KnowledgeSourceCountView.builder()
          .sourceKind(KnowledgeSourceKind.from(row.getSourceKind()))
          .count(row.getCnt() == null ? 0L : row.getCnt())
          .build());
    }
    return KnowledgeIndexStatsView.builder()
        .total(total)
        .ready(ready)
        .failed(failed)
        .bySourceKind(bySource)
        .build();
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public KnowledgeChatSessionView createSession(KnowledgeChatCreateRequest request) {
    LocalDateTime now = LocalDateTime.now();
    String title = request == null
        ? KnowledgeRagConstants.DEFAULT_SESSION_TITLE
        : StrUtil.blankToDefault(request.getTitle(), KnowledgeRagConstants.DEFAULT_SESSION_TITLE);
    KnowledgeChatSessionEntity session = KnowledgeChatSessionEntity.builder()
        .id(IdsUtil.newId())
        .title(title)
        .summary("")
        .memoryEnabled(true)
        .createdAt(now)
        .updatedAt(now)
        .build();
    sessionMapper.insert(session);
    return KnowledgeViewAssembler.toSessionView(session);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public KnowledgeChatSessionView renameSession(String sessionId, String title) {
    if (StrUtil.isBlank(sessionId)) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_SESSION_NOT_FOUND, "会话 ID 不能为空", 400);
    }
    String nextTitle = StrUtil.trim(title);
    if (StrUtil.isBlank(nextTitle)) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "标题不能为空", 400);
    }
    KnowledgeChatSessionEntity session = sessionMapper.selectById(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_SESSION_NOT_FOUND, "知识库会话不存在", 404);
    }
    session.setTitle(StrUtil.maxLength(nextTitle, 64));
    session.setUpdatedAt(LocalDateTime.now());
    sessionMapper.updateById(session);
    return KnowledgeViewAssembler.toSessionView(session);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void deleteSession(String sessionId) {
    if (StrUtil.isBlank(sessionId)) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_SESSION_NOT_FOUND, "会话 ID 不能为空", 400);
    }
    KnowledgeChatSessionEntity session = sessionMapper.selectById(sessionId);
    if (session == null) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_SESSION_NOT_FOUND, "知识库会话不存在", 404);
    }
    messageMapper.delete(new LambdaQueryWrapper<KnowledgeChatMessageEntity>()
        .eq(KnowledgeChatMessageEntity::getSessionId, sessionId));
    retrievalTraceService.deleteBySession(sessionId);
    sessionMapper.deleteById(sessionId);
  }

  /** {@inheritDoc} */
  @Override
  public List<KnowledgeChatSessionView> listSessions() {
    return sessionMapper.selectList(new LambdaQueryWrapper<KnowledgeChatSessionEntity>()
            .orderByDesc(KnowledgeChatSessionEntity::getUpdatedAt)
            .last("LIMIT 100"))
        .stream()
        .map(KnowledgeViewAssembler::toSessionView)
        .toList();
  }

  /** {@inheritDoc} */
  @Override
  public List<KnowledgeChatMessageView> listMessages(String sessionId) {
    return ragChatService.listMessages(sessionId);
  }

  /** {@inheritDoc} */
  @Override
  public KnowledgeChatAnswerView ask(String sessionId, KnowledgeChatMessageRequest request) {
    return ragChatService.ask(sessionId, request);
  }

  /** {@inheritDoc} */
  @Override
  public void indexDocumentNow(String documentId) {
    indexPipeline.indexDocument(documentId);
  }

  /** {@inheritDoc} */
  @Override
  public void runReindexAll(KnowledgeReindexRequest request) {
    KnowledgeSourceKind filter = request == null ? null : request.getSourceKind();
    for (KnowledgeSourceConnector connector : sourceConnectors) {
      if (filter != null && !connector.supports(filter)) {
        continue;
      }
      for (String sourceRefId : connector.listForReindex()) {
        // 不写死 mediaKind：由连接器 enrich，避免覆盖手动上传的 DOCUMENT/WEB_PAGE 等
        RegisterKnowledgeDocumentRequest register = RegisterKnowledgeDocumentRequest.builder()
            .sourceKind(resolveSourceKind(connector, filter))
            .sourceRefId(sourceRefId)
            .build();
        persistSourceDocument(register);
      }
    }
    LambdaQueryWrapper<KnowledgeDocumentEntity> query = new LambdaQueryWrapper<>();
    if (filter != null) {
      query.eq(KnowledgeDocumentEntity::getSourceKind, filter.getValue());
    }
    List<KnowledgeDocumentEntity> documents = documentMapper.selectList(query);
    if (filter != null) {
      // 按来源局部重建：就地写 active；若存在未切流的 writing 版本则禁止，避免与全量蓝绿撕裂
      assertNoPendingWritingVersion();
      for (KnowledgeDocumentEntity document : documents) {
        indexPipeline.indexDocument(document.getId());
      }
      log.info(
          "知识库按来源重建完成 sourceKind={} documents={}",
          filter.getValue(),
          documents.size());
      return;
    }
    if (documents.isEmpty()) {
      log.info("知识库全量重建跳过：无文档，不切流");
      return;
    }
    // 先落库 writing 标记再写，避免首包窗口内增量仍进 active
    String previousActive = indexVersionService.activeVersion();
    String writingVersion = indexVersionService.beginNextWritingVersion();
    for (KnowledgeDocumentEntity document : documents) {
      indexPipeline.indexDocument(document.getId(), writingVersion);
    }
    assertWritingVersionCoversDocuments(writingVersion, documents);
    // shortcut: 尚无金标门禁，覆盖完整即切流；接入金标后改为门禁通过再 promote
    // promote 内会 GC 旧版图；previousActive 仅用于日志对照
    promoteIndexVersion(writingVersion);
    log.info(
        "知识库全量重建完成 documents={} writingVersion={} previousActive={}",
        documents.size(),
        writingVersion,
        previousActive);
  }

  /**
   * 断言目标版本覆盖全部待重建文档（每文档至少一条切块）。
   *
   * @param writingVersion 写入版本
   * @param documents      文档列表
   */
  private void assertWritingVersionCoversDocuments(
      String writingVersion, List<KnowledgeDocumentEntity> documents) {
    for (KnowledgeDocumentEntity document : documents) {
      if (document == null || StrUtil.isBlank(document.getId())) {
        continue;
      }
      assertDocumentCoveredInVersion(document.getId(), writingVersion);
    }
  }

  /**
   * 断言文档在目标版本下至少有一条切块。
   *
   * @param documentId 文档 ID
   * @param version    索引版本
   */
  private void assertDocumentCoveredInVersion(String documentId, String version) {
    Long chunkCount = chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkEntity>()
        .eq(KnowledgeChunkEntity::getDocumentId, documentId)
        .eq(KnowledgeChunkEntity::getIndexVersion, version));
    if (chunkCount == null || chunkCount <= 0) {
      throw new BusinessException(
          ErrorCodes.INVALID_STATUS,
          "目标版本未覆盖文档，拒绝切流：documentId=" + documentId + " version=" + version,
          400);
    }
  }

  /**
   * 若状态表存在 writing_version，视为全量蓝绿进行中，禁止按来源局部写 active。
   */
  private void assertNoPendingWritingVersion() {
    String writing = indexVersionService.writingVersion();
    if (StrUtil.isNotBlank(writing)) {
      throw new BusinessException(
          ErrorCodes.INVALID_STATUS,
          "全量重建写入版本未切流（" + writing + "），禁止按来源局部重建",
          409);
    }
  }

  /**
   * 按来源键查找已有文档。
   *
   * @param request 登记请求
   * @return 已有行；无则 null
   */
  private KnowledgeDocumentEntity findExistingSourceDocument(RegisterKnowledgeDocumentRequest request) {
    if (request == null
        || request.getSourceKind() == null
        || StrUtil.isBlank(request.getSourceRefId())) {
      return null;
    }
    return documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
        .eq(KnowledgeDocumentEntity::getSourceKind, request.getSourceKind().getValue())
        .eq(KnowledgeDocumentEntity::getSourceRefId, request.getSourceRefId())
        .last("LIMIT 1"));
  }

  /**
   * 登记或更新文档行（不入队索引任务）。
   *
   * @param request 登记请求
   * @return 持久化后的实体
   */
  private KnowledgeDocumentEntity persistSourceDocument(RegisterKnowledgeDocumentRequest request) {
    if (request == null
        || request.getSourceKind() == null
        || StrUtil.isBlank(request.getSourceRefId())) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "登记请求缺少来源信息", 400);
    }
    enrichFromConnector(request);
    KnowledgeDocumentEntity existing = findExistingSourceDocument(request);
    LocalDateTime now = LocalDateTime.now();
    if (existing == null) {
      KnowledgeDocumentEntity created = KnowledgeDocumentEntity.builder()
          .id(IdsUtil.newId())
          .sourceKind(request.getSourceKind().getValue())
          .sourceRefId(request.getSourceRefId())
          .mediaKind(ObjectUtil.defaultIfNull(request.getMediaKind(), KnowledgeMediaKind.ARTICLE_MARKDOWN).getValue())
          .title(StrUtil.blankToDefault(request.getTitle(), ""))
          .themeId(request.getThemeId())
          .originFileId(request.getOriginFileId())
          .textFileId(request.getTextFileId())
          .contentHash(request.getContentHash())
          .sourceVersionStamp(request.getSourceVersionStamp())
          .status(KnowledgeDocumentStatus.PENDING.getValue())
          .chunkCount(0)
          .deepLink(request.getDeepLink())
          .createdAt(now)
          .updatedAt(now)
          .build();
      documentMapper.insert(created);
      return created;
    }
    boolean contentChanged = contentChanged(existing, request);
    existing.setTitle(StrUtil.blankToDefault(request.getTitle(), existing.getTitle()));
    existing.setThemeId(StrUtil.blankToDefault(request.getThemeId(), existing.getThemeId()));
    if (request.getMediaKind() != null) {
      existing.setMediaKind(request.getMediaKind().getValue());
    }
    if (StrUtil.isNotBlank(request.getContentHash())) {
      existing.setContentHash(request.getContentHash());
    }
    if (StrUtil.isNotBlank(request.getSourceVersionStamp())) {
      existing.setSourceVersionStamp(request.getSourceVersionStamp());
    }
    if (StrUtil.isNotBlank(request.getDeepLink())) {
      existing.setDeepLink(request.getDeepLink());
    }
    if (StrUtil.isNotBlank(request.getOriginFileId())) {
      existing.setOriginFileId(request.getOriginFileId());
    }
    if (StrUtil.isNotBlank(request.getTextFileId())) {
      existing.setTextFileId(request.getTextFileId());
    }
    if (contentChanged) {
      existing.setStatus(KnowledgeDocumentStatus.STALE.getValue());
    }
    existing.setUpdatedAt(now);
    documentMapper.updateById(existing);
    return existing;
  }

  /**
   * 从连接器补齐登记请求缺省字段。
   *
   * @param request 登记请求
   */
  private void enrichFromConnector(RegisterKnowledgeDocumentRequest request) {
    for (KnowledgeSourceConnector connector : sourceConnectors) {
      if (!connector.supports(request.getSourceKind())) {
        continue;
      }
      KnowledgeIngestDraft draft = connector.load(request.getSourceRefId());
      if (draft == null) {
        return;
      }
      if (request.getMediaKind() == null && draft.getMediaKind() != null) {
        request.setMediaKind(draft.getMediaKind());
      }
      // 技术 ID 标题一律用连接器可读标题覆盖，便于资料库展示文章名
      if (StrUtil.isBlank(request.getTitle())
          || KnowledgeCitationTitles.looksTechnical(request.getTitle())) {
        if (StrUtil.isNotBlank(draft.getTitle())) {
          request.setTitle(draft.getTitle());
        }
      }
      if (StrUtil.isBlank(request.getContentHash())) {
        request.setContentHash(draft.getContentHash());
      }
      if (StrUtil.isBlank(request.getSourceVersionStamp())) {
        request.setSourceVersionStamp(draft.getSourceVersionStamp());
      }
      if (StrUtil.isBlank(request.getDeepLink())) {
        request.setDeepLink(draft.getDeepLink());
      }
      if (StrUtil.isBlank(request.getThemeId())) {
        request.setThemeId(draft.getThemeId());
      }
      return;
    }
  }

  /**
   * 判断内容是否变化。
   *
   * @param existing 既有文档
   * @param request  登记请求
   * @return 变化时 true
   */
  private static boolean contentChanged(KnowledgeDocumentEntity existing, RegisterKnowledgeDocumentRequest request) {
    if (StrUtil.isNotBlank(request.getContentHash())
        && !StrUtil.equals(request.getContentHash(), existing.getContentHash())) {
      return true;
    }
    return StrUtil.isNotBlank(request.getSourceVersionStamp())
        && !StrUtil.equals(request.getSourceVersionStamp(), existing.getSourceVersionStamp());
  }

  /**
   * 解析连接器对应的来源种类。
   *
   * @param connector 连接器
   * @param filter    过滤
   * @return 来源种类
   */
  private static KnowledgeSourceKind resolveSourceKind(
      KnowledgeSourceConnector connector, KnowledgeSourceKind filter) {
    if (filter != null) {
      return filter;
    }
    for (KnowledgeSourceKind kind : KnowledgeSourceKind.values()) {
      if (connector.supports(kind)) {
        return kind;
      }
    }
    return KnowledgeSourceKind.WORK_OUTPUT;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void correctGraphEdge(KnowledgeGraphEdgeCorrectRequest request) {
    if (request == null) {
      return;
    }
    graphEdgeCorrectionService.upsertOverride(
        request.getEdgeUid(),
        request.getSourceChunkId(),
        request.getConfidence(),
        request.getNote(),
        request.getEnabled());
    if (Boolean.TRUE.equals(request.getRebuildChunk())) {
      graphEdgeCorrectionService.rebuildEdgesForChunk(request.getSourceChunkId());
    }
  }

  /** {@inheritDoc} */
  @Override
  public String activeIndexVersion() {
    return indexVersionService.activeVersion();
  }

  /** {@inheritDoc} */
  @Override
  public void promoteIndexVersion(String version) {
    if (StrUtil.isBlank(version)) {
      throw new BusinessException(ErrorCodes.INVALID_STATUS, "索引版本不能为空", 400);
    }
    String trimmed = version.trim();
    String previousActive = indexVersionService.activeVersion();
    Long chunkCount = chunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunkEntity>()
        .eq(KnowledgeChunkEntity::getIndexVersion, trimmed));
    if (chunkCount == null || chunkCount <= 0) {
      throw new BusinessException(
          ErrorCodes.INVALID_STATUS,
          "目标版本无切块，拒绝切流：" + trimmed,
          400);
    }
    // 与全量重建同门禁：旧 active 上有切块的文档，目标版本必须覆盖
    if (!StrUtil.equals(previousActive, trimmed)) {
      List<String> documentIds =
          chunkMapper.selectDistinctDocumentIdsByIndexVersion(previousActive);
      for (String documentId : documentIds) {
        if (StrUtil.isNotBlank(documentId)) {
          assertDocumentCoveredInVersion(documentId, trimmed);
        }
      }
    }
    indexVersionService.promoteActiveVersion(trimmed);
    if (!StrUtil.equals(previousActive, trimmed)) {
      graphStore.deleteByIndexVersion(previousActive);
    }
    log.info("知识库索引版本已切流 version={} chunks={}", trimmed, chunkCount);
  }

  /** {@inheritDoc} */
  @Override
  public List<KnowledgeMemoryView> listMemories(int limit) {
    return longTermMemoryService.listMemories(limit);
  }

  /** {@inheritDoc} */
  @Override
  public void deleteMemory(String memoryId) {
    longTermMemoryService.deleteMemory(memoryId);
  }

  /** {@inheritDoc} */
  @Override
  public void setMemoryPaused(String memoryId, boolean paused) {
    longTermMemoryService.setPaused(memoryId, paused);
  }

  /** {@inheritDoc} */
  @Override
  public List<KnowledgeRetrievalTraceView> listRetrievalTraces(String sessionId, int limit) {
    return retrievalTraceService.listBySession(sessionId, limit);
  }

  /** {@inheritDoc} */
  @Override
  public JobView enqueueReconcile(boolean dryRun) {
    JobView job = jobFacade.create(
        JobTypes.KNOWLEDGE_RECONCILE,
        KnowledgeJobEntityTypes.DOCUMENT,
        "reconcile",
        KnowledgeReconcileJobInput.builder().dryRun(dryRun).build());
    log.info("已入队知识库对账 jobId={} dryRun={}", job.getId(), dryRun);
    return job;
  }

  /** {@inheritDoc} */
  @Override
  public KnowledgeReconcileResultView runReconcile() {
    return reconcileService.reconcile(false);
  }

  /** {@inheritDoc} */
  @Override
  public KnowledgeReconcileResultView runReconcile(boolean dryRun) {
    return reconcileService.reconcile(dryRun);
  }

  /** {@inheritDoc} */
  @Override
  public KnowledgeAnswerFeedbackView submitAnswerFeedback(
      String messageId, KnowledgeAnswerFeedbackRequest request) {
    return answerFeedbackService.submit(messageId, request);
  }

}
