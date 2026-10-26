/**
 * 通知中心相对时间：刚刚 / N 分钟前 / N 小时前 / 本地日期。
 * 输出格式与原 NotificationCenter.formatTime 一致。
 */
export function formatNotificationRelativeTime(value: string) {
  const date = new Date(value);
  const diff = Date.now() - date.getTime();
  if (diff < 60_000) return "刚刚";
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`;
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`;
  return date.toLocaleDateString("zh-CN");
}

/**
 * 首页任务时间短格式：M/D HH:mm。
 * 输出格式与原 App.formatHomeJobTime 一致。
 */
export function formatHomeJobTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

/**
 * 执行轨迹时间：北京时区 MM/DD HH:mm:ss；无效返回 "-"。
 * 输出格式与原 ExecutionTrajectoryPage.formatExecutionTime 一致。
 */
export function formatExecutionTime(value?: string | number | null) {
  if (value == null || value === "") return "-";
  // SSE 可能发 epoch millis；历史 API 常发无时区后缀的 UTC 串
  let date: Date;
  if (typeof value === "number") {
    date = new Date(value);
  } else if (/^\d{11,13}$/.test(value.trim())) {
    date = new Date(Number(value));
  } else {
    const normalized = /(?:Z|[+-]\d{2}:\d{2})$/.test(value)
      ? value
      : `${value}Z`;
    date = new Date(normalized);
  }
  if (Number.isNaN(date.getTime())) return "-";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

/**
 * 工作流列表/运行时间：MM/DD HH:mm（无秒）。
 * 输出格式与原 WorkflowPage.formatDate 一致。
 */
export function formatWorkflowDateTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

/**
 * 执行页节点时间：MM/DD HH:mm:ss（本地，无时区强制）；空为 "--"。
 * 输出格式与原 HotNewsExecutionPage / RadarCollectExecutionPage.time 一致。
 */
export function formatExecutionNodeTime(value?: string | null) {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      }).format(new Date(value))
    : "--";
}

/**
 * 热点雷达发布时间：年月日（上海时区）；空/无效为「未知」。
 * 输出格式与原 HotNewsPage.formatPublishDate 一致。
 */
export function formatPublishDate(value?: string | null): string {
  if (!value) return "未知";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "未知";
  return date.toLocaleString("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "numeric",
    day: "numeric",
  });
}

/**
 * 已选选题确认时间：YYYY/MM/DD HH:mm（上海时区）。
 * 输出格式与原 HotNewsSelectedPage.formatConfirmTime 一致。
 */
export function formatConfirmTime(iso: string | null | undefined): string {
  if (!iso) {
    return "确认时间未知";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "确认时间未知";
  }
  return date.toLocaleString("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * 通用本地完整时间（zh-CN toLocaleString），用于工作区等一处展示。
 */
export function formatLocaleDateTime(value: string | number | Date) {
  return new Date(value).toLocaleString("zh-CN");
}

/**
 * 信源最近成功时间：M/D HH:mm。
 * 输出格式与原 SourceManagementPage 内联 options 一致。
 */
export function formatSourceSuccessTime(value: string) {
  return new Date(value).toLocaleString("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}
