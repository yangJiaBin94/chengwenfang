package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.KnowledgeIndexDocumentJobInput;
import com.chengwenfang.job.facade.dto.job.KnowledgeReindexAllJobInput;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatSessionEntity;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatCreateRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeChatMessageRequest;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeReindexRequest;
import com.chengwenfang.knowledge.facade.dto.request.RegisterKnowledgeDocumentRequest;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatAnswerView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatMessageView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeChatSessionView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeDocumentView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeIndexStatsView;
import com.chengwenfang.knowledge.facade.dto.view.KnowledgeSourceCountView;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeSourceConnector;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeChatSessionMapper;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link KnowledgeFacade} 默认实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultKnowledgeFacade implements KnowledgeFacade {

  /** 任务入队实体类型。 */
  private static final String JOB_ENTITY_TYPE = "knowledge_document";

  /** 文档 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;
  /** 会话 Mapper。 */
  private final KnowledgeChatSessionMapper sessionMapper;
  /** 任务门面。 */
  private final JobFacade jobFacade;
  /** 索引流水线。 */
  private final KnowledgeIndexPipeline indexPipeline;
  /** RAG 问答。 */
  private final RagChatService ragChatService;
  /** 来源连接器。 */
  private final List<KnowledgeSourceConnector> sourceConnectors;

  /** {@inheritDoc} */
  @Override
  @Transactional
  public KnowledgeDocumentView upsertSourceDocument(RegisterKnowledgeDocumentRequest request) {
    KnowledgeDocumentEntity saved = persistSourceDocument(request);
    enqueueIndexDocument(saved.getId());
    return toDocumentView(saved);
  }

  /** {@inheritDoc} */
  @Override
  public JobView enqueueIndexDocument(String documentId) {
    if (StrUtil.isBlank(documentId)) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_DOCUMENT_NOT_FOUND, "文档 ID 不能为空", 400);
    }
    KnowledgeIndexDocumentJobInput input = KnowledgeIndexDocumentJobInput.builder()
        .documentId(documentId)
        .build();
    JobView job = jobFacade.create(
        JobTypes.KNOWLEDGE_INDEX_DOCUMENT,
        JOB_ENTITY_TYPE,
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
        JOB_ENTITY_TYPE,
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
    List<Map<String, Object>> grouped = documentMapper.selectMaps(new QueryWrapper<KnowledgeDocumentEntity>()
        .select("source_kind", "COUNT(*) AS cnt")
        .groupBy("source_kind"));
    List<KnowledgeSourceCountView> bySource = new ArrayList<>();
    for (Map<String, Object> row : grouped) {
      String kind = String.valueOf(row.get("source_kind"));
      Long count = convertToLong(row.get("cnt"));
      bySource.add(KnowledgeSourceCountView.builder()
          .sourceKind(KnowledgeSourceKind.from(kind))
          .count(count)
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
    String title = request == null ? "" : StrUtil.blankToDefault(request.getTitle(), "新的知识库对话");
    KnowledgeChatSessionEntity session = KnowledgeChatSessionEntity.builder()
        .id(IdsUtil.newId())
        .title(title)
        .createdAt(now)
        .updatedAt(now)
        .build();
    sessionMapper.insert(session);
    return toSessionView(session);
  }

  /** {@inheritDoc} */
  @Override
  public List<KnowledgeChatSessionView> listSessions() {
    return sessionMapper.selectList(new LambdaQueryWrapper<KnowledgeChatSessionEntity>()
            .orderByDesc(KnowledgeChatSessionEntity::getUpdatedAt)
            .last("LIMIT 100"))
        .stream()
        .map(DefaultKnowledgeFacade::toSessionView)
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
        RegisterKnowledgeDocumentRequest register = RegisterKnowledgeDocumentRequest.builder()
            .sourceKind(resolveSourceKind(connector, filter))
            .sourceRefId(sourceRefId)
            .mediaKind(KnowledgeMediaKind.ARTICLE_MARKDOWN)
            .build();
        persistSourceDocument(register);
      }
    }
    LambdaQueryWrapper<KnowledgeDocumentEntity> query = new LambdaQueryWrapper<>();
    if (filter != null) {
      query.eq(KnowledgeDocumentEntity::getSourceKind, filter.getValue());
    }
    List<KnowledgeDocumentEntity> documents = documentMapper.selectList(query);
    for (KnowledgeDocumentEntity document : documents) {
      indexPipeline.indexDocument(document.getId());
    }
    log.info("知识库全量重建完成 documents={}", documents.size());
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
    KnowledgeDocumentEntity existing = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocumentEntity>()
        .eq(KnowledgeDocumentEntity::getSourceKind, request.getSourceKind().getValue())
        .eq(KnowledgeDocumentEntity::getSourceRefId, request.getSourceRefId())
        .last("LIMIT 1"));
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
      if (StrUtil.isBlank(request.getTitle())) {
        request.setTitle(draft.getTitle());
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

  /**
   * 聚合查询 count 转 Long。
   *
   * @param raw 原始值
   * @return Long
   */
  private static Long convertToLong(Object raw) {
    if (raw == null) {
      return 0L;
    }
    if (raw instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(raw));
  }

  /**
   * 文档实体转视图。
   *
   * @param entity 实体
   * @return 视图
   */
  private static KnowledgeDocumentView toDocumentView(KnowledgeDocumentEntity entity) {
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
  private static KnowledgeChatSessionView toSessionView(KnowledgeChatSessionEntity entity) {
    return KnowledgeChatSessionView.builder()
        .id(entity.getId())
        .title(entity.getTitle())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
