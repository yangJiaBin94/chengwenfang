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
import { parent, text } from "../../hotnews/components/hotNewsExecutionHelpers";

export function ActionButton({
  icon: Icon,
  title,
  text,
  onClick,
}: {
  icon: typeof Bot;
  title: string;
  text: string;
  onClick: () => void;
}) {
  return (
    <button className="action-button" onClick={onClick}>
      <div className="action-icon">
        <Icon size={18} />
      </div>
      <span>
        <strong>{title}</strong>
        <small>{text}</small>
      </span>
    </button>
  );
}

/** Render the selected course tab and coordinate review-specific actions. */

export function EditableMarkdown({
  content,
  hideImageCaptions = false,
  hideImageHeadings = false,
  label,
  onChange,
  onReset,
  onConfirm,
}: {
  content: string;
  hideImageCaptions?: boolean;
  hideImageHeadings?: boolean;
  label: string;
  onChange: (value: string) => Promise<void>;
  onReset: () => Promise<void>;
  onConfirm: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
}) {
  const [mode, setMode] = useState<"preview" | "edit">("preview");
  const [draftValue, setDraftValue] = useState(content);
  const [saveError, setSaveError] = useState("");
  const [saving, setSaving] = useState(false);
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">(
    "idle",
  );

  useEffect(() => {
    if (mode === "preview") setDraftValue(content);
  }, [content, mode]);

  /** Persist the editor buffer only after the explicit save command. */
  async function saveDraft() {
    // Save only on explicit command to avoid rerendering long Markdown on each keystroke.
    setSaving(true);
    setSaveError("");
    try {
      await onChange(draftValue);
      setMode("preview");
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "保存失败。");
    } finally {
      setSaving(false);
    }
  }

  /** Request a restore from the immutable initial imported version. */
  async function resetImportedVersion() {
    // Ask the parent to create a restore version from the initial import.
    if (
      !(await onConfirm(
        "恢复导入版本",
        "确定恢复初始导入版本吗？当前编辑内容会保留在版本历史中。",
      ))
    )
      return;
    setSaveError("");
    try {
      await onReset();
      setMode("preview");
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "恢复失败。");
    }
  }

  /** Copy the complete Markdown currently visible in the preview or editor. */
  async function copyMarkdown() {
    const markdown = mode === "edit" ? draftValue : content;
    try {
      await navigator.clipboard.writeText(markdown);
      setCopyState("copied");
      window.setTimeout(() => setCopyState("idle"), 1800);
    } catch {
      setCopyState("failed");
      window.setTimeout(() => setCopyState("idle"), 2400);
    }
  }

  return (
    <div className="editable-markdown">
      <div className="editor-toolbar">
        <span>{label}</span>
        <div className="segmented-control">
          <button onClick={resetImportedVersion}>恢复导入版本</button>
          <button
            className="markdown-copy-button"
            onClick={copyMarkdown}
            disabled={!(mode === "edit" ? draftValue : content).trim()}
            title="复制全部 Markdown 内容"
          >
            {copyState === "copied" ? (
              <ClipboardCheck size={14} />
            ) : (
              <Clipboard size={14} />
            )}
            {copyState === "copied"
              ? "已复制"
              : copyState === "failed"
                ? "复制失败"
                : "复制 Markdown"}
          </button>
          <button
            className={mode === "preview" ? "active" : ""}
            onClick={() => setMode("preview")}
          >
            预览
          </button>
          <button
            className={mode === "edit" ? "active" : ""}
            onClick={() => setMode("edit")}
          >
            编辑
          </button>
        </div>
      </div>

      {mode === "preview" ? (
        <MarkdownReader
          content={content}
          hideImageCaptions={hideImageCaptions}
          hideImageHeadings={hideImageHeadings}
        />
      ) : (
        <div className="editor-wrap">
          <textarea
            aria-label={label}
            className="markdown-editor"
            value={draftValue}
            onChange={(event) => setDraftValue(event.target.value)}
            spellCheck={false}
          />
          <div className="editor-footer">
            <p>
              {saveError ||
                "点击保存后会写入成文坊数据库。新增 Markdown 图片后，配套图片会同步更新。"}
            </p>
            <button
              className="ghost-button"
              onClick={saveDraft}
              disabled={saving}
            >
              {saving ? "保存中..." : "保存到预览"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/** Read-only Markdown renderer with optional image-caption suppression. */

export function MarkdownReader({
  content,
  hideImageCaptions = false,
  hideImageHeadings = false,
}: {
  content: string;
  hideImageCaptions?: boolean;
  hideImageHeadings?: boolean;
}) {
  const readableContent = hideImageHeadings
    ? normalizeObsidianEmbeds(content).replace(
        /^#{1,6}\s*(?:封面图|插图\s*\d+|图片\s*\d*)\s*[:：].*\n+(?=\s*!\[)/gm,
        "",
      )
    : normalizeObsidianEmbeds(content);

  return (
    <div className="markdown-reader">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          /** Render images with public-path resolution and missing-asset feedback. */
          img({ src = "", alt = "" }) {
            const imageSrc = resolveImageSrc(src);
            return (
              <span className="markdown-image-wrap">
                <img
                  src={imageSrc}
                  alt={alt}
                  loading="lazy"
                  onError={(event) => {
                    event.currentTarget
                      .closest(".markdown-image-wrap")
                      ?.classList.add("missing-image");
                  }}
                />
                {!hideImageCaptions && alt && (
                  <span className="markdown-image-caption">{alt}</span>
                )}
              </span>
            );
          },
        }}
      >
        {readableContent}
      </ReactMarkdown>
    </div>
  );
}
