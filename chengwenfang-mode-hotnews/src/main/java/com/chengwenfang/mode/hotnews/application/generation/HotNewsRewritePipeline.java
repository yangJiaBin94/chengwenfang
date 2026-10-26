package com.chengwenfang.mode.hotnews.application.generation;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.HotNewsArticleRewriteJobInput;
import com.chengwenfang.job.facade.dto.job.HotNewsArticleRewriteJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 热点文章改写管道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsRewritePipeline {
  /** 热点资讯门面。 */
  private final HotNewsFacade hotNews;
  /** 文本 AI。 */
  private final AiTextFacade ai;

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
    String instruction = StrUtil.blankToDefault(
        input.getInstruction(), "优化热点文章表达，保持事实不变");

    step(job, context, "rewrite", 40, "改写热点文章");
    String aiText = requireAiText(
        "按以下要求改写文章：" + instruction + "\n文章ID=" + articleId,
        ModelCallScenes.HOT_NEWS_REWRITE);
    // 落库改写稿并写入生成设置中的指令与时间
    HotNewsArticleView saved = hotNews.applyAiRewrite(articleId, aiText, instruction);
    step(job, context, "completed", 100, "改写完成");
    context.completeWith(HotNewsArticleRewriteJobOutput.from(saved, instruction));
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
