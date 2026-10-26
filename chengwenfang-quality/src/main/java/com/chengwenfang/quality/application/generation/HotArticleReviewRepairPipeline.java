package com.chengwenfang.quality.application.generation;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.HotArticleReviewRepairJobInput;
import com.chengwenfang.job.facade.dto.job.HotArticleReviewRepairJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.quality.facade.HotArticleReviewFacade;
import com.chengwenfang.quality.facade.enums.ReviewStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 热点文章审核修复管道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleReviewRepairPipeline {
  /** 热点资讯门面。 */
  private final HotNewsFacade hotNews;
  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** 热点审稿门面。 */
  private final HotArticleReviewFacade hotArticleReviews;

  /**
   * 热点文章审核修复：调用文本 AI 生成修复稿；失败或空结果时任务失败。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @throws Exception AI 失败或候选保存失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 hotArticleReviewRepair jobId={} entityId={}", job.getId(), job.getEntityId());
    HotArticleReviewRepairJobInput input = HotArticleReviewRepairJobInput.from(
        JobPayloads.parseMap(job.getInputJson()));
    String reviewId = StrUtil.firstNonBlank(stringOrNull(input.get("reviewId")), stringOrNull(job.getEntityId()));
    String articleId = StrUtil.firstNonBlank(stringOrNull(input.get("articleId")), stringOrNull(job.getEntityId()));
    step(job, context, "repair", 30, "调用模型修复");
    String original = String.valueOf(input.getOrDefault("content", ""));
    if (original.isBlank()) {
      HotNewsArticleView article = hotNews.getHotArticle(articleId);
      original = article.getContent() == null ? "" : article.getContent();
    }
    String repaired = requireAiText(
        "请修复以下热点文章，保持事实不变，输出完整 Markdown：\n" + original,
        ModelCallScenes.HOT_NEWS_REVIEW_REPAIR);
    var candidate = hotArticleReviews.saveRepairCandidate(
        reviewId == null ? job.getId() : reviewId,
        articleId,
        job.getId(),
        repaired,
        input.getOrDefault("issueIds", List.of()),
        "热点文章内容 AI 修复");
    step(job, context, "revalidate", 80, "记录修复候选");
    context.completeWith(HotArticleReviewRepairJobOutput.builder()
        .candidateId(candidate.getId())
        .status(ReviewStatus.PENDING.getValue())
        .articleId(articleId)
        .reviewId(reviewId)
        .content(repaired)
        .build());
    step(job, context, "revalidate", 100, "修复候选已就绪");
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
   * 文本补全；失败或空结果直接失败。
   *
   * @param prompt 提示词
   * @param scene  审计场景
   * @return 模型文本
   * @throws IllegalStateException 调用失败或结果为空
   */
  private String requireAiText(String prompt, String scene) {
    String text;
    try {
      var result = ai.completeText(new TextCompleteRequest(prompt, scene));
      text = result == null ? null : result.getText();
    } catch (RuntimeException exception) {
      throw new IllegalStateException("文本生成失败，scene=" + scene + "：" + exception.getMessage(), exception);
    }
    if (StrUtil.isBlank(text)) {
      throw new IllegalStateException("文本生成结果为空，scene=" + scene);
    }
    return text;
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

}
