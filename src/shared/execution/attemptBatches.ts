import type { JobEventData, WorkflowNodeData } from "../api/jobs";

/** 一次用户可切换的执行批次（课程轨迹与热点执行页共用）。 */
export type ExecutionAttemptBatch<TNode extends WorkflowNodeData = WorkflowNodeData> = {
  number: number;
  events: JobEventData[];
  nodes: TNode[];
  startedAt: string | null;
  finishedAt: string | null;
};

/** 课程默认批次边界：失败步骤重试 / 重新排队。 */
export function isCourseAttemptBoundary(
  event: Pick<JobEventData, "step" | "eventType">,
) {
  return (
    event.step === "retry_queued" ||
    (event.eventType === "queued" && event.step === "queued")
  );
}

/** 热点批次边界：在课程基础上再切开租约恢复。 */
export function isHotNewsAttemptBoundary(
  event: Pick<JobEventData, "step" | "eventType">,
) {
  return (
    event.step === "retry_queued" ||
    event.step === "recovered" ||
    (event.eventType === "queued" && event.step === "queued")
  );
}

/**
 * 将同一次 Job 的事件/节点切成用户可切换的执行批次。
 * @param isBoundary 批次切开谓词；默认与课程轨迹一致
 */
export function buildExecutionAttemptBatches<
  TNode extends WorkflowNodeData = WorkflowNodeData,
>(
  events: JobEventData[],
  nodes: TNode[],
  isBoundary: (
    event: Pick<JobEventData, "step" | "eventType">,
  ) => boolean = isCourseAttemptBoundary,
): ExecutionAttemptBatch<TNode>[] {
  const orderedEvents = [...events].sort((left, right) => left.id - right.id);
  const eventGroups: JobEventData[][] = [[]];
  orderedEvents.forEach((event) => {
    if (isBoundary(event) && eventGroups[eventGroups.length - 1].length) {
      eventGroups.push([]);
    }
    eventGroups[eventGroups.length - 1].push(event);
  });

  const groups = eventGroups.filter((group) => group.length > 0);
  if (!groups.length) groups.push([]);
  const starts = groups.map((group, index) => {
    const eventTime = group[0]?.createdAt;
    if (eventTime) return new Date(eventTime).getTime();
    const firstNode = [...nodes]
      .filter((node) => node.startedAt)
      .sort(
        (left, right) =>
          new Date(left.startedAt!).getTime() -
          new Date(right.startedAt!).getTime(),
      )[0];
    return firstNode?.startedAt
      ? new Date(firstNode.startedAt).getTime()
      : index;
  });

  return groups.map((group, index): ExecutionAttemptBatch<TNode> => {
    const nextStart = starts[index + 1] ?? Number.POSITIVE_INFINITY;
    const batchNodes = nodes.filter((node) => {
      if (!node.startedAt) return index === groups.length - 1;
      const startedAt = new Date(node.startedAt).getTime();
      return startedAt >= starts[index] && startedAt < nextStart;
    });
    const finishedTimes = [
      ...group.map((event) => event.createdAt),
      ...batchNodes.flatMap((node) =>
        node.finishedAt ? [node.finishedAt] : [],
      ),
    ];
    return {
      number: index + 1,
      events: group,
      nodes: batchNodes,
      startedAt: group[0]?.createdAt || batchNodes[0]?.startedAt || null,
      finishedAt: finishedTimes.at(-1) || null,
    };
  });
}
