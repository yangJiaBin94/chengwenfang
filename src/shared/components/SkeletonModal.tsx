import { X } from "lucide-react";

/** 骨架弹窗所需字段（热点资料池 / 文章工作区共用）。 */
export type SkeletonModalData = {
  workingTitle?: string | null;
  scope?: string | null;
  coreThesis?: string | null;
  coreEvent?: string | null;
  keyInsights?: unknown;
  sectionPlan?: unknown;
};

function asTextList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item ?? "").trim()).filter(Boolean);
}

/**
 * 文章骨架只读弹窗。
 * 课程模式无此弹窗；热点资料池与文章工作区共用同一渲染。
 */
export function SkeletonModal({
  skeleton,
  onClose,
}: {
  skeleton: SkeletonModalData;
  onClose: () => void;
}) {
  const insights = asTextList(skeleton.keyInsights);
  const sectionPlan = asTextList(skeleton.sectionPlan);
  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section
        className="modal-card hot-research-skeleton-modal"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <div>
            <span className="section-kicker">文章骨架</span>
            <h3>{skeleton.workingTitle || "未命名骨架"}</h3>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="关闭">
            <X size={18} />
          </button>
        </header>
        <div className="hot-research-skeleton">
          {skeleton.scope ? (
            <p>
              <b>范围：</b>
              {skeleton.scope}
            </p>
          ) : null}
          {skeleton.coreThesis ? (
            <p>
              <b>核心论点：</b>
              {skeleton.coreThesis}
            </p>
          ) : null}
          {skeleton.coreEvent ? (
            <p>
              <b>核心事件：</b>
              {skeleton.coreEvent}
            </p>
          ) : null}
          {insights.length > 0 ? (
            <>
              <b>关键洞察</b>
              <ul>
                {insights.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            </>
          ) : null}
          {sectionPlan.length > 0 ? (
            <>
              <b>章节计划</b>
              <ul>
                {sectionPlan.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            </>
          ) : null}
        </div>
        <div className="modal-actions">
          <button className="primary-button" onClick={onClose}>
            关闭
          </button>
        </div>
      </section>
    </div>
  );
}

/** 供资料池等复用的列表规范化（与弹窗内逻辑一致）。 */
export function skeletonAsTextList(value: unknown): string[] {
  return asTextList(value);
}
