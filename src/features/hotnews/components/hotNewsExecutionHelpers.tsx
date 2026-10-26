import {
  AlertTriangle,
  ArrowLeft,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  History,
  RefreshCcw,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { JobData, JobEventData, WorkflowNodeData } from "../../../shared/api/jobs";
import {
  loadJobEventHistory,
  loadJobNodes,
  retryFailedJob,
} from "../../../shared/api/jobs";
import { formatElapsedDurationLabel } from "../../../shared/format/duration";
import { formatExecutionNodeTime } from "../../../shared/format/time";
import { usePolling } from "../../../shared/hooks/usePolling";
import {
  getHotNewsArticle,
  getHotNewsArticleSkeleton,
  listHotNewsArticleGenerationRuns,
  resolveHotNewsDocumentIdFromLocation,
  withDocumentIdQuery,
} from "../api";
import {
  toImageStepFailureMessage,
  toUserFailureMessage,
} from "../../../shared/job-failure-message";
import type {
  HotNewsArticle,
  HotNewsArticleSkeleton,
} from "../api";
import {
  buildExecutionAttemptBatches as buildSharedExecutionAttemptBatches,
  isHotNewsAttemptBoundary as sharedIsHotNewsAttemptBoundary,
  type ExecutionAttemptBatch,
} from "../../../shared/execution/attemptBatches";

export type { ExecutionAttemptBatch };
export type HotNewsWorkflowNode = WorkflowNodeData;

export const stages = [
  [
    "hot_news_load_context",
    "读取文章骨架与资料",
    "读取骨架结构、选题、资料线索与字数/配图档。",
  ],
  [
    "hot_news_load_skills",
    "加载热点文章 Skill",
    "固定本流程 Skill、提示词与版本快照。",
  ],
  [
    "hot_news_prepare_facts",
    "整理资料线索为事实包",
    "以骨架为轴整理事实边界，资料仅作线索。",
  ],
  [
    "hot_news_generate_draft",
    "按骨架生成热点原稿",
    "跟随骨架生成完整原稿，并写入封面与正文插图提示词。",
  ],
  [
    "hot_news_review_draft",
    "审校热点原稿",
    "检查结构、事实边界、文风与字数档。",
  ],
  [
    "hot_news_generate_wechat",
    "生成公众号文章",
    "仅基于已审校原稿生成公众号成品。",
  ],
  [
    "hot_news_review_wechat",
    "审校公众号文章",
    "检查保真、标题导语、移动端表达与内部术语清理。",
  ],
  [
    "hot_news_plan_images",
    "确认配套图片计划",
    "按骨架封面/风格与插图档确认配图计划。",
  ],
  ["hot_news_generate_images", "生成配套图片", "生成封面与指定数量正文插图。"],
  [
    "hot_news_validate_images",
    "检查图片数量与引用",
    "检查数量、占位替换与正文引用一致性。",
  ],
  [
    "hot_news_final_validation",
    "最终完整性检查",
    "检查审校门禁、字数档、配图数量与产物完整。",
  ],
  [
    "hot_news_commit_version",
    "保存文章版本",
    "通过终检后提交原稿、公众号文章与产物版本。",
  ],
] as const;

export const labels: Record<string, string> = Object.fromEntries(
  stages.map(([key, title]) => [key, title]),
);

export const runningNotes: Record<string, string> = {
  hot_news_load_context: "正在读取文章骨架、资料线索与生成配置...",
  hot_news_load_skills: "正在加载并固化热点文章 Skill...",
  hot_news_prepare_facts: "正在整理资料线索为事实包...",
  hot_news_generate_draft: "正在按骨架生成原稿与插图提示词...",
  hot_news_review_draft: "正在审校原稿结构、事实、字数与插图提示词...",
  hot_news_generate_wechat: "正在生成公众号成品文章...",
  hot_news_review_wechat: "正在审校公众号成品保真与表达...",
  hot_news_plan_images: "正在按原稿插图提示词确认配图计划...",
  hot_news_generate_images: "正在按原稿插图提示词生成配套图片...",
  hot_news_validate_images: "正在检查配图数量与正文引用...",
  hot_news_final_validation: "正在做提交前字数/配图/审校终检...",
  hot_news_commit_version: "正在保存文章、图片和版本记录...",
};

export const statusText = (s: string) =>
  s === "succeeded"
    ? "已完成"
    : s === "failed"
      ? "执行失败"
      : s === "running"
        ? "执行中"
        : s === "cancelled"
          ? "已取消"
          : "等待执行";

export const jobDisplayStatus = (job: Pick<JobData, "status" | "currentStep">) =>
  job.status === "pending" &&
  (job.currentStep === "retry_queued" || job.currentStep === "recovered")
    ? "running"
    : job.status;

export const time = formatExecutionNodeTime;

export const duration = formatElapsedDurationLabel;

export const text = (v: unknown, fallback: string) => {
  if (!v) return fallback;
  if (typeof v === "string")
    return v.length > 150 ? `${v.slice(0, 150)}...` : v;
  if (Array.isArray(v)) return v.slice(0, 4).map(String).join("；") || fallback;
  if (typeof v === "object")
    return (
      Object.entries(v as Record<string, unknown>)
        .slice(0, 4)
        .map(
          ([k, x]) =>
            `${k}：${Array.isArray(x) ? `${x.length} 项` : String(x)}`,
        )
        .join("；") || fallback
    );
  return String(v);
};

export const readableOutput = (value: unknown, fallback: string) => {
  if (!value || typeof value !== "object" || Array.isArray(value))
    return fallback;
  const data = value as Record<string, unknown>;
  const parts: string[] = [];
  if (typeof data.status === "string")
    parts.push(
      data.status === "passed"
        ? "检查通过"
        : data.status === "planned"
          ? "已完成规划"
          : data.status,
    );
  if (typeof data.skillCount === "number")
    parts.push(`已加载 ${data.skillCount} 项写作能力`);
  if (typeof data.confirmedFactCount === "number")
    parts.push(`已确认 ${data.confirmedFactCount} 条核心事实`);
  if (typeof data.sourceCount === "number")
    parts.push(`已关联 ${data.sourceCount} 个来源`);
  if (typeof data.sectionCount === "number")
    parts.push(`已规划 ${data.sectionCount} 个内容段落`);
  if (typeof data.chars === "number")
    parts.push(`内容长度 ${data.chars} 个字符`);
  if (typeof data.sectionKey === "string") {
    const sectionNames: Record<string, string> = {
      opening: "开篇导语",
      event: "事件说明",
      impact: "影响分析",
      limits: "边界与限制",
      judgement: "作者判断",
      conclusion: "结论",
      references: "来源整理",
    };
    parts.push(`正在处理：${sectionNames[data.sectionKey] || "文章段落"}`);
  }
  if (typeof data.count === "number") parts.push(`共 ${data.count} 项`);
  if (typeof data.versionNo === "number")
    parts.push(`已保存为第 ${data.versionNo} 个版本`);
  return parts.join("；") || fallback;
};

export const parent = (key: string) => {
  if (labels[key]) return key;
  if (
    key.startsWith("hot_news_generate_section_") ||
    key.startsWith("hot_news_validate_section_")
  )
    return "hot_news_generate_draft";
  if (key.includes("section") || key.includes("draft"))
    return key.includes("validate")
      ? "hot_news_review_draft"
      : "hot_news_generate_draft";
  if (key.includes("wechat"))
    return key.includes("validate") || key.includes("review")
      ? "hot_news_review_wechat"
      : "hot_news_generate_wechat";
  if (key.includes("image"))
    return key.includes("validate")
      ? "hot_news_validate_images"
      : key.includes("plan")
        ? "hot_news_plan_images"
        : "hot_news_generate_images";
  if (key.includes("fact") || key.includes("reference"))
    return "hot_news_prepare_facts";
  // 已废弃的定位/大纲节点归并到「按骨架生成」
  if (key.includes("positioning") || key.includes("plan_draft") || key.includes("outline"))
    return "hot_news_generate_draft";
  if (key.includes("save") || key.includes("commit"))
    return "hot_news_commit_version";
  if (key.includes("final") || key.includes("article"))
    return "hot_news_final_validation";
  return null;
};

export type StageDisplayStatus = "pending" | "running" | "succeeded" | "failed";

export function stepClassName(displayStatus: string) {
  if (displayStatus === "succeeded") return "complete";
  if (displayStatus === "failed") return "failed";
  if (displayStatus === "running") return "running";
  return "pending";
}

export function normalizeHotNewsNodeStatus(node: Pick<HotNewsWorkflowNode, "status" | "finishedAt">): string {
  return node.status === "running" && node.finishedAt ? "succeeded" : node.status;
}

/** 与课程执行轨迹一致：失败重试 / 重新排队 / 租约恢复切开批次。 */
export const isHotNewsAttemptBoundary = sharedIsHotNewsAttemptBoundary;

export function isHotNewsExecutionFailureEvent(eventType: string | undefined) {
  return eventType === "error" || eventType === "failed" || eventType === "cancelled";
}

/**
 * 将同一次 Job 的事件/节点切成用户可切换的执行批次（对齐课程模式）。
 */
export function buildHotNewsExecutionAttemptBatches(
  events: JobEventData[],
  nodes: HotNewsWorkflowNode[],
): ExecutionAttemptBatch<HotNewsWorkflowNode>[] {
  return buildSharedExecutionAttemptBatches(
    events,
    nodes,
    sharedIsHotNewsAttemptBoundary,
  );
}

export function getHotNewsStageDisplayStatus({
  persistedState,
  jobStatus,
  stageIndex,
  currentStageIndex,
  isCurrentStage,
}: {
  persistedState: string;
  jobStatus?: string;
  stageIndex: number;
  currentStageIndex: number;
  isCurrentStage: boolean;
}): StageDisplayStatus {
  // 与课程执行轨迹一致：任务已成功则全部阶段视为完成（节点明细缺失时也要体现进度）
  if (jobStatus === "succeeded") {
    return persistedState === "failed" ? "failed" : "succeeded";
  }
  const activeRun = jobStatus === "running" || jobStatus === "failed";
  if (activeRun && currentStageIndex >= 0 && stageIndex > currentStageIndex) {
    return "pending";
  }
  // 当前步骤已前进时，前面阶段即使节点明细仍标 running 也视为已完成
  if (activeRun && currentStageIndex >= 0 && stageIndex < currentStageIndex) {
    return persistedState === "failed" ? "failed" : "succeeded";
  }
  if (persistedState === "succeeded" || persistedState === "failed") {
    return persistedState;
  }
  if (jobStatus === "running" && isCurrentStage) return "running";
  return persistedState === "running" ? "running" : "pending";
}

/** 从 Job 视图或 output 载荷解析工作流运行 ID。 */

export function resolveHotNewsWorkflowRunId(
  job: Pick<JobData, "workflowRunId" | "output"> | null | undefined,
): string | null {
  if (!job) return null;
  if (typeof job.workflowRunId === "string" && job.workflowRunId.trim()) {
    return job.workflowRunId.trim();
  }
  const output = job.output;
  if (!output || typeof output !== "object") return null;
  const runId = output.runId ?? output.workflowRunId;
  return typeof runId === "string" && runId.trim() ? runId.trim() : null;
}

/**
 * 配图进度文案：按槽位标识取「当前正在生成的第几张」。
 * 优先最大序号（重试跳过已成功槽位后不会被更早的「第 1 张」盖住）。
 */

export function resolveHotNewsImageProgressMessage(
  events: Pick<JobEventData, "id" | "step" | "message">[],
): string | undefined {
  const parsed = events
    .filter(
      (event) =>
        parent(event.step) === "hot_news_generate_images" &&
        typeof event.message === "string" &&
        event.message.trim(),
    )
    .map((event) => {
      const match = event.message.match(/第\s*(\d+)\s*\/\s*(\d+)\s*张/);
      if (!match) {
        return {
          id: event.id,
          slotIndex: 0,
          message: event.message,
        };
      }
      return {
        id: event.id,
        slotIndex: Number(match[1]),
        message: event.message,
      };
    });
  if (!parsed.length) return undefined;
  parsed.sort(
    (left, right) =>
      right.slotIndex - left.slotIndex || right.id - left.id,
  );
  return parsed[0]?.message;
}

export {
  wordCountLabel,
  imageCountLabel,
} from "../../../shared/format/wordImageLabels";
