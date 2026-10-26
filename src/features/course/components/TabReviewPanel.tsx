// @ts-nocheck
import {
  AlertTriangle,
  CheckCircle2,
  RefreshCcw,
  Wand2,
} from "lucide-react";
import {
  ReviewPanel,
  type ReviewAuditItemView,
} from "../../../shared/components/ReviewPanel";
import type { AuditItem, AuditReport, HumanReview } from "../model";
import { localizedAuditItem } from "../model";

/**
 * 课程详情「审核」标签：状态由 TabContent 持有，本组件只负责渲染。
 * UI 外壳与热点共用 shared ReviewPanel，课程差异（自动修复、撤销）用参数表达。
 */
export function TabReviewPanel(props: {
  course: { articleExcerpt?: string | null };
  content: {
    articleArticleId?: string | null;
    article?: string | null;
  };
  humanReview: HumanReview | null | undefined;
  onSubmitHumanReview: (status: "approved" | "rejected", comment: string) => void;
  onResetHumanReview: () => void;
  onConfirm: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  reviewComment: string;
  setReviewComment: (value: string) => void;
  auditReport: AuditReport;
  auditLoading: boolean;
  aiFixing: boolean;
  aiFixProgress: string;
  aiFixError: string;
  setAiFixError: (value: string) => void;
  rechecking: boolean;
  fixApplied: boolean;
  fixAuditSummary: { before: AuditReport; after: AuditReport } | null;
  runAiFix: () => void;
  undoAutoFix: () => void;
  isImageAssetRepairItem: (item: AuditItem) => boolean;
}) {
  const {
    course,
    content,
    humanReview,
    onSubmitHumanReview,
    onResetHumanReview,
    onConfirm,
    reviewComment,
    setReviewComment,
    auditReport,
    auditLoading,
    aiFixing,
    aiFixProgress,
    aiFixError,
    setAiFixError,
    rechecking,
    fixApplied,
    fixAuditSummary,
    runAiFix,
    undoAutoFix,
    isImageAssetRepairItem,
  } = props;

  const reviewStatus = humanReview?.status ?? "pending";
  const articleAvailable = Boolean(
    content.articleArticleId && (content.article || course.articleExcerpt),
  );
  const approvalBlockReason = auditLoading
    ? "自动审核仍在执行，请等待完成。"
    : !articleAvailable
      ? "当前课程缺少公众号文章，暂不能通过。"
      : !auditReport.id
        ? "当前文章版本尚未完成自动审核，暂不能通过。"
        : auditReport.failed > 0
          ? `自动审核仍有 ${auditReport.failed} 个失败项，暂不能通过。`
          : "";
  const canApprove = approvalBlockReason === "";

  const items: ReviewAuditItemView[] = auditReport.items.map((item) => {
    const copy = localizedAuditItem(item);
    return {
      key: item.code,
      status: item.status,
      title: copy.title,
      body: (
        <>
          <small>{copy.detail}</small>
          {item.status !== "pass" && (
            <em>
              {item.fixableByTextAi
                ? "文本 AI 可修复"
                : isImageAssetRepairItem(item)
                  ? "系统可自动修复"
                  : "需要流程或资产处理"}
            </em>
          )}
        </>
      ),
    };
  });

  return (
    <ReviewPanel
      score={auditReport.score}
      auditHeadingActions={
        <button
          className="auto-fix-trigger"
          disabled={
            auditLoading ||
            aiFixing ||
            !auditReport.items.some(
              (item) =>
                item.status !== "pass" &&
                (item.fixableByTextAi || isImageAssetRepairItem(item)),
            )
          }
          onClick={() => void runAiFix()}
        >
          <Wand2 size={16} />
          {aiFixing ? "自动修复中" : "自动修复审核问题"}
        </button>
      }
      statusBadge={
        <span className={`review-status review-status-${reviewStatus}`}>
          {reviewStatus === "approved"
            ? "人工已通过"
            : reviewStatus === "rejected"
              ? "人工已退回"
              : "待人工审核"}
        </span>
      }
      auditSummary={
        <p>
          已完成 {auditReport.items.length} 项检查：
          {auditReport.passed} 通过，{auditReport.warnings} 警告，
          {auditReport.failed} 失败。
        </p>
      }
      auditAlerts={
        <>
          {(aiFixing || aiFixProgress) && (
            <div className="auto-fix-rechecking">
              <RefreshCcw size={17} />
              <span>{aiFixProgress || "自动修复进行中..."}</span>
            </div>
          )}
          {aiFixError && (
            <div className="auto-fix-error">
              <AlertTriangle size={17} />
              <span>{aiFixError}</span>
              <button onClick={() => setAiFixError("")}>关闭</button>
            </div>
          )}
          {rechecking && (
            <div className="auto-fix-rechecking">
              <RefreshCcw size={17} />
              <span>修复稿已写入，正在自动重新检查全部审核项...</span>
            </div>
          )}
          {fixApplied && fixAuditSummary && (
            <div className="auto-fix-success">
              <CheckCircle2 size={17} />
              <span>
                自动复检完成：分数 {fixAuditSummary.before.score} →{" "}
                {fixAuditSummary.after.score}， 失败{" "}
                {fixAuditSummary.before.failed} →{" "}
                {fixAuditSummary.after.failed}， 警告{" "}
                {fixAuditSummary.before.warnings} →{" "}
                {fixAuditSummary.after.warnings}。
              </span>
              <button onClick={undoAutoFix}>撤销本次修复</button>
            </div>
          )}
        </>
      }
      items={items}
      humanReviewedAt={humanReview?.reviewedAt}
      comment={reviewComment}
      onComment={setReviewComment}
      commentPlaceholder="记录需要修改的事实、结构、表达或排版问题。通过时也可以留下发布备注。"
      approvalBlockReason={canApprove ? undefined : approvalBlockReason}
      canApprove={canApprove}
      showActionIcons
      onReject={() => onSubmitHumanReview("rejected", reviewComment)}
      onApprove={() => onSubmitHumanReview("approved", reviewComment)}
      onReset={
        humanReview
          ? async () => {
              if (await onConfirm("撤销审核", "确定撤销当前审核决定吗？"))
                onResetHumanReview();
            }
          : undefined
      }
    />
  );
}
