package com.chengwenfang.job.facade;

import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.constant.JobTypes;

/**
 * 某类任务的执行器；由 JobWorkerRunner 按 {@link #type()} 路由调用。
 */
public interface JobHandler {
  /** 处理的任务类型字符串，须与 {@link JobTypes} / 任务 type 字段一致。 */
  String type();

  /**
   * 执行任务。
   * <p>成功后可通过 {@link JobExecutionContext#completeWith} 设置输出；
   * 未自行 succeed/fail 时由 Worker 在返回后标记成功。
   *
   * @param job 已认领的任务实体（含 input_json）
   * @param context 进度、续租与取消检测
   */
  void execute(JobEntity job, JobExecutionContext context) throws Exception;
}
