package com.workbench.mode.course.application.generation;

import cn.hutool.core.util.StrUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.dto.view.ArticleView;
import com.workbench.content.facade.enums.ArticleKind;
import com.workbench.job.domain.entity.JobEntity;
import com.workbench.job.facade.JobExecutionContext;
import com.workbench.job.facade.dto.job.WechatArticleGenerationJobOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 公众号文章转换管道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatArticlePipeline {
  /** 内容门面。 */
  private final ContentFacade content;
  /** 文本 AI。 */
  private final AiTextFacade ai;

  /**
   * 执行公众号文章转换管道。
   *
   * @param job     任务实体（entityId=课程 ID）
   * @param context 执行上下文
   * @throws Exception 生成或持久化失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 wechatArticleGeneration jobId={} entityId={}", job.getId(), job.getEntityId());
    step(job, context, "wechat", 40, "生成公众号文章");
    String entityId = job.getEntityId();
    String markdown = requireAiText(
        "请生成公众号文章：" + entityId,
        ArticleKind.WECHAT_ARTICLE.getValue());
    ArticleView article = content.generateArticleKind(
        entityId, ArticleKind.WECHAT_ARTICLE.getValue(), markdown);
    context.completeWith(WechatArticleGenerationJobOutput.of(
        article, article.getCourseId() == null ? entityId : article.getCourseId()));
    step(job, context, "wechat", 100, "公众号文章已就绪");
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
}
