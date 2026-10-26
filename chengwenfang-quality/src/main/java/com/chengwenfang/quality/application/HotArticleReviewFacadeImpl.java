package com.chengwenfang.quality.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.WorkOutputRef;
import com.chengwenfang.quality.domain.entity.HotArticleRepairCandidateEntity;
import com.chengwenfang.quality.facade.constant.ArticleAuditDomains;
import com.chengwenfang.quality.facade.constant.HotArticleRepairStatuses;
import com.chengwenfang.quality.facade.constant.HotAuditResultKeys;
import com.chengwenfang.quality.domain.entity.ArticleAuditRunEntity;
import com.chengwenfang.quality.facade.dto.view.HotArticleAuditView;
import com.chengwenfang.quality.facade.dto.view.HotArticleRepairCandidateView;
import com.chengwenfang.quality.facade.dto.view.HotArticleRepairRejectView;
import com.chengwenfang.quality.facade.dto.view.HotArticleReviewDecisionView;
import com.chengwenfang.quality.facade.dto.view.HotArticleReviewEngineResult;
import com.chengwenfang.quality.facade.HotArticleReviewFacade;
import com.chengwenfang.quality.facade.dto.request.HotArticleReviewRunCreateRequest;
import com.chengwenfang.quality.facade.dto.view.HotArticleReviewRunView;
import com.chengwenfang.quality.facade.enums.ReviewStatus;
import com.chengwenfang.quality.infrastructure.mapper.HotArticleRepairCandidateMapper;
import com.chengwenfang.quality.infrastructure.mapper.ArticleAuditRunMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link HotArticleReviewFacade} 实现：审稿运行/条目读写 {@code audit_*}，修复候选落 {@code repair_candidates}。
 */
@Slf4j
@Service
public class HotArticleReviewFacadeImpl implements HotArticleReviewFacade {
  /** 审稿运行表。 */
  private final ArticleAuditRunMapper runMapper;
  /** 修复候选表。 */
  private final HotArticleRepairCandidateMapper candidateMapper;
  /** 热点结果列/子表存储。 */
  private final HotAuditResultStore auditResultStore;
  /** 修复候选正文与问题码。 */
  private final RepairCandidateStore candidateStore;
  /** 内容门面（产出 → 作品 id）。 */
  private final ContentFacade content;
  /** 审稿汇总视图组装。 */
  private final HotArticleAuditViewAssembler auditViewAssembler;

  /**
   * 注入审稿相关依赖。
   *
   * @param runMapper          运行表 Mapper
   * @param candidateMapper    修复候选 Mapper
   * @param auditResultStore   热点结果存储
   * @param candidateStore     候选正文/问题存储
   * @param content            内容门面
   * @param auditViewAssembler 审稿汇总视图组装器
   */
  public HotArticleReviewFacadeImpl(
      ArticleAuditRunMapper runMapper,
      HotArticleRepairCandidateMapper candidateMapper,
      HotAuditResultStore auditResultStore,
      RepairCandidateStore candidateStore,
      ContentFacade content,
      HotArticleAuditViewAssembler auditViewAssembler) {
    this.runMapper = runMapper;
    this.candidateMapper = candidateMapper;
    this.auditResultStore = auditResultStore;
    this.candidateStore = candidateStore;
    this.content = content;
    this.auditViewAssembler = auditViewAssembler;
  }

  /**
   * 创建 pending 审稿运行行（id 通常等于 jobId）。
   *
   * @param reviewId  运行主键（常与 jobId 相同）
   * @param articleId 被审热点文章 ID
   * @param jobId     关联后台任务 ID
   * @param input     创建入参；null 按空 builder；mode/contentHash/standardVersion 可缺省
   * @return 新建运行的视图
   */
  @Override
  @Transactional
  public HotArticleReviewRunView createPendingRun(
      String reviewId, String articleId, String jobId, HotArticleReviewRunCreateRequest input) {
    HotArticleReviewRunCreateRequest fields =
        input == null ? HotArticleReviewRunCreateRequest.builder().build() : input;
    ArticleAuditRunEntity run = new ArticleAuditRunEntity();
    run.setId(reviewId);
    run.setOutputId(resolveOutputId(articleId, fields.getDocumentId()));
    run.setJobId(jobId);
    run.setStatus(ReviewStatus.PENDING.getValue());
    run.setMode(StrUtil.isBlank(fields.getMode())
        ? ArticleAuditDomains.MODE_FULL
        : fields.getMode());
    run.setContentHash(fields.getContentHash() == null ? "" : fields.getContentHash());
    run.setStandardVersion(
        StrUtil.isBlank(fields.getStandardVersion())
            ? ArticleAuditDomains.STANDARD_HOT_NEWS_V1
            : fields.getStandardVersion());
    run.setBlockingFailed(false);
    run.setCreatedAt(LocalDateTime.now());
    runMapper.insert(run);
    log.info("热点审核待运行 id={} articleId={} jobId={}", reviewId, articleId, jobId);
    return toRunView(run, articleId);
  }

  /**
   * 持久化审核引擎结果：全量替换条目并回写 run 汇总。
   *
   * @param reviewId 审稿运行 ID
   * @param output   引擎结果；null 按空结果处理
   */
  @Override
  @Transactional
  public void persistReviewResult(String reviewId, HotArticleReviewEngineResult output) {
    HotArticleReviewEngineResult payload =
        output == null ? HotArticleReviewEngineResult.builder().build() : output;
    log.info("热点审核结果已落库 reviewId={} status={}", reviewId, payload.getStatus());
    ArticleAuditRunEntity run = runMapper.selectById(reviewId);
    if (run == null) {
      run = new ArticleAuditRunEntity();
      run.setId(reviewId);
      run.setJobId(reviewId);
      run.setStatus(ReviewStatus.PENDING.getValue());
      run.setStandardVersion(ArticleAuditDomains.STANDARD_HOT_NEWS_V1);
      run.setMode(ArticleAuditDomains.MODE_FULL);
      run.setCreatedAt(LocalDateTime.now());
      runMapper.insert(run);
      run = runMapper.selectById(reviewId);
    }
    LocalDateTime now = LocalDateTime.now();
    auditResultStore.write(run, toPersistMap(payload), now);
    run.setFinishedAt(now);
    runMapper.updateById(run);
  }

  /**
   * 文章最近一次审稿视图。
   *
   * @param articleId 热点文章 ID
   * @return 最新运行汇总；无记录时返回 none 占位视图
   */
  @Override
  public HotArticleAuditView latest(String articleId) {
    return latest(articleId, null);
  }

  @Override
  public HotArticleAuditView latest(String articleId, String documentId) {
    String outputId = resolveOutputId(articleId, documentId);
    LambdaQueryWrapper<ArticleAuditRunEntity> query = new LambdaQueryWrapper<ArticleAuditRunEntity>()
        .eq(ArticleAuditRunEntity::getStandardVersion, ArticleAuditDomains.STANDARD_HOT_NEWS_V1);
    if (StrUtil.isNotBlank(outputId)) {
      query.eq(ArticleAuditRunEntity::getOutputId, outputId);
    } else {
      return auditViewAssembler.noneStub(articleId);
    }
    ArticleAuditRunEntity run = runMapper.selectOne(query
        .orderByDesc(ArticleAuditRunEntity::getCreatedAt)
        .last("LIMIT 1"));
    if (run == null) {
      return auditViewAssembler.noneStub(articleId);
    }
    return auditViewAssembler.toAuditView(run, articleId);
  }

  /**
   * 加载运行；不存在抛业务异常。
   *
   * @param reviewId 审稿运行 ID
   * @return 运行视图
   * @throws BusinessException 记录不存在时 404
   */
  @Override
  public HotArticleReviewRunView requireRun(String reviewId) {
    ArticleAuditRunEntity run = runMapper.selectById(reviewId);
    if (run == null) {
      throw new BusinessException("review_not_found", "审核记录不存在", 404);
    }
    return toRunView(run, resolveArticleId(run.getOutputId()));
  }

  /**
   * 保存修复候选；若运行不存在则先补一条 repair 模式运行。
   *
   * @param reviewId    关联审稿运行 ID；空则新生成
   * @param articleId   文章 ID
   * @param jobId       修复任务 ID
   * @param content     候选正文
   * @param issueIds    关联问题 ID 列表（任意可 JSON 序列化对象）
   * @param diffSummary 差异摘要文案
   * @return 新建候选视图
   */
  @Override
  @Transactional
  public HotArticleRepairCandidateView saveRepairCandidate(
      String reviewId,
      String articleId,
      String jobId,
      String content,
      Object issueIds,
      String diffSummary) {
    String runId = StrUtil.isBlank(reviewId) ? IdsUtil.newId() : reviewId;
    String outputId = resolveOutputId(articleId, null);
    ArticleAuditRunEntity run = runMapper.selectById(runId);
    if (run == null) {
      run = new ArticleAuditRunEntity();
      run.setId(runId);
      run.setOutputId(outputId);
      run.setJobId(jobId);
      run.setStatus(ReviewStatus.PENDING.getValue());
      run.setMode(ArticleAuditDomains.MODE_REPAIR);
      run.setContentHash("");
      run.setStandardVersion(ArticleAuditDomains.STANDARD_HOT_NEWS_V1);
      run.setBlockingFailed(false);
      run.setCreatedAt(LocalDateTime.now());
      runMapper.insert(run);
    }
    HotArticleRepairCandidateEntity candidate = new HotArticleRepairCandidateEntity();
    candidate.setId(IdsUtil.newId());
    candidate.setAuditRunId(runId);
    candidate.setOutputId(StrUtil.blankToDefault(outputId, run.getOutputId()));
    candidate.setJobId(jobId);
    candidate.setStatus(ReviewStatus.PENDING.getValue());
    candidate.setDiffSummary(diffSummary == null ? "" : diffSummary);
    candidate.setCreatedAt(LocalDateTime.now());
    // 先写正文文件拿到 body_file_id，再落主行，最后写子表（FK 依赖主行）
    candidateStore.writeBody(candidate, content == null ? "" : content);
    candidate.setRepairAll(false);
    candidateMapper.insert(candidate);
    boolean repairAll = candidateStore.replaceIssues(candidate.getId(), issueIds);
    candidate.setRepairAll(repairAll);
    candidateMapper.updateById(candidate);
    return toCandidateView(candidate, articleId);
  }

  /**
   * 标记候选为 accepted。
   *
   * @param candidateId 候选主键
   * @return 更新后的候选视图
   * @throws BusinessException 候选不存在时 404
   */
  @Override
  @Transactional
  public HotArticleRepairCandidateView markCandidateAccepted(String candidateId) {
    HotArticleRepairCandidateEntity candidate = requireCandidateEntity(candidateId);
    candidate.setStatus(HotArticleRepairStatuses.ACCEPTED);
    candidate.setDecidedAt(LocalDateTime.now());
    candidateMapper.updateById(candidate);
    return toCandidateView(candidate, resolveArticleId(candidate.getOutputId()));
  }

  /**
   * 标记候选为 rejected。
   *
   * @param candidateId 候选主键
   * @return 拒绝确认视图（ok/status）
   * @throws BusinessException 候选不存在时 404
   */
  @Override
  @Transactional
  public HotArticleRepairRejectView rejectRepair(String candidateId) {
    HotArticleRepairCandidateEntity candidate = requireCandidateEntity(candidateId);
    candidate.setStatus(ReviewStatus.REJECTED.getValue());
    candidate.setDecidedAt(LocalDateTime.now());
    candidateMapper.updateById(candidate);
    return HotArticleRepairRejectView.builder()
        .ok(true)
        .candidateId(candidateId)
        .status(ReviewStatus.REJECTED.getValue())
        .build();
  }

  /**
   * 写入人工裁决字段到运行列（运行不存在时静默成功）。
   *
   * @param reviewId 审稿运行 ID
   * @param status   裁决状态（如 approved/rejected）
   * @param comment  裁决备注；null 转空串
   * @return 裁决确认视图
   */
  @Override
  @Transactional
  public HotArticleReviewDecisionView decide(String reviewId, String status, String comment) {
    ArticleAuditRunEntity run = runMapper.selectById(reviewId);
    if (run != null) {
      auditResultStore.writeDecision(run, status, comment);
      runMapper.updateById(run);
    }
    return HotArticleReviewDecisionView.builder()
        .ok(true)
        .reviewId(reviewId)
        .status(status)
        .build();
  }

  /**
   * 获取候选详情。
   *
   * @param candidateId 候选主键
   * @return 候选视图
   * @throws BusinessException 候选不存在时 404
   */
  @Override
  public HotArticleRepairCandidateView getCandidate(String candidateId) {
    HotArticleRepairCandidateEntity candidate = requireCandidateEntity(candidateId);
    return toCandidateView(candidate, resolveArticleId(candidate.getOutputId()));
  }

  /**
   * 按 ID 加载修复候选实体。
   *
   * @param candidateId 候选主键
   * @return 实体
   * @throws BusinessException 不存在时 404
   */
  private HotArticleRepairCandidateEntity requireCandidateEntity(String candidateId) {
    HotArticleRepairCandidateEntity candidate = candidateMapper.selectById(candidateId);
    if (candidate == null) {
      throw new BusinessException("repair_candidate_not_found", "修复候选不存在", 404);
    }
    return candidate;
  }

  /**
   * 运行实体转视图。
   *
   * @param run       运行实体
   * @param articleId 文章 id（API 契约）
   * @return 运行视图
   */
  private HotArticleReviewRunView toRunView(ArticleAuditRunEntity run, String articleId) {
    return HotArticleReviewRunView.builder()
        .id(run.getId())
        .articleId(articleId)
        .jobId(run.getJobId())
        .status(run.getStatus())
        .mode(run.getMode())
        .contentHash(run.getContentHash() == null ? "" : run.getContentHash())
        .standardVersion(
            run.getStandardVersion() == null
                ? ArticleAuditDomains.STANDARD_HOT_NEWS_V1
                : run.getStandardVersion())
        .blockingFailed(Boolean.TRUE.equals(run.getBlockingFailed()))
        .createdAt(run.getCreatedAt())
        .finishedAt(run.getFinishedAt())
        .build();
  }

  /**
   * 修复候选实体转视图。
   *
   * @param candidate 候选实体
   * @param articleId 文章 id
   * @return 候选视图
   */
  private HotArticleRepairCandidateView toCandidateView(
      HotArticleRepairCandidateEntity candidate, String articleId) {
    return HotArticleRepairCandidateView.builder()
        .id(candidate.getId())
        .reviewRunId(candidate.getAuditRunId())
        .articleId(articleId)
        .jobId(candidate.getJobId())
        .status(candidate.getStatus())
        .content(candidateStore.readBody(candidate))
        .diffSummary(candidate.getDiffSummary())
        .issueIdsJson(candidateStore.issueIdsJson(candidate.getId()))
        .createdAt(candidate.getCreatedAt())
        .decidedAt(candidate.getDecidedAt())
        .build();
  }

  /**
   * 引擎结果转可持久化 Map（优先使用 extra，再覆盖 status/results）。
   *
   * @param output 引擎结果
   * @return 可写入存储的 Map
   */
  private Map<String, Object> toPersistMap(HotArticleReviewEngineResult output) {
    Map<String, Object> payload =
        output.getExtra() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(output.getExtra());
    if (output.getStatus() != null) {
      payload.put(HotAuditResultKeys.STATUS, output.getStatus());
    }
    payload.put(HotAuditResultKeys.BLOCKING_FAILED, Boolean.TRUE.equals(output.getBlockingFailed()));
    if (output.getResults() != null) {
      payload.put(HotAuditResultKeys.RESULTS, output.getResults());
    }
    return payload;
  }

  /**
   * 解析产出 id：显式 documentId 优先，否则按热点公众号轨派生。
   *
   * @param articleId  文章 id
   * @param documentId 显式产出/文档 id
   * @return 产出 id；未知文章时 null
   */
  private String resolveOutputId(String articleId, String documentId) {
    if (StrUtil.isNotBlank(documentId)) {
      return documentId;
    }
    if (StrUtil.isNotBlank(articleId) && !ArticleAuditDomains.ARTICLE_UNKNOWN.equals(articleId)) {
      return content.resolvePublishingOutputId(articleId);
    }
    return null;
  }

  /**
   * 由产出 id 还原文章 id（API 契约）。
   *
   * @param outputId 产出 id
   * @return 文章 id；无法解析时返回占位
   */
  private String resolveArticleId(String outputId) {
    if (StrUtil.isBlank(outputId)) {
      return ArticleAuditDomains.ARTICLE_UNKNOWN;
    }
    WorkOutputRef ref = content.findOutputRef(outputId);
    if (ref != null && StrUtil.isNotBlank(ref.getWorkId())) {
      return ref.resolveArticleId();
    }
    return outputId;
  }
}
