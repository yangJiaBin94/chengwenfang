package com.chengwenfang.app.adapter.worker;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.JobHandler;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.job.KnowledgeReindexAllJobInput;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import com.chengwenfang.knowledge.facade.dto.request.KnowledgeReindexRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 知识库全量重建索引任务处理器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeReindexHandler implements JobHandler {

  /** 知识库门面。 */
  private final KnowledgeFacade knowledgeFacade;

  /** {@inheritDoc} */
  @Override
  public String type() {
    return JobTypes.KNOWLEDGE_REINDEX_ALL;
  }

  /**
   * 执行全量或按来源重建。
   *
   * @param job     任务实体
   * @param context 执行上下文
   */
  @Override
  public void execute(JobEntity job, JobExecutionContext context) throws Exception {
    KnowledgeReindexAllJobInput input =
        JSONUtil.toBean(job.getInputJson(), KnowledgeReindexAllJobInput.class);
    KnowledgeSourceKind sourceKind = null;
    if (input != null && StrUtil.isNotBlank(input.getSourceKind())) {
      sourceKind = KnowledgeSourceKind.from(input.getSourceKind());
    }
    KnowledgeReindexRequest request = KnowledgeReindexRequest.builder()
        .sourceKind(sourceKind)
        .build();
    log.info("知识库全量重建开始 jobId={} sourceKind={}", job.getId(), sourceKind);
    knowledgeFacade.runReindexAll(request);
    log.info("知识库全量重建结束 jobId={}", job.getId());
  }
}
