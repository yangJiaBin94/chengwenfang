import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  BookOpen,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ClipboardCheck,
  History,
  RefreshCcw,
} from "lucide-react";
import type {
  ArticleVersionData,
  ContentThemeData,
} from "../../course/api";
import type {
  JobData,
  JobEventData,
  WorkflowNodeData,
} from "../../../shared/api/jobs";
import { formatExecutionTime } from "../../../shared/format/time";
import {
  isExecutionFailureEvent,
  generationStepIndex,
  completedExecutionMessage,
  formatGenerationSnapshot,
  workflowNodeTitle,
  eventStepTitle,
  technicalEventMessage,
  workflowNodeParentKey,
  buildExecutionAttemptBatches,
  WorkflowNodeRow,
  displayJobStatus,
  jobStatusLabel,
  uniqueGenerationPipeline,
  Course,
} from "../components/executionHelpers";

export { jobStatusLabel } from "../components/executionHelpers";
import { ExecutionTrajectoryView } from "../components/ExecutionTrajectoryView";

export { formatExecutionTime };

export function ExecutionTrajectoryPage({
  jobs,
  events,
  nodes,
  versions,
  courses,
  themes,
  selectedThemeId,
  themePickerOpen,
  onThemePickerOpenChange,
  onThemeChange,
  selectedJobId,
  onSelect,
  onRetry,
  onBackToCourse,
}: {
  jobs: JobData[];
  events: JobEventData[];
  nodes: WorkflowNodeData[];
  versions: ArticleVersionData[];
  courses: Course[];
  themes: ContentThemeData[];
  selectedThemeId: string;
  themePickerOpen: boolean;
  onThemePickerOpenChange: (open: boolean) => void;
  onThemeChange: (themeId: string) => void;
  selectedJobId: string | null;
  onSelect: (id: string) => void;
  onRetry: (jobId: string) => void;
  onBackToCourse: (courseId: string) => void;
}) {
  const [showSkills, setShowSkills] = useState(true);
  const [showNodeDetails, setShowNodeDetails] = useState(false);
  const [showEvents, setShowEvents] = useState(false);
  const [selectedAttemptNumber, setSelectedAttemptNumber] = useState<
    number | null
  >(null);
  const [historyPage, setHistoryPage] = useState(1);
  const HISTORY_PAGE_SIZE = 10;
  const selectedTheme =
    themes.find((theme) => theme.id === selectedThemeId) || themes[0] || null;
  const courseIds = useMemo(
    () => new Set(courses.map((course) => course.id)),
    [courses],
  );
  // 仅展示当前主题下课程的生成任务，避免跨主题裸 id 标题混在一起
  const visibleJobs = useMemo(
    () => jobs.filter((job) => courseIds.has(job.entityId)),
    [jobs, courseIds],
  );
  // 禁止回落到全局 jobs[0]：优先当前主题列表，深链 job 仍可从全量 jobs 取详情
  const selectedRaw = selectedJobId
    ? jobs.find((job) => job.id === selectedJobId) ||
      visibleJobs.find((job) => job.id === selectedJobId) ||
      null
    : visibleJobs[0] || null;
  const selected = selectedRaw
    ? { ...selectedRaw, status: displayJobStatus(selectedRaw, events) }
    : selectedRaw;
  const course = courses.find((item) => item.id === selected?.entityId);
  const historyPageCount = Math.max(
    1,
    Math.ceil(visibleJobs.length / HISTORY_PAGE_SIZE),
  );
  const safeHistoryPage = Math.min(historyPage, historyPageCount);
  const pagedJobs = visibleJobs.slice(
    (safeHistoryPage - 1) * HISTORY_PAGE_SIZE,
    safeHistoryPage * HISTORY_PAGE_SIZE,
  );
  useEffect(() => {
    setHistoryPage(1);
  }, [selectedThemeId]);
  useEffect(() => {
    if (!selectedJobId) return;
    const index = visibleJobs.findIndex((job) => job.id === selectedJobId);
    if (index < 0) return;
    setHistoryPage(Math.floor(index / HISTORY_PAGE_SIZE) + 1);
  }, [selectedJobId, visibleJobs]);
  // 无选中或选中已失效时，落到当前主题第一条；深链跨主题时先等父级切主题
  useEffect(() => {
    if (courses.length === 0) return;
    if (selectedJobId) {
      if (visibleJobs.some((job) => job.id === selectedJobId)) return;
      if (jobs.some((job) => job.id === selectedJobId)) return;
    }
    if (visibleJobs[0]) {
      onSelect(visibleJobs[0].id);
    }
  }, [courses.length, visibleJobs, selectedJobId, jobs, onSelect]);
  const attemptBatches = useMemo(
    () => buildExecutionAttemptBatches(events, nodes),
    [events, nodes],
  );
  const selectedAttempt =
    attemptBatches.find((batch) => batch.number === selectedAttemptNumber) ||
    attemptBatches[attemptBatches.length - 1];
  const attemptEvents = selectedAttempt?.events || [];
  const attemptNodes = selectedAttempt?.nodes || [];
  useEffect(() => setSelectedAttemptNumber(null), [selectedJobId]);
  const mappedEvents = useMemo(() => {
    let inheritedStep = 0;
    return attemptEvents.map((event) => {
      const mappedStep = generationStepIndex(event.step, event.message);
      if (mappedStep >= 0) inheritedStep = mappedStep;
      return { event, stepIndex: inheritedStep };
    });
  }, [attemptEvents]);
  const latestMappedEvent = mappedEvents[mappedEvents.length - 1];
  const latestEvent = latestMappedEvent?.event;
  const activeIndex =
    latestMappedEvent?.stepIndex ??
    (selected?.currentStep
      ? Math.max(0, generationStepIndex(selected.currentStep))
      : -1);
  const isExecuting = selected?.status === "running";
  const selectedStatusText =
    selected?.status === "succeeded"
      ? "生成完成"
      : selected?.status === "failed"
        ? "执行失败"
        : selected?.status === "cancelled"
          ? "已取消"
          : selected?.status === "pending"
            ? selected.currentStep === "recovered"
              ? "中断后已恢复排队"
              : "等待执行"
            : "执行中";
  const isRetrying = Boolean(
    latestEvent &&
    ((latestEvent.step ?? "").includes("retry") ||
      (latestEvent.message ?? "").includes("重试") ||
      (latestEvent.message ?? "").includes("修正")),
  );
  const failedStepTitle =
    uniqueGenerationPipeline.find((_, index) => index === activeIndex)?.[1] ??
    "未知步骤";
  const queuedSkills = attemptEvents.find((event) =>
    Array.isArray(event.payload?.skills),
  )?.payload?.skills;
  const outputSkills = selected?.output?.skills;
  const generationReport = selected?.output?.generationReport as {
    steps?: string[];
    files?: Record<
      string,
      {
        path?: string | null;
        exists?: boolean;
        sha256?: string | null;
        purpose?: string;
      }
    >;
    images?: {
      generated?: number;
      failed?: number;
      plan?: Array<{
        sequenceNo?: number;
        kind?: string;
        size?: string;
        publicUrl?: string;
        validated?: boolean;
      }>;
      failures?: Array<{
        code?: string;
        message?: string;
        kind?: string;
        size?: string;
      }>;
    };
    draft?: { generationPlan?: string; contentManifest?: string | null };
    wechat?: { generationPlan?: string; contentManifest?: string | null };
  } | null;
  const skills = (
    Array.isArray(queuedSkills)
      ? queuedSkills
      : Array.isArray(outputSkills)
        ? outputSkills
        : []
  ) as Array<{
    id?: string;
    name?: string;
    scope?: string;
    priority?: number;
  }>;
  const groupedNodes = useMemo(
    () =>
      attemptNodes
        .filter((node) => workflowNodeParentKey(node.nodeKey) === null)
        .map((parent) => ({
          parent,
          children: attemptNodes.filter(
            (node) => workflowNodeParentKey(node.nodeKey) === parent.nodeKey,
          ),
        })),
    [attemptNodes],
  );

  return (
    <ExecutionTrajectoryView
      HISTORY_PAGE_SIZE={HISTORY_PAGE_SIZE}
      activeIndex={activeIndex}
      attemptBatches={attemptBatches}
      attemptEvents={attemptEvents}
      attemptNodes={attemptNodes}
      course={course}
      courses={courses}
      events={events}
      failedStepTitle={failedStepTitle}
      generationReport={generationReport}
      groupedNodes={groupedNodes}
      historyPageCount={historyPageCount}
      isExecuting={isExecuting}
      isRetrying={isRetrying}
      latestEvent={latestEvent}
      mappedEvents={mappedEvents}
      onBackToCourse={onBackToCourse}
      onRetry={onRetry}
      onSelect={onSelect}
      onThemeChange={onThemeChange}
      onThemePickerOpenChange={onThemePickerOpenChange}
      pagedJobs={pagedJobs}
      safeHistoryPage={safeHistoryPage}
      selected={selected}
      selectedAttempt={selectedAttempt}
      selectedJobId={selectedJobId}
      selectedStatusText={selectedStatusText}
      selectedTheme={selectedTheme}
      selectedThemeId={selectedThemeId}
      setHistoryPage={setHistoryPage}
      setSelectedAttemptNumber={setSelectedAttemptNumber}
      setShowEvents={setShowEvents}
      setShowNodeDetails={setShowNodeDetails}
      setShowSkills={setShowSkills}
      showEvents={showEvents}
      showNodeDetails={showNodeDetails}
      showSkills={showSkills}
      skills={skills}
      themePickerOpen={themePickerOpen}
      themes={themes}
      versions={versions}
      visibleJobs={visibleJobs}
    />
  );
}
