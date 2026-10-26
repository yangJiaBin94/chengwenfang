// @ts-nocheck
import { useEffect, useMemo, useRef, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  AlertTriangle,
  BookOpen,
  Bot,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clipboard,
  ClipboardCheck,
  ExternalLink,
  History,
  Image,
  MonitorCheck,
  Palette,
  Play,
  RefreshCcw,
  Search,
  Sparkles,
  Trash2,
  Undo2,
  Wand2,
  WandSparkles,
  X,
} from "lucide-react";
import type {
  ArticleData,
  ContentThemeData,
  CourseGenerationSettingsData,
  ImageCountOption,
  WordCountOption,
} from "../api";
import {
  deleteContentTheme,
  patchContentTheme,
  repairArticle,
  repairArticleMetadata,
  resumeArticleRepair,
  runArticleAudit,
  themePathSlug,
} from "../api";
import { formatExecutionTime } from "../../executions/pages/ExecutionTrajectoryPage";
import { PublishingWorkspace } from "../../publishing/components/PublishingWorkspace";
import {
  type ArticleVersion,
  type AuditItem,
  type AuditReport,
  type Course,
  type CourseContent,
  type HumanReview,
  type HumanReviewStatus,
  type SyncedImage,
  type TabKey,
  type UnifiedContentVersion,
  emptyAuditReport,
  formatCourseStatus,
  imageCountLabels,
  imageNameFromSrc,
  localizedAuditItem,
  nativePublishingEnabled,
  normalizeObsidianEmbeds,
  preferCourseDocumentId,
  prepareMdniceMarkdown,
  resolveImageSrc,
  statusTone,
  tabs,
  wordCountLabels,
} from "../model";
import { EditableMarkdown, MarkdownReader } from "./courseEditorUi";

export function TabVersionsPanel(props: any) {
  const {
    draftArticleVersions, previewVersion, setPreviewVersion,
    previewVersionTab, setPreviewVersionTab, unifiedVersions,
    onRestoreVersion, onViewTrajectory, onConfirm,
  } = props;
  const { formatExecutionTime } = props;
  const pairedDraftVersion = previewVersion
    ? draftArticleVersions.find(
        (version) =>
          previewVersion.workflowRunId &&
          version.workflowRunId === previewVersion.workflowRunId,
      ) ||
      draftArticleVersions.find(
        (version) =>
          new Date(version.createdAt) <= new Date(previewVersion.createdAt),
      )
    : undefined;

  return (
    <>
      <div className="version-list">
        {unifiedVersions.length ? (
          unifiedVersions.map((version) => (
            <div className="version-row" key={version.id}>
              <span>
                <strong>
                  v{version.versionNo} · {version.reason}
                </strong>
                <em className="version-kinds">
                  {version.draftVersion ? "原稿" : ""}
                  {version.draftVersion && version.articleVersion ? " + " : ""}
                  {version.articleVersion ? "公众号" : ""}
                </em>
                <small>
                  {formatExecutionTime(version.createdAt)} · 评分{" "}
                  {version.audit?.score ?? 0} · 失败 {version.audit?.failed ?? 0}{" "}
                  · 警告 {version.audit?.warnings ?? 0}
                </small>
              </span>
              <div className="version-actions">
                <button
                  onClick={() => {
                    setPreviewVersion(version);
                    setPreviewVersionTab("draft");
                  }}
                >
                  查看版本内容
                </button>
                <button
                  disabled={!version.workflowRunId}
                  title={
                    version.workflowRunId
                      ? "查看该版本对应的 AI 生成过程"
                      : "该版本没有关联生成轨迹"
                  }
                  onClick={() =>
                    version.workflowRunId &&
                    onViewTrajectory(version.workflowRunId)
                  }
                >
                  查看生成轨迹
                </button>
                <button
                  onClick={async () => {
                    if (
                      await onConfirm(
                        "恢复历史版本",
                        `确定恢复版本 ${version.versionNo} 吗？当前内容将保存为新版本。`,
                      )
                    )
                      version.articleVersion &&
                        onRestoreVersion(version.articleVersion.id);
                  }}
                >
                  恢复此版本
                </button>
              </div>
            </div>
          ))
        ) : (
          <div className="empty-version">
            尚无历史版本。执行 AI 自动修复前会自动备份当前原文，最多保留 5
            个版本。
          </div>
        )}
      </div>
      {previewVersion && (
        <div className="modal-backdrop" onClick={() => setPreviewVersion(null)}>
          <section
            className="version-preview-modal"
            role="dialog"
            aria-modal="true"
            onClick={(event) => event.stopPropagation()}
          >
            <header>
              <div>
                <small>版本 v{previewVersion.versionNo}</small>
                <h3>查看版本内容</h3>
              </div>
              <button aria-label="关闭" onClick={() => setPreviewVersion(null)}>
                <X size={18} />
              </button>
            </header>
            <div className="version-preview-tabs">
              <button
                className={previewVersionTab === "draft" ? "active" : ""}
                onClick={() => setPreviewVersionTab("draft")}
              >
                课程原稿
              </button>
              <button
                className={previewVersionTab === "article" ? "active" : ""}
                onClick={() => setPreviewVersionTab("article")}
              >
                公众号版本
              </button>
            </div>
            <div className="version-preview-content">
              {previewVersionTab === "draft" ? (
                pairedDraftVersion ? (
                  <MarkdownReader content={pairedDraftVersion.content} />
                ) : (
                  <div className="empty-version">
                    该版本没有可匹配的课程原稿。
                  </div>
                )
              ) : (
                <MarkdownReader content={previewVersion.content} />
              )}
            </div>
          </section>
        </div>
      )}
    </>
  );
}
