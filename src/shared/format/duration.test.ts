import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  formatElapsedDurationLabel,
  formatVideoDuration,
  formatWorkflowMetricDuration,
} from "./duration";

describe("formatElapsedDurationLabel", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-01-01T00:01:30Z"));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("matches original execution page duration labels", () => {
    expect(formatElapsedDurationLabel(null)).toBe("耗时 --");
    expect(
      formatElapsedDurationLabel(
        "2026-01-01T00:01:00Z",
        "2026-01-01T00:01:25Z",
      ),
    ).toBe("耗时 25 秒");
    expect(
      formatElapsedDurationLabel(
        "2026-01-01T00:00:00Z",
        "2026-01-01T00:01:30Z",
      ),
    ).toBe("耗时 1 分 30 秒");
  });
});

describe("formatWorkflowMetricDuration", () => {
  it("matches original WorkflowPage.formatDuration", () => {
    expect(formatWorkflowMetricDuration(0)).toBe("0 秒");
    expect(formatWorkflowMetricDuration(12.4)).toBe("12 秒");
    expect(formatWorkflowMetricDuration(90)).toBe("1分30秒");
  });
});

describe("formatVideoDuration", () => {
  it("matches original HotNewsResearchPage expectations", () => {
    expect(formatVideoDuration(0)).toBe("0:00");
    expect(formatVideoDuration(65)).toBe("1:05");
    expect(formatVideoDuration(3600)).toBe("1:00:00");
    expect(formatVideoDuration(null)).toBeNull();
  });
});
