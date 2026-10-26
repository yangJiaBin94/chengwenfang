package com.workbench.sourcing.application.videoparse;

import cn.hutool.core.util.StrUtil;
import com.workbench.job.facade.JobExecutionContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频解析并行进度上报：串行化写库、百分比只升不降；资料池与信源 Job 共用。
 */
public final class VideoParseProgressSupport {

  private VideoParseProgressSupport() {}

  /**
   * 并行场景下上报进度。
   * <p>文案按「已完成 + 并行进行中」汇总，避免单条序号歧义。
   *
   * @param context      任务上下文
   * @param jobId        任务 ID
   * @param stepKey      步骤 key
   * @param total        总条数
   * @param completed    已完成条数
   * @param activeTitles 进行中条目短标题（itemId → title）
   * @param maxProgress  已上报最大百分比
   * @param progressLock 进度写锁
   */
  public static void reportLocked(
      JobExecutionContext context,
      String jobId,
      String stepKey,
      int total,
      int completed,
      Map<String, String> activeTitles,
      AtomicInteger maxProgress,
      Object progressLock) {
    synchronized (progressLock) {
      int running = activeTitles == null ? 0 : activeTitles.size();
      List<String> titles = activeTitles == null
          ? List.of()
          : new ArrayList<>(activeTitles.values());
      String runningPart;
      if (running <= 0) {
        runningPart = completed >= total ? "即将结束" : "排队等待中";
      } else if (running == 1) {
        runningPart = "进行中 1 条";
      } else {
        runningPart = "并行解析中 " + running + " 条";
      }
      int rawProgress = parallelPercent(total, completed, running);
      int progress = maxProgress.updateAndGet(previous -> Math.max(previous, rawProgress));
      String message = "共"
          + total
          + "条 · 已完成"
          + completed
          + "条 · "
          + runningPart
          + " · "
          + progress
          + "%";
      Map<String, Object> meta = new LinkedHashMap<>();
      meta.put("total", total);
      meta.put("completed", completed);
      meta.put("running", running);
      meta.put("parallel", true);
      meta.put("titles", titles);
      meta.put("step", StrUtil.nullToEmpty(stepKey));
      meta.put("percent", progress);
      context.progress(jobId, stepKey, progress, message, meta);
    }
  }

  /**
   * 并行进度估算：已完成 + 进行中按 0.4 折算（0–99；整批结束时另报 100）。
   *
   * @param total     总条数
   * @param completed 已完成条数
   * @param running   进行中条数
   * @return 百分比
   */
  public static int parallelPercent(int total, int completed, int running) {
    if (total <= 0) {
      return 0;
    }
    double inFlight = Math.max(0, Math.min(running, total - completed)) * 0.4;
    int percent = (int) Math.round(((completed + inFlight) * 100.0) / total);
    return Math.max(0, Math.min(99, percent));
  }
}
