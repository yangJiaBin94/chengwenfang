package com.chengwenfang.mode.course.application.generation;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.enums.ArticleKind;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.CourseDraftGenerationJobOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 课程草稿生成管道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseDraftPipeline {
  /** 内容门面。 */
  private final ContentFacade content;
  /** 文本 AI。 */
  private final AiTextFacade ai;

  /**
   * 执行课程草稿生成管道。
   *
   * @param job     任务实体（entityId=课程 ID）
   * @param context 执行上下文
   * @throws Exception 生成或持久化失败
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 courseDraftGeneration jobId={} entityId={}", job.getId(), job.getEntityId());
    step(job, context, "draft", 40, "生成课程原稿");
    String entityId = job.getEntityId();
    String markdown = requireAiText(
        "请生成课程原稿：" + entityId,
        ArticleKind.COURSE_DRAFT.getValue());
    ArticleView article = content.generateArticleKind(
        entityId, ArticleKind.COURSE_DRAFT.getValue(), markdown);
    context.completeWith(CourseDraftGenerationJobOutput.of(
        article, article.getCourseId() == null ? entityId : article.getCourseId()));
    step(job, context, "draft", 100, "课程原稿已就绪");
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
