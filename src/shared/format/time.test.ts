import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  formatConfirmTime,
  formatExecutionNodeTime,
  formatExecutionTime,
  formatHomeJobTime,
  formatNotificationRelativeTime,
  formatPublishDate,
  formatWorkflowDateTime,
} from "./time";

describe("formatNotificationRelativeTime", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-15T12:00:00+08:00"));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("matches original NotificationCenter relative buckets", () => {
    expect(formatNotificationRelativeTime("2026-06-15T11:59:30+08:00")).toBe(
      "刚刚",
    );
    expect(formatNotificationRelativeTime("2026-06-15T11:30:00+08:00")).toBe(
      "30 分钟前",
    );
    expect(formatNotificationRelativeTime("2026-06-15T09:00:00+08:00")).toBe(
      "3 小时前",
    );
    const older = formatNotificationRelativeTime("2026-06-10T12:00:00+08:00");
    expect(older).toBe(new Date("2026-06-10T12:00:00+08:00").toLocaleDateString("zh-CN"));
  });
});

describe("formatHomeJobTime", () => {
  it("matches original App short format", () => {
    const value = "2026-03-08T15:04:00+08:00";
    expect(formatHomeJobTime(value)).toBe(
      new Intl.DateTimeFormat("zh-CN", {
        month: "numeric",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date(value)),
    );
  });

  it("returns dash for invalid", () => {
    expect(formatHomeJobTime("not-a-date")).toBe("-");
  });
});

describe("formatExecutionTime", () => {
  it("formats ISO without zone as UTC then Asia/Shanghai", () => {
    // 2026-01-01T00:00:00Z → 北京 08:00:00
    expect(formatExecutionTime("2026-01-01T00:00:00")).toBe(
      new Intl.DateTimeFormat("zh-CN", {
        timeZone: "Asia/Shanghai",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      }).format(new Date("2026-01-01T00:00:00Z")),
    );
  });

  it("returns dash for empty", () => {
    expect(formatExecutionTime(null)).toBe("-");
    expect(formatExecutionTime("")).toBe("-");
  });
});

describe("formatWorkflowDateTime", () => {
  it("matches original WorkflowPage.formatDate", () => {
    const value = "2026-04-01T10:20:30+08:00";
    expect(formatWorkflowDateTime(value)).toBe(
      new Intl.DateTimeFormat("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      }).format(new Date(value)),
    );
  });
});

describe("formatExecutionNodeTime", () => {
  it("matches original execution page time helper", () => {
    const value = "2026-04-01T10:20:30+08:00";
    expect(formatExecutionNodeTime(value)).toBe(
      new Intl.DateTimeFormat("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      }).format(new Date(value)),
    );
    expect(formatExecutionNodeTime(null)).toBe("--");
  });
});

describe("formatPublishDate", () => {
  it("matches original HotNewsPage publish date", () => {
    expect(formatPublishDate(null)).toBe("未知");
    expect(formatPublishDate("bad")).toBe("未知");
    const value = "2026-05-01T00:00:00+08:00";
    expect(formatPublishDate(value)).toBe(
      new Date(value).toLocaleString("zh-CN", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "numeric",
        day: "numeric",
      }),
    );
  });
});

describe("formatConfirmTime", () => {
  it("matches original HotNewsSelectedPage confirm time", () => {
    expect(formatConfirmTime(null)).toBe("确认时间未知");
    const value = "2026-05-01T14:30:00+08:00";
    expect(formatConfirmTime(value)).toBe(
      new Date(value).toLocaleString("zh-CN", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      }),
    );
  });
});
