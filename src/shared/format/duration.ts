/**
 * 执行页起止耗时文案：「耗时 N 秒」或「耗时 M 分 S 秒」；无 start 为「耗时 --」。
 * 输出格式与原 HotNewsExecutionPage / RadarCollectExecutionPage.duration 一致。
 */
export function formatElapsedDurationLabel(
  start?: string | null,
  end?: string | null,
) {
  if (!start) return "耗时 --";
  const elapsed = Math.max(
    0,
    (new Date(end || Date.now()).getTime() - new Date(start).getTime()) / 1000,
  );
  return `耗时 ${
    elapsed < 60
      ? `${Math.round(elapsed)} 秒`
      : `${Math.floor(elapsed / 60)} 分 ${Math.round(elapsed % 60)} 秒`
  }`;
}

/**
 * 工作流指标时长：0 秒 / N 秒 / M分S秒（≥60 秒时「分」「秒」之间无空格）。
 * 输出格式与原 WorkflowPage.formatDuration 一致。
 */
export function formatWorkflowMetricDuration(seconds: number) {
  if (!seconds) return "0 秒";
  return seconds >= 60
    ? `${Math.floor(seconds / 60)}分${Math.round(seconds % 60)}秒`
    : `${Math.round(seconds)} 秒`;
}

/**
 * 视频时长：m:ss 或 h:mm:ss；无效返回 null。
 * 输出格式与原 HotNewsResearchPage.formatVideoDuration 一致。
 */
export function formatVideoDuration(
  seconds: number | null | undefined,
): string | null {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) {
    return null;
  }
  const total = Math.round(seconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  const mm = hours > 0 ? String(minutes).padStart(2, "0") : String(minutes);
  const ss = String(secs).padStart(2, "0");
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}
