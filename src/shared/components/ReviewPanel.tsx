import {
  AlertTriangle,
  CheckCircle2,
  Circle,
  Undo2,
} from "lucide-react";
import type { ReactNode } from "react";

/** 审核列表单项的展示形态（课程 / 热点状态字不同，归一后再传入）。 */
export type ReviewAuditItemView = {
  key: string;
  status: "pass" | "warn" | "fail" | "pending";
  title: ReactNode;
  body: ReactNode;
};

/**
 * 课程与热点共用的审核工作区外壳：自动审核结果区 + 人工审核决定区。
 * 差异（分数算法、头部按钮、阻断文案、撤销按钮等）全部用参数表达。
 */
export function ReviewPanel({
  workspaceClassName = "review-workspace",
  score,
  auditHeadingActions,
  statusBadge,
  statusInHumanHeading = false,
  auditSummary,
  auditAlerts,
  items,
  humanReviewedAt,
  comment,
  onComment,
  commentPlaceholder,
  approvalBlockReason,
  canApprove,
  approveTitle,
  onReject,
  onApprove,
  onReset,
  showActionIcons = false,
}: {
  workspaceClassName?: string;
  score: ReactNode;
  auditHeadingActions?: ReactNode;
  statusBadge?: ReactNode;
  /** true：状态徽标放在人工审核标题行（热点）；false：放在 AI 预审标题行（课程）。 */
  statusInHumanHeading?: boolean;
  auditSummary: ReactNode;
  auditAlerts?: ReactNode;
  items: ReviewAuditItemView[];
  humanReviewedAt?: string | null;
  comment: string;
  onComment: (value: string) => void;
  commentPlaceholder?: string;
  approvalBlockReason?: string;
  canApprove: boolean;
  approveTitle?: string;
  onReject: () => void;
  onApprove: () => void;
  onReset?: () => void;
  showActionIcons?: boolean;
}) {
  return (
    <div className={workspaceClassName}>
      <section className="review-result">
        <div className="score-ring">{score}</div>
        <div>
          <div className="review-section-heading">
            <div>
              <span className="section-kicker">AI 预审</span>
              <h3>自动审核结果</h3>
            </div>
            <div className="review-heading-actions">
              {auditHeadingActions}
              {!statusInHumanHeading ? statusBadge : null}
            </div>
          </div>
          {typeof auditSummary === "string" || typeof auditSummary === "number" ? (
            <p>{auditSummary}</p>
          ) : (
            auditSummary
          )}
          {auditAlerts}
          <div className="issue-list">
            {items.map((item) => (
              <div className={`audit-item ${item.status}`} key={item.key}>
                {item.status === "pass" ? (
                  <CheckCircle2 size={16} />
                ) : item.status === "pending" ? (
                  <Circle size={16} />
                ) : (
                  <AlertTriangle size={16} />
                )}
                <div>
                  <strong>{item.title}</strong>
                  {item.body}
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="human-review-panel">
        <div className="review-section-heading">
          <div>
            <span className="section-kicker">Human in the loop</span>
            <h3>人工审核决定</h3>
          </div>
          {statusInHumanHeading ? statusBadge : null}
          {!statusInHumanHeading && humanReviewedAt ? (
            <small>最近提交：{humanReviewedAt}</small>
          ) : null}
        </div>

        <label className="review-comment-field">
          <span>审核意见</span>
          <textarea
            value={comment}
            onChange={(event) => onComment(event.target.value)}
            placeholder={commentPlaceholder}
          />
          <small>{comment.trim().length} 字 · 退回时必须填写具体意见</small>
        </label>

        {approvalBlockReason ? (
          <div className="review-blocker">
            <AlertTriangle size={17} />
            <span>{approvalBlockReason}</span>
          </div>
        ) : null}

        <div className="review-actions">
          <button
            className="review-button reject"
            disabled={!comment.trim()}
            onClick={onReject}
          >
            {showActionIcons ? <Undo2 size={17} /> : null}
            退回修改
          </button>
          <button
            className="review-button approve"
            disabled={!canApprove}
            title={approveTitle}
            onClick={onApprove}
          >
            {showActionIcons ? <CheckCircle2 size={17} /> : null}
            审核通过
          </button>
          {onReset ? (
            <button className="review-button reset" onClick={onReset}>
              撤销决定
            </button>
          ) : null}
        </div>
      </section>
    </div>
  );
}
