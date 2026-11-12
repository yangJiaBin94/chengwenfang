package com.chengwenfang.app.adapter.worker;

import cn.hutool.json.JSONUtil;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobHandler;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.KnowledgeIndexDocumentJobInput;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 知识库单文档索引任务处理器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexHandler implements JobHandler {

  /** 知识库门面。 */
  private final KnowledgeFacade knowledgeFacade;

  /** {@inheritDoc} */
  @Override
  public String type() {
    return JobTypes.KNOWLEDGE_INDEX_DOCUMENT;
  }

  /**
   * 执行单文档索引。
   *
   * @param job     任务实体
   * @param context 执行上下文
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) throws Exception {
    KnowledgeIndexDocumentJobInput input =
        JSONUtil.toBean(job.getInputJson(), KnowledgeIndexDocumentJobInput.class);
    String documentId = input == null ? job.getEntityId() : input.getDocumentId();
    log.info("知识库索引任务开始 jobId={} documentId={}", job.getId(), documentId);
    knowledgeFacade.indexDocumentNow(documentId);
    log.info("知识库索引任务完成 jobId={}", job.getId());
  }
}
