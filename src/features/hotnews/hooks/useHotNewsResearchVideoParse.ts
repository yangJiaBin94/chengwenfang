// @ts-nocheck
import { useEffect, useState } from "react";
import { loadJob } from "../../../shared/api/jobs";
import {
  startHotNewsVideoParse,
  startHotNewsVideoParseItem,
} from "../api";
import {
  VIDEO_STEP_LABELS,
  parseVideoParseProgressMessage,
  asProgressTitles,
} from "../components/researchPanels";

/** 热点调研页：视频解析任务状态与启动逻辑。 */
export function useHotNewsResearchVideoParse({
  topicId,
  pendingVideoCount,
  items,
  setItems,
  refresh,
  showNotice,
}: {
  topicId: string;
  pendingVideoCount: number;
  items: Array<Record<string, unknown>>;
  setItems: React.Dispatch<React.SetStateAction<Array<Record<string, unknown>>>>;
  refresh: () => Promise<unknown>;
  showNotice: (title: string, message: string) => void;
}) {
  const [videoParseJobId, setVideoParseJobId] = useState<string | null>(null);
  const [videoParseProgress, setVideoParseProgress] = useState(0);
  const [videoParseMessage, setVideoParseMessage] = useState("");
  const [videoParseStep, setVideoParseStep] = useState("");
  const [videoParseBusy, setVideoParseBusy] = useState(false);
  /** batch=顶部总进度；item=进度挂在单条卡片上 */
  const [videoParseScope, setVideoParseScope] = useState<"batch" | "item">("batch");
  const [videoParseItemId, setVideoParseItemId] = useState<string | null>(null);
  const [videoParseMeta, setVideoParseMeta] = useState<{
    total?: number;
    completed?: number;
    running?: number;
    titles?: string[];
    parallel?: boolean;
  }>({});

  // 轮询视频解析 Job；单条模式顺带刷新条目状态
  useEffect(() => {
    if (!videoParseJobId || !videoParseBusy) return;
    let cancelled = false;
    async function pollParse() {
      try {
        const job = await loadJob(videoParseJobId!);
        if (cancelled) return;
        setVideoParseStep(String(job.currentStep || ""));
        const meta = (job.progressMeta || {}) as Record<string, unknown>;
        const fromMessage = parseVideoParseProgressMessage(
          String(job.progressMessage || ""),
        );
        const total =
          Number(meta.total || 0) ||
          fromMessage.total ||
          undefined;
        const completedRaw =
          meta.completed != null ? meta.completed : fromMessage.completed;
        const completed =
          completedRaw == null || completedRaw === ""
            ? 0
            : Number(completedRaw);
        const runningRaw =
          meta.running != null ? meta.running : fromMessage.running;
        const running =
          runningRaw == null || runningRaw === ""
            ? 0
            : Number(runningRaw);
        const titles = asProgressTitles(meta.titles, fromMessage.titles);
        // 百分比与「已完成 + 进行中」对齐：优先用 meta，其次按完成/并行数推算
        const derivedPercent =
          total && total > 0
            ? Math.min(
                99,
                Math.round(
                  ((completed + Math.min(running, Math.max(0, total - completed)) * 0.4) *
                    100) /
                    total,
                ),
              )
            : Number(job.progress || 0);
        const percent =
          Number(meta.percent || 0) ||
          derivedPercent ||
          Number(job.progress || 0);
        setVideoParseProgress(percent);
        setVideoParseMeta({
          total,
          completed,
          running,
          titles,
          parallel: meta.parallel === true || running > 1,
        });
        setVideoParseMessage(String(job.progressMessage || ""));
        const status = String(job.status || "");
        if (["succeeded", "failed", "cancelled"].includes(status)) {
          // 先清掉 jobId，避免下次点解析时仍轮询到旧的终态任务
          setVideoParseBusy(false);
          setVideoParseJobId(null);
          setVideoParseItemId(null);
          setVideoParseMeta({});
          await refresh();
          if (status === "succeeded") {
            const output = job.output || {};
            showNotice(
              "视频解析结束",
              `成功 ${String(output.parsed ?? "—")}，失败 ${String(output.failed ?? "—")}。请查看各条目状态。`,
            );
          } else {
            showNotice(
              "视频解析未完成",
              String(job.errorMessage || status),
            );
          }
        } else {
          // 批量/单条均刷新条目，便于卡片显示 running
          await refresh();
        }
      } catch {
        /* keep polling */
      }
    }
    void pollParse();
    const timer = window.setInterval(() => void pollParse(), 2_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [videoParseJobId, videoParseBusy, videoParseScope, topicId]);

  async function startBatchVideoParse() {
    setVideoParseScope("batch");
    setVideoParseItemId(null);
    // 必须先清掉上次 jobId，再置 busy，否则会立刻轮询到旧成功态并误弹「结束」
    setVideoParseJobId(null);
    setVideoParseBusy(true);
    setVideoParseProgress(0);
    setVideoParseMessage("排队中…");
    setVideoParseStep("");
    setVideoParseMeta({
      total: pendingVideoCount,
      completed: 0,
      running: 0,
      titles: [],
      parallel: true,
    });
    try {
      const job = await startHotNewsVideoParse(topicId, {});
      setVideoParseJobId(job.id);
    } catch (error) {
      setVideoParseBusy(false);
      setVideoParseJobId(null);
      setVideoParseMeta({});
      showNotice(
        "无法启动视频解析",
        error instanceof Error ? error.message : "请检查本机 Python/ffmpeg/yt-dlp/Whisper",
      );
    }
  }

  async function startItemVideoParse(itemId: string, fromStep?: string) {
    const current = items.find((row) => String(row.id) === itemId);
    const isVisual = String(current?.videoContentKind || "") === "visual";
    // 画面类型重解析从下载视频起，避免再跑字幕/ASR
    const resolvedStep =
      fromStep ||
      (isVisual ? "download_video" : undefined);
    const optimisticStep = resolvedStep || "fetch_subtitle";
    setVideoParseScope("item");
    setVideoParseItemId(itemId);
    setVideoParseJobId(null);
    setVideoParseBusy(true);
    setVideoParseProgress(0);
    setVideoParseMessage("排队中…");
    setVideoParseStep(optimisticStep);
    // 乐观更新：卡片立刻显示解析中
    setItems((prev) =>
      prev.map((row) =>
        String(row.id) === itemId
          ? {
              ...row,
              videoParseStatus: "running",
              videoParseStep: optimisticStep,
              videoParseError: null,
            }
          : row,
      ),
    );
    try {
      const job = await startHotNewsVideoParseItem(
        topicId,
        itemId,
        resolvedStep ? { fromStep: resolvedStep } : {},
      );
      setVideoParseJobId(job.id);
    } catch (error) {
      setVideoParseBusy(false);
      setVideoParseJobId(null);
      setVideoParseItemId(null);
      await refresh();
      showNotice(
        "无法启动视频解析",
        error instanceof Error ? error.message : "请稍后重试",
      );
    }
  }

  function friendlyParseError(raw: string, stepKey: string): string {
    const text = String(raw || "").trim();
    if (!text) return "解析失败，请稍后重试。";
    const lower = text.toLowerCase();
    const stepLabel = VIDEO_STEP_LABELS[stepKey] || stepKey;
    const withStep = stepLabel ? `（步骤：${stepLabel}）` : "";
    if (
      lower.includes("gbk") ||
      lower.includes("codec") ||
      lower.includes("illegal multibyte") ||
      text.includes("\\u")
    ) {
      return `语音识别结果含特殊字符，写出时编码失败${withStep}。请再点一次「解析」重试。`;
    }
    if (lower.includes("traceback") || lower.includes("exception") || lower.includes("error:")) {
      return `解析失败${withStep}。请重试；若仍失败请检查本机依赖。`;
    }
    // 后端已人话化的短说明直接展示
    return text;
  }

  return {
    videoParseBusy,
    videoParseItemId,
    videoParseMessage,
    videoParseMeta,
    videoParseProgress,
    videoParseScope,
    videoParseStep,
    startBatchVideoParse,
    startItemVideoParse,
    friendlyParseError,
  };
}
