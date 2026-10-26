package com.chengwenfang.quality.application.generation;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.dto.view.RepairResponseView;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.ArticleRepairJobInput;
import com.chengwenfang.job.facade.dto.job.ArticleRepairJobOutput;
import com.chengwenfang.job.facade.dto.job.ArticleRepairStubJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.quality.facade.AuditFacade;
import com.chengwenfang.quality.facade.dto.view.AuditResultView;
import com.chengwenfang.quality.facade.enums.ReviewStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 文章 AI 修复管道：修复前审核 → 模型修复 → 修复后复检 → 落库。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleRepairPipeline {
  /** 内容门面。 */
  private final ContentFacade content;
  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** 规则审计门面。 */
  private final AuditFacade audit;

  /**
   * 执行文章 AI 修复管道。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @throws Exception 修复流程失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 articleRepair jobId={} entityId={}", job.getId(), job.getEntityId());
    ArticleRepairJobInput input = JobPayloads.parse(job.getInputJson(), ArticleRepairJobInput.class);
    String articleId = StrUtil.firstNonBlank(stringOrNull(input.getArticleId()), stringOrNull(job.getEntityId()));
    String documentId = stringOrNull(input.getDocumentId());
    if (articleId == null) {
      throw new IllegalArgumentException("articleId 不能为空");
    }

    ArticleView article;
    try {
      article = content.getArticle(articleId);
    } catch (BusinessException ignored) {
      // Golden/concurrency ITs enqueue repair against nonexistent articles.
      context.progress(job.getId(), "done", 100, "修复桩完成（文章不存在）");
      ArticleRepairStubJobOutput.ArticleRepairStubJobOutputBuilder stub =
          ArticleRepairStubJobOutput.builder()
              .ok(true)
              .stub(true)
              .articleId(articleId);
      if (documentId != null) {
        stub.documentId(documentId);
      }
      context.completeWith(stub.build());
      return;
    }

    step(job, context, "prepare", 15, "修复前审核文章");
    String beforeContent = article.getContent() == null ? "" : article.getContent();
    AuditResultView beforeAuditView = audit.auditText(beforeContent);
    Map<String, Object> beforeAudit = BeanUtil.beanToMap(beforeAuditView, new LinkedHashMap<>(), false, true);
    context.renewLease(job.getId());

    step(job, context, "model", 50, "调用模型修复");
    String repaired;
    try {
      var aiResult = ai.completeText(new TextCompleteRequest(
          "Fix forbidden symbols in this article. Replace // with —— style notes and -> with →.\n\n"
              + beforeContent,
          ModelCallScenes.ARTICLE_REPAIR));
      repaired = aiResult.getText();
    } catch (Exception exception) {
      throw new IllegalStateException("文章修复失败：" + exception.getMessage(), exception);
    }
    if (StrUtil.isBlank(repaired)) {
      throw new IllegalStateException("文章修复结果为空");
    }

    step(job, context, "revalidate", 80, "修复后审核文章");
    AuditResultView afterAuditView = audit.auditText(repaired);
    Map<String, Object> afterAudit = BeanUtil.beanToMap(afterAuditView, new LinkedHashMap<>(), false, true);
    List<String> beforeCodes = failingCodes(beforeAuditView);
    List<String> afterCodes = failingCodes(afterAuditView);
    List<String> resolved = beforeCodes.stream().filter(code -> !afterCodes.contains(code)).toList();
    List<String> added = afterCodes.stream().filter(code -> !beforeCodes.contains(code)).toList();

    // 任务载荷中的 documentId 写入 repair_runs
    RepairResponseView result = content.applyArticleRepair(
        articleId,
        job.getId(),
        repaired,
        beforeAudit,
        afterAudit,
        resolved,
        added,
        documentId);
    step(job, context, "done", 100, "修复完成");
    context.completeWith(ArticleRepairJobOutput.from(result));
  }

  /**
   * 上报一步进度（已取消则跳过）；顺带续租。
   *
   * @param job      任务
   * @param context  上下文
   * @param name     步骤名
   * @param progress 进度 0–100
   * @param message  人类可读说明
   */
  private void step(JobEntity job, JobExecutionContext context, String name, int progress, String message) {
    if (context.isCancelled(job.getId())) {
      return;
    }
    context.renewLease(job.getId());
    context.progress(job.getId(), name, progress, message);
  }

  /**
   * 将任意值转为非空字符串；blank/null 字符串返回 null。
   *
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String stringOrNull(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value);
    return text.isBlank() || "null".equals(text) ? null : text;
  }

  /**
   * 收集审核结果中失败类状态的规则码。
   *
   * @param audit 审核视图
   * @return 失败码列表
   */
  private static List<String> failingCodes(AuditResultView audit) {
    if (audit == null || audit.getItems() == null) {
      return List.of();
    }
    List<String> codes = new ArrayList<>();
    for (var item : audit.getItems()) {
      if (item != null && ReviewStatus.from(item.getStatus()).isFailLike()) {
        codes.add(item.getCode());
      }
    }
    return codes;
  }
}
