package com.chengwenfang.app.adapter.worker;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobHandler;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.KnowledgeReconcileJobInput;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 知识库三存储对账任务处理器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeReconcileHandler implements JobHandler {

  /** 知识库门面。 */
  private final KnowledgeFacade knowledgeFacade;

  /** {@inheritDoc} */
  @Override
  public String type() {
    return JobTypes.KNOWLEDGE_RECONCILE;
  }

  /**
   * 执行对账。
   *
   * @param job     任务
   * @param context 上下文
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) {
    boolean dryRun = false;
    if (StrUtil.isNotBlank(job.getInputJson())) {
      KnowledgeReconcileJobInput input =
          JSONUtil.toBean(job.getInputJson(), KnowledgeReconcileJobInput.class);
      dryRun = input != null && Boolean.TRUE.equals(input.getDryRun());
    }
    log.info("知识库对账开始 jobId={} dryRun={}", job.getId(), dryRun);
    knowledgeFacade.runReconcile(dryRun);
    log.info("知识库对账结束 jobId={}", job.getId());
  }
}
