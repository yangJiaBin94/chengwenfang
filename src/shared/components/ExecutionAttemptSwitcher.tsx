import type { JobEventData } from "../api/jobs";

/** 批次列表项：至少含序号、开始时间与事件（用于失败态）。 */
export type AttemptSwitcherBatch = {
  number: number;
  startedAt: string | null;
  events: Array<Pick<JobEventData, "eventType">>;
};

/**
 * 执行页「执行批次」切换条：课程轨迹与热点执行页共用。
 * 时间格式与失败判定由调用方注入，保证两处文案与时区一致。
 */
export function ExecutionAttemptSwitcher({
  batches,
  selectedNumber,
  onSelect,
  formatTime,
  isFailureEvent,
  buttonType,
}: {
  batches: AttemptSwitcherBatch[];
  selectedNumber: number | undefined;
  onSelect: (batchNumber: number) => void;
  formatTime: (value: string | null) => string;
  isFailureEvent: (eventType: string | undefined) => boolean;
  /** 热点执行页按钮带 type="button"；课程轨迹不写。 */
  buttonType?: "button";
}) {
  return (
    <nav className="execution-attempt-switcher" aria-label="执行批次">
      <div>
        <strong>执行批次</strong>
        <span>每次失败步骤重试独立记录</span>
      </div>
      <div className="execution-attempt-options">
        {batches.map((batch, index) => {
          const latest = index === batches.length - 1;
          const active = batch.number === selectedNumber;
          const batchFailed = batch.events.some((event) =>
            isFailureEvent(event.eventType),
          );
          return (
            <button
              className={active ? "active" : ""}
              key={batch.number}
              onClick={() => onSelect(batch.number)}
              type={buttonType}
            >
              <span>
                {batch.number === 1
                  ? "首次执行"
                  : `第 ${batch.number} 次执行`}
              </span>
              <small>
                {formatTime(batch.startedAt)}
                {latest ? " · 最新" : batchFailed ? " · 失败" : ""}
              </small>
            </button>
          );
        })}
      </div>
    </nav>
  );
}
