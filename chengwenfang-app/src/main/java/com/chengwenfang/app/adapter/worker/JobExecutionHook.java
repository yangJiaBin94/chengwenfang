package com.chengwenfang.app.adapter.worker;

import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;

/** 可选的 Job 执行拦截钩子，供集成测试使用。 */
public interface JobExecutionHook {
  /**
   * article_repair 执行前钩子。
   *
   * @param job     任务实体
   * @param context 执行上下文
   * @return true 表示处理器应停止（例如阻塞期间已取消）
   * @throws Exception 钩子实现失败时向上抛出
   */
  default boolean beforeArticleRepair(JobEntity job, JobExecutionContext context) throws Exception {
    return false;
  }
}
