import { describe, expect, it } from "vitest";
import {
  buildHotNewsExecutionAttemptBatches,
  getHotNewsStageDisplayStatus,
  normalizeHotNewsNodeStatus,
  resolveHotNewsImageProgressMessage,
  resolveHotNewsWorkflowRunId,
} from "./HotNewsExecutionPage";

describe("getHotNewsStageDisplayStatus", () => {
  it("keeps stages after the running stage pending even when old node attempts succeeded", () => {
    expect(
      getHotNewsStageDisplayStatus({
        persistedState: "succeeded",
        jobStatus: "running",
        stageIndex: 6,
        currentStageIndex: 5,
        isCurrentStage: false,
      }),
    ).toBe("pending");
  });

  it("shows the effective current stage as running before persisted progress catches up", () => {
    expect(
      getHotNewsStageDisplayStatus({
        persistedState: "pending",
        jobStatus: "running",
        stageIndex: 5,
        currentStageIndex: 5,
        isCurrentStage: true,
      }),
    ).toBe("running");
  });

  it("prefers persisted success over a stale running job flag", () => {
    expect(
      getHotNewsStageDisplayStatus({
        persistedState: "succeeded",
        jobStatus: "running",
        stageIndex: 10,
        currentStageIndex: 10,
        isCurrentStage: true,
      }),
    ).toBe("succeeded");
  });

  it("marks all stages complete when the job already succeeded", () => {
    expect(
      getHotNewsStageDisplayStatus({
        persistedState: "pending",
        jobStatus: "succeeded",
        stageIndex: 0,
        currentStageIndex: -1,
        isCurrentStage: false,
      }),
    ).toBe("succeeded");
    expect(
      getHotNewsStageDisplayStatus({
        persistedState: "pending",
        jobStatus: "succeeded",
        stageIndex: 11,
        currentStageIndex: -1,
        isCurrentStage: false,
      }),
    ).toBe("succeeded");
  });

  it("shows a requeued retry as running", () => {
    expect(
      getHotNewsStageDisplayStatus({
        persistedState: "pending",
        jobStatus: "running",
        stageIndex: 0,
        currentStageIndex: 0,
        isCurrentStage: true,
      }),
    ).toBe("running");
  });
});

describe("resolveHotNewsWorkflowRunId", () => {
  it("reads runId from job output when workflowRunId is missing", () => {
    expect(
      resolveHotNewsWorkflowRunId({
        workflowRunId: null,
        output: { runId: "2875415d-run" },
      }),
    ).toBe("2875415d-run");
  });
});

describe("normalizeHotNewsNodeStatus", () => {
  it("treats a running node with a finish timestamp as completed", () => {
    expect(
      normalizeHotNewsNodeStatus({
        status: "running",
        finishedAt: "2026-08-28T06:00:00Z",
      }),
    ).toBe("succeeded");
  });
});

describe("resolveHotNewsImageProgressMessage", () => {
  it("prefers the highest slot index so retry does not flash earlier slots", () => {
    expect(
      resolveHotNewsImageProgressMessage([
        {
          id: 1,
          step: "hot_news_generate_images",
          message: "正在生成第 1/4 张",
        },
        {
          id: 3,
          step: "hot_news_generate_images",
          message: "正在生成第 3/4 张",
        },
        {
          id: 4,
          step: "hot_news_generate_images",
          message: "正在生成第 1/4 张",
        },
      ]),
    ).toBe("正在生成第 3/4 张");
  });
});

describe("buildHotNewsExecutionAttemptBatches", () => {
  it("splits retries into selectable batches like course mode", () => {
    const batches = buildHotNewsExecutionAttemptBatches(
      [
        {
          id: 1,
          eventType: "progress",
          step: "hot_news_load_context",
          progress: 10,
          message: "start",
          createdAt: "2026-10-10T01:00:00Z",
        },
        {
          id: 2,
          eventType: "error",
          step: "hot_news_generate_images",
          progress: 70,
          message: "fail",
          createdAt: "2026-10-10T01:10:00Z",
        },
        {
          id: 3,
          eventType: "queued",
          step: "retry_queued",
          progress: 0,
          message: "重试",
          createdAt: "2026-10-10T01:11:00Z",
        },
        {
          id: 4,
          eventType: "progress",
          step: "hot_news_generate_images",
          progress: 75,
          message: "正在生成第 3/4 张",
          createdAt: "2026-10-10T01:12:00Z",
        },
      ],
      [],
    );
    expect(batches).toHaveLength(2);
    expect(batches[0]?.number).toBe(1);
    expect(batches[1]?.number).toBe(2);
    expect(batches[1]?.events.map((event) => event.id)).toEqual([3, 4]);
  });
});
