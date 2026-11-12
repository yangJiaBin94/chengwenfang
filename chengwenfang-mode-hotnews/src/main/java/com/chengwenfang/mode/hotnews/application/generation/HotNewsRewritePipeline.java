package com.chengwenfang.mode.hotnews.application.generation;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.HotNewsArticleRewriteJobInput;
import com.chengwenfang.job.facade.dto.job.HotNewsArticleRewriteJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.mode.hotnews.application.HotNewsReferencePromptFormatter;
import com.chengwenfang.mode.hotnews.application.HotNewsResearchService;
import com.chengwenfang.mode.hotnews.application.HotNewsWorkContentService;
import com.chengwenfang.mode.hotnews.application.IllustrationFitnessJudge;
import com.chengwenfang.mode.hotnews.application.prompt.DraftOptimizeRewritePrompts;
import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleContentKinds;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsGenerationScopes;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsRewriteJobKeys;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationRunRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsIllustrationReuseReportView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 热点文章改写管道：按建议改写原稿，并裁定插图复用/重生。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsRewritePipeline {
  /** 热点资讯门面。 */
  private final HotNewsFacade hotNews;
  /** 正文读取。 */
  private final HotNewsWorkContentService contents;
  /** 选题资料查询。 */
  private final HotNewsResearchService research;
  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** 插图适配裁定。 */
  private final IllustrationFitnessJudge fitnessJudge;

  /**
   * 执行热点文章改写管道；文本 AI 失败或空结果时任务失败。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @throws Exception 改写或落库失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 hotNewsRewrite jobId={} entityId={}", job.getId(), job.getEntityId());
    HotNewsArticleRewriteJobInput input =
        JobPayloads.parse(job.getInputJson(), HotNewsArticleRewriteJobInput.class);
    String articleId = StrUtil.firstNonBlank(job.getEntityId(), stringOrNull(input.getArticleId()));
    String advice = StrUtil.nullToEmpty(input.getAdvice()).trim();
    String instruction = StrUtil.blankToDefault(
        input.getInstruction(),
        StrUtil.blankToDefault(advice, "优化热点文章表达，保持事实不变"));

    HotNewsArticleEntity article = contents.requireArticle(articleId);
    String oldDraft = contents.resolveHotBody(
        article, HotNewsArticleContentKinds.DRAFT, article.getDraftContent());
    if (StrUtil.isBlank(oldDraft)) {
      throw new BusinessException(HotNewsErrorCodes.DRAFT_EMPTY, "热点原稿为空，无法改写", 400);
    }

    List<HotNewsResearchItemView> references = StrUtil.isBlank(article.getTopicId())
        ? List.of()
        : research.listArticleReferenceItems(article.getTopicId());
    step(job, context, "rewrite", 35, "按建议改写热点原稿");
    String rewritten = requireAiText(
        buildRewritePrompt(oldDraft, advice, instruction, references),
        ModelCallScenes.HOT_NEWS_REWRITE);

    step(job, context, "illustration_fit", 70, "裁定插图是否复用");
    IllustrationFitnessJudge.MergeResult merged =
        fitnessJudge.merge(oldDraft, rewritten, StrUtil.blankToDefault(advice, instruction));
    HotNewsIllustrationReuseReportView reuseReport = merged.getReport();

    step(job, context, "save", 90, "落库改写稿");
    HotNewsArticleView saved = hotNews.applyAiRewrite(
        articleId, merged.getDraft(), StrUtil.blankToDefault(advice, instruction));

    step(job, context, "completed", 100, "改写完成");
    HotNewsArticleRewriteJobOutput output = HotNewsArticleRewriteJobOutput.from(
        saved, StrUtil.blankToDefault(advice, instruction), reuseReport);
    int regenCount = reuseReport == null ? 0 : reuseReport.getRegenRequiredCount();
    int reusedCount = reuseReport == null ? 0 : reuseReport.getReusedCount();
    if (regenCount > 0) {
      output.put(HotNewsRewriteJobKeys.IMAGES_REGEN_PENDING, true);
      // 改写已落库：配图入队失败不得拖垮改写任务
      try {
        JobView imagesJob = hotNews.startGenerationRun(
            articleId,
            HotNewsGenerationRunRequest.builder().scope(HotNewsGenerationScopes.IMAGES).build());
        output.put(HotNewsRewriteJobKeys.IMAGES_REGEN_JOB_ID, imagesJob.getId());
        output.put(
            HotNewsRewriteJobKeys.IMAGES_REGEN_HINT,
            "有 " + regenCount + " 张插图需重生；已入队仅配图任务（不重写正文）。");
      } catch (Exception exception) {
        log.warn(
            "改写后自动入队仅配图失败 articleId={} regen={} err={}",
            articleId,
            regenCount,
            exception.getMessage());
        output.put(
            HotNewsRewriteJobKeys.IMAGES_REGEN_HINT,
            "有 " + regenCount + " 张插图需重生；自动入队仅配图失败，请手动触发「只生成配套图片」。");
      }
    }
    log.info(
        "热点改写完成 articleId={} jobId={} reused={} regen={} imagesJob={}",
        articleId,
        job.getId(),
        reusedCount,
        regenCount,
        output.get(HotNewsRewriteJobKeys.IMAGES_REGEN_JOB_ID));
    context.completeWith(output);
  }

  /**
   * 组装改写用户提示。
   *
   * @param draft       原稿
   * @param advice      优化建议
   * @param instruction 补充指令
   * @param references  参考资料；可空
   * @return 用户提示
   */
  static String buildRewritePrompt(
      String draft,
      String advice,
      String instruction,
      List<HotNewsResearchItemView> references) {
    StringBuilder builder = new StringBuilder();
    builder.append("请按下列优化建议改写热点原稿。\n");
    if (StrUtil.isNotBlank(advice)) {
      builder.append("优化建议：\n").append(advice.trim()).append("\n\n");
    }
    if (StrUtil.isNotBlank(instruction) && !instruction.equals(advice)) {
      builder.append("补充指令：\n").append(instruction.trim()).append("\n\n");
    }
    builder.append(HotNewsReferencePromptFormatter.formatBlock(references)).append('\n');
    builder.append("原稿 Markdown：\n").append(draft);
    return builder.toString();
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
   * 调用文本 AI；失败或空结果抛业务异常。
   *
   * @param prompt 用户提示
   * @param scene  模型场景
   * @return 非空正文
   */
  private String requireAiText(String prompt, String scene) {
    String text;
    try {
      var result = ai.completeText(TextCompleteRequest.builder()
          .systemPrompt(DraftOptimizeRewritePrompts.SYSTEM)
          .prompt(prompt)
          .scene(scene)
          .timeoutSeconds(180)
          .build());
      text = result == null ? null : result.getText();
    } catch (RuntimeException exception) {
      log.warn("热点改写文本生成失败 scene={} err={}", scene, exception.getMessage());
      throw new BusinessException(
          HotNewsErrorCodes.OPTIMIZE_REWRITE_FAILED,
          "文本生成失败，scene=" + scene + "：" + exception.getMessage(),
          500);
    }
    if (StrUtil.isBlank(text)) {
      log.warn("热点改写文本生成结果为空 scene={}", scene);
      throw new BusinessException(
          HotNewsErrorCodes.OPTIMIZE_REWRITE_FAILED, "文本生成结果为空，scene=" + scene, 500);
    }
    return text.strip();
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
