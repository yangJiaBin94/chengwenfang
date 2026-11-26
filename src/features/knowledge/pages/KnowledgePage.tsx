import { useCallback, useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  BookOpen,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Pencil,
  Plus,
  RefreshCcw,
  ThumbsDown,
  ThumbsUp,
  Trash2,
} from "lucide-react";
import {
  askKnowledgeChat,
  createSession,
  deleteKnowledgeDocument,
  deleteMemory,
  deleteSession,
  ingestKnowledgeWebPage,
  listKnowledgeDocuments,
  listMemories,
  listMessages,
  listSessions,
  loadKnowledgeStats,
  reindexKnowledge,
  reindexKnowledgeDocument,
  renameSession,
  setMemoryPaused,
  submitAnswerFeedback,
  uploadKnowledgeDocument,
  type KnowledgeChatMessage,
  type KnowledgeChatSession,
  type KnowledgeCitation,
  type KnowledgeDocument,
  type KnowledgeIndexStats,
  type KnowledgeMemory,
} from "../api";
import { openProtectedResource } from "../../../shared/api/http";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";

export type KnowledgePageProps = {
  navigate: (path: string) => void;
  showNotice?: (title: string, message?: string) => void;
  confirmAction?: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  promptAction?: (
    title: string,
    message: string,
    options?: {
      inputLabel?: string;
      placeholder?: string;
      defaultValue?: string;
      minLength?: number;
      tone?: "default" | "danger";
    },
  ) => Promise<string | null>;
};

type MainTab = "chat" | "library" | "memory";

function degradeLabel(code: string) {
  if (code === "vector_fallback_memory") return "向量库降级为内存";
  if (code === "graph_unavailable") return "知识图谱不可用";
  return code;
}

function abstainLabel(code?: string) {
  if (code === "insufficient_evidence") return "未找到相关资料";
  if (code === "faithfulness_failed") return "资料不足以支撑回答";
  return code || "已拒答";
}

/** 把后端观测 step 转成用户可读说明。 */
function formatToolStep(step: {
  name?: string;
  resultSummary?: string;
  latencyMs?: number;
  ok?: boolean;
}): string {
  const name = (step.name || "").trim();
  const summary = (step.resultSummary || "").trim();
  const title =
    name === "rewrite"
      ? "理解问题"
      : name === "route"
        ? "选择检索方式"
        : name === "hybrid_search"
          ? "检索知识库"
          : name === "graph_local"
            ? "按关联内容检索"
            : name === "graph_global"
              ? "全局关联检索"
              : name === "faithfulness"
                ? "核对答案依据"
                : name === "context_budget"
                  ? "整理引用材料"
                  : name === "list_work_metadata"
                    ? "查阅作品信息"
                    : name || "处理中";

  let detail = "";
  if (name === "rewrite") {
    const query = summary.match(/(?:^|\b)query=(.+)$/i)?.[1]?.trim();
    detail = query ? `检索用语：${query}` : "";
  } else if (name === "route") {
    const modeMap: Record<string, string> = {
      hybrid: "混合检索",
      graph_local: "关联检索",
      graph_global: "全局检索",
      agent: "多轮检索",
      abstain: "无法作答",
    };
    detail = modeMap[summary] || (summary ? `方式：${summary}` : "");
  } else if (
    name === "hybrid_search" ||
    name === "graph_local" ||
    name === "graph_global"
  ) {
    const hits = summary.match(/hits=(\d+)/i)?.[1];
    if (hits === "0") detail = "未找到相关内容";
    else if (hits) detail = `找到 ${hits} 条相关内容`;
  } else if (name === "faithfulness") {
    if (summary === "fail" || step.ok === false) detail = "未通过";
    else if (summary === "pass" || summary === "ok") detail = "通过";
    else if (summary) detail = summary;
  } else if (name === "context_budget" && summary) {
    detail = "已按长度精简部分摘录";
  } else if (summary && !summary.includes("=")) {
    detail = summary;
  }

  return detail ? `${title} · ${detail}` : title;
}

const CITATION_PREVIEW_LIMIT = 3;

const EXAMPLE_PROMPTS = [
  "某主题下写过哪些引流方法？",
  "AI 是什么？和机器人有什么区别？",
];

function formatSessionTime(value: string) {
  try {
    return new Intl.DateTimeFormat("zh-CN", {
      dateStyle: "short",
      timeStyle: "short",
    }).format(new Date(value));
  } catch {
    return value;
  }
}

function roleIsAssistant(role: string) {
  return role.trim().toLowerCase() === "assistant";
}

function sourceKindLabel(kind: string) {
  const map: Record<string, string> = {
    work_output: "作品",
    WORK_OUTPUT: "作品",
    manual_upload: "手动资料",
    MANUAL_UPLOAD: "手动资料",
    source_item: "信源",
    SOURCE_ITEM: "信源",
    research_item: "调研",
    RESEARCH_ITEM: "调研",
    radar_topic: "雷达选题",
    RADAR_TOPIC: "雷达选题",
  };
  return map[kind] ?? kind;
}

function mediaKindLabel(kind: string | undefined) {
  if (!kind) return "";
  const map: Record<string, string> = {
    article_markdown: "文章",
    ARTICLE_MARKDOWN: "文章",
    plain_text: "纯文本",
    PLAIN_TEXT: "纯文本",
    document: "文档",
    DOCUMENT: "文档",
    web_page: "网页",
    WEB_PAGE: "网页",
    video: "视频",
    VIDEO: "视频",
    image: "图片",
    IMAGE: "图片",
  };
  return map[kind] ?? "";
}

function documentStatusLabel(status: string) {
  const map: Record<string, string> = {
    pending: "待索引",
    PENDING: "待索引",
    indexing: "索引中",
    INDEXING: "索引中",
    ready: "可检索",
    READY: "可检索",
    stale: "待更新",
    STALE: "待更新",
    failed: "失败",
    FAILED: "失败",
  };
  return map[status] ?? status;
}

function modeLabel(mode: string | undefined) {
  if (!mode) return "";
  const map: Record<string, string> = {
    hybrid: "混合检索",
    graph_local: "图局部",
    graph_global: "图全局",
    agent: "多轮检索",
    abstain: "拒答",
  };
  return map[mode] ?? "";
}

/** 课程作品技术 ID：course-{theme}-{uuid…} */
const COURSE_TECHNICAL_TITLE = /^course-[a-z0-9_]+-[0-9a-f-]{8,}/i;

/**
 * 引用标题展示：技术 ID 换成章节首段，并保留原稿/公众号后缀。
 *
 * @param title 入库或历史消息里的标题
 * @param headingPath 章节路径
 */
function displayCitationTitle(title?: string, headingPath?: string) {
  const raw = (title ?? "").trim();
  let base = raw;
  let suffix = "";
  if (raw.endsWith(" 原稿")) {
    base = raw.slice(0, -3).trim();
    suffix = "原稿";
  } else if (raw.endsWith(" · 原稿")) {
    base = raw.slice(0, -5).trim();
    suffix = "原稿";
  } else if (raw.endsWith(" 公众号")) {
    base = raw.slice(0, -4).trim();
    suffix = "公众号";
  } else if (raw.endsWith(" · 公众号")) {
    base = raw.slice(0, -5).trim();
    suffix = "公众号";
  }
  const technical =
    !base ||
    COURSE_TECHNICAL_TITLE.test(base) ||
    base.startsWith("course-");
  if (technical) {
    const first = (headingPath ?? "").split(">")[0]?.trim() ?? "";
    base = first || "未命名文档";
  }
  return suffix ? `${base} · ${suffix}` : base || "未命名文档";
}

function truncatePath(path: string, max = 36) {
  const text = path.trim();
  if (text.length <= max) return text;
  return `${text.slice(0, max - 1)}…`;
}

function CitationList({
  citations,
  onNavigate,
  highlightIndex,
}: {
  citations: KnowledgeCitation[];
  onNavigate: (path: string) => void;
  highlightIndex: number | null;
}) {
  const [expanded, setExpanded] = useState<Record<number, boolean>>({});
  const [showAll, setShowAll] = useState(false);
  const rowRefs = useRef<Record<number, HTMLDivElement | null>>({});

  useEffect(() => {
    if (highlightIndex == null || highlightIndex < 0) return;
    setShowAll(true);
    setExpanded((current) => ({ ...current, [highlightIndex]: true }));
    // 等折叠行挂载后再滚，避免 n>3 时 scrollIntoView 打空
    const timer = window.setTimeout(() => {
      rowRefs.current[highlightIndex]?.scrollIntoView({
        behavior: "smooth",
        block: "nearest",
      });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [highlightIndex]);

  const visible = showAll
    ? citations
    : citations.slice(0, CITATION_PREVIEW_LIMIT);
  const hiddenCount = Math.max(0, citations.length - CITATION_PREVIEW_LIMIT);

  return (
    <div className="knowledge-citations">
      <span className="panel-label">引用来源</span>
      {visible.map((citation, index) => {
        const open = Boolean(expanded[index]);
        const clickable = Boolean(citation.deepLink?.trim());
        const media = mediaKindLabel(citation.mediaKind);
        const highlighted = highlightIndex === index;
        return (
          <div
            key={`${citation.chunkId ?? citation.documentId}-${index}`}
            ref={(node) => {
              rowRefs.current[index] = node;
            }}
            className={`knowledge-citation-row${highlighted ? " is-highlight" : ""}`}
            id={`knowledge-cite-${index + 1}`}
          >
            <button
              type="button"
              className="knowledge-citation-toggle"
              onClick={() =>
                setExpanded((current) => ({ ...current, [index]: !open }))
              }
            >
              <span className="knowledge-cite-index">[{index + 1}]</span>
              <span className="knowledge-cite-title">
                {displayCitationTitle(citation.title, citation.headingPath)}
                {citation.headingPath
                  ? ` · ${truncatePath(citation.headingPath)}`
                  : ""}
              </span>
              <span className="knowledge-badges">
                <span className="knowledge-badge">
                  {sourceKindLabel(citation.sourceKind)}
                </span>
                {media ? (
                  <span className="knowledge-badge muted">{media}</span>
                ) : null}
              </span>
            </button>
            {open ? (
              <div className="knowledge-citation-body">
                {citation.headingPath ? (
                  <small className="knowledge-citation-path">
                    {citation.headingPath}
                  </small>
                ) : null}
                <p>{citation.snippet}</p>
                <small className="knowledge-citation-note">
                  {clickable
                    ? "可跳转来源"
                    : "摘自解析文本（非原件页码）"}
                </small>
                {clickable ? (
                  <button
                    type="button"
                    className="knowledge-citation-open"
                    onClick={() => onNavigate(citation.deepLink!.trim())}
                  >
                    打开原文
                  </button>
                ) : null}
              </div>
            ) : null}
          </div>
        );
      })}
      {!showAll && hiddenCount > 0 ? (
        <button
          type="button"
          className="knowledge-cite-more"
          onClick={() => setShowAll(true)}
        >
          展开其余 {hiddenCount} 条
        </button>
      ) : null}
    </div>
  );
}

function MessageBubble({
  message,
  navigate,
  streaming,
  showNotice,
  promptAction,
}: {
  message: KnowledgeChatMessage;
  navigate: (path: string) => void;
  streaming?: boolean;
  showNotice?: (title: string, message?: string) => void;
  promptAction?: KnowledgePageProps["promptAction"];
}) {
  const assistant = roleIsAssistant(message.role);
  const citations = message.citations ?? [];
  const [highlightIndex, setHighlightIndex] = useState<number | null>(null);
  const modeText = modeLabel(message.mode);

  const renderTextWithCitations = (text: string) => {
    const parts = text.split(/(\[\d+\])/g);
    return parts.map((part, partIndex) => {
      const match = /^\[(\d+)\]$/.exec(part);
      if (!match) {
        return <span key={`t-${partIndex}`}>{part}</span>;
      }
      const oneBased = Number(match[1]);
      const targetIndex = oneBased - 1;
      const enabled =
        Number.isFinite(oneBased) &&
        targetIndex >= 0 &&
        targetIndex < citations.length;
      return (
        <button
          key={`c-${partIndex}`}
          type="button"
          className="knowledge-inline-cite"
          disabled={!enabled}
          onClick={() => {
            if (enabled) setHighlightIndex(targetIndex);
          }}
        >
          [{oneBased}]
        </button>
      );
    });
  };

  return (
    <article
      className={`knowledge-message${assistant ? " is-assistant" : " is-user"}${
        message.abstained ? " is-abstain" : ""
      }`}
    >
      <div className="knowledge-message-meta">
        <span>{assistant ? "助手" : "你"}</span>
        {assistant && modeText ? (
          <small className="knowledge-mode-tag">
            {modeText}
            {message.rounds && message.rounds > 0 ? ` · ${message.rounds} 轮` : ""}
          </small>
        ) : null}
        {assistant && message.degradedReasons?.length ? (
          <small className="knowledge-degraded-tag">
            检索降级：
            {message.degradedReasons.map(degradeLabel).join("；")}
          </small>
        ) : null}
        {assistant && message.abstained ? (
          <small className="knowledge-abstain-tag">
            拒答：{abstainLabel(message.abstainReason)}
          </small>
        ) : null}
        {!streaming && message.createdAt ? (
          <small>{formatSessionTime(message.createdAt)}</small>
        ) : null}
        {streaming ? (
          <small className="knowledge-streaming">
            <Loader2 size={12} className="spin" /> 生成中…
          </small>
        ) : null}
      </div>
      <div className="knowledge-message-body">
        {assistant ? (
          <div className="markdown-reader knowledge-markdown">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                p: ({ children }) => {
                  const flat = flattenText(children);
                  if (flat.includes("[") && /\[\d+\]/.test(flat)) {
                    return <p>{renderTextWithCitations(flat)}</p>;
                  }
                  return <p>{children}</p>;
                },
                li: ({ children }) => {
                  const flat = flattenText(children);
                  if (flat.includes("[") && /\[\d+\]/.test(flat)) {
                    return <li>{renderTextWithCitations(flat)}</li>;
                  }
                  return <li>{children}</li>;
                },
              }}
            >
              {message.content || (streaming ? "…" : "")}
            </ReactMarkdown>
          </div>
        ) : (
          <p>{message.content}</p>
        )}
      </div>
      {assistant && citations.length > 0 ? (
        <CitationList
          citations={citations}
          onNavigate={navigate}
          highlightIndex={highlightIndex}
        />
      ) : null}
      {assistant && message.toolSteps?.length ? (
        <details className="knowledge-tool-steps">
          <summary>回答过程（{message.toolSteps.length} 步）</summary>
          <ol>
            {message.toolSteps.map((step, index) => (
              <li key={`${step.name}-${index}`}>{formatToolStep(step)}</li>
            ))}
          </ol>
        </details>
      ) : null}
      {assistant && !streaming && message.id && !message.id.startsWith("tmp-") ? (
        <div className="knowledge-feedback-row" aria-label="回答反馈">
          <button
            type="button"
            className="knowledge-feedback-btn is-up"
            title="有用"
            onClick={() => {
              void submitAnswerFeedback(message.id, "up")
                .then(() => showNotice?.("已记录赞"))
                .catch((error) =>
                  showNotice?.(
                    "反馈失败",
                    error instanceof Error ? error.message : "无法提交赞",
                  ),
                );
            }}
          >
            <ThumbsUp size={13} strokeWidth={2} aria-hidden />
            有用
          </button>
          <button
            type="button"
            className="knowledge-feedback-btn is-down"
            title="不准（可附备注，生成难例 JSON）"
            onClick={() => {
              void (async () => {
                const note = promptAction
                  ? await promptAction(
                      "标记不准",
                      "可附备注说明问题；将生成难例候选 JSON（不自动进 CI）。",
                      {
                        inputLabel: "问题备注（可选）",
                        placeholder: "例如：引用不对、结论过时…",
                      },
                    )
                  : "";
                if (note == null) return;
                try {
                  const result = await submitAnswerFeedback(
                    message.id,
                    "down",
                    note,
                  );
                  if (result.goldenCandidateJson) {
                    void navigator.clipboard?.writeText(
                      result.goldenCandidateJson,
                    );
                    showNotice?.(
                      "已记录踩",
                      "难例候选 JSON 已复制到剪贴板",
                    );
                  } else {
                    showNotice?.("已记录踩");
                  }
                } catch (error) {
                  showNotice?.(
                    "反馈失败",
                    error instanceof Error ? error.message : "无法提交踩",
                  );
                }
              })();
            }}
          >
            <ThumbsDown size={13} strokeWidth={2} aria-hidden />
            不准
          </button>
        </div>
      ) : null}
    </article>
  );
}

function flattenText(children: React.ReactNode): string {
  if (children == null || typeof children === "boolean") return "";
  if (typeof children === "string" || typeof children === "number") {
    return String(children);
  }
  if (Array.isArray(children)) {
    return children.map(flattenText).join("");
  }
  if (typeof children === "object" && "props" in children) {
    const element = children as { props?: { children?: React.ReactNode } };
    return flattenText(element.props?.children);
  }
  return "";
}

export function KnowledgePage({
  navigate,
  showNotice,
  confirmAction,
  promptAction,
}: KnowledgePageProps) {
  const [mainTab, setMainTab] = useState<MainTab>("chat");
  const [stats, setStats] = useState<KnowledgeIndexStats | null>(null);
  const [statsLoading, setStatsLoading] = useState(true);
  const [reindexing, setReindexing] = useState(false);
  const [sessions, setSessions] = useState<KnowledgeChatSession[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<KnowledgeChatMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [draft, setDraft] = useState("");
  const [asking, setAsking] = useState(false);
  const [streamingMessage, setStreamingMessage] = useState<KnowledgeChatMessage | null>(
    null,
  );
  const [renamingSessionId, setRenamingSessionId] = useState<string | null>(null);
  const [renameDraft, setRenameDraft] = useState("");
  const renameInputRef = useRef<HTMLInputElement | null>(null);
  const skipRenameBlurRef = useRef(false);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  const refreshStats = useCallback(async () => {
    setStatsLoading(true);
    try {
      const data = await loadKnowledgeStats();
      setStats(data);
    } catch (error) {
      showNotice?.(
        "统计加载失败",
        error instanceof Error ? error.message : "无法读取知识库统计",
      );
    } finally {
      setStatsLoading(false);
    }
  }, [showNotice]);

  const refreshSessions = useCallback(async () => {
    setSessionsLoading(true);
    try {
      const items = await listSessions();
      setSessions(items);
      setActiveSessionId((current) => {
        if (current && items.some((item) => item.id === current)) {
          return current;
        }
        return items[0]?.id ?? null;
      });
    } catch (error) {
      showNotice?.(
        "会话加载失败",
        error instanceof Error ? error.message : "无法列出对话",
      );
    } finally {
      setSessionsLoading(false);
    }
  }, [showNotice]);

  useEffect(() => {
    void refreshStats();
    void refreshSessions();
  }, [refreshStats, refreshSessions]);

  useEffect(() => {
    if (!activeSessionId) {
      setMessages([]);
      return;
    }
    let cancelled = false;
    setMessagesLoading(true);
    void listMessages(activeSessionId)
      .then((items) => {
        if (!cancelled) setMessages(items);
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          showNotice?.(
            "消息加载失败",
            error instanceof Error ? error.message : "无法读取历史消息",
          );
          setMessages([]);
        }
      })
      .finally(() => {
        if (!cancelled) setMessagesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeSessionId, showNotice]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, streamingMessage]);

  async function handleReindex() {
    setReindexing(true);
    try {
      const job = await reindexKnowledge();
      showNotice?.(
        "已提交重建索引",
        `任务 ${job.id.slice(0, 8)}… 已入队；完成后看本页索引统计与站内通知（知识库任务不走课程执行轨迹）。`,
      );
      await refreshStats();
    } catch (error) {
      showNotice?.(
        "重建索引失败",
        error instanceof Error ? error.message : "提交失败",
      );
    } finally {
      setReindexing(false);
    }
  }

  async function handleNewSession() {
    try {
      const session = await createSession();
      setSessions((current) => [session, ...current]);
      setActiveSessionId(session.id);
      setMessages([]);
      setDraft("");
    } catch (error) {
      showNotice?.(
        "创建会话失败",
        error instanceof Error ? error.message : "无法创建对话",
      );
    }
  }

  function beginRenameSession(session: KnowledgeChatSession) {
    skipRenameBlurRef.current = false;
    setRenamingSessionId(session.id);
    setRenameDraft(session.title || "");
  }

  function cancelRenameSession() {
    setRenamingSessionId(null);
    setRenameDraft("");
  }

  async function commitRenameSession(session: KnowledgeChatSession) {
    const title = renameDraft.trim();
    if (!title) {
      showNotice?.("重命名失败", "标题不能为空");
      return;
    }
    if (title === (session.title || "").trim()) {
      cancelRenameSession();
      return;
    }
    try {
      const updated = await renameSession(session.id, title);
      setSessions((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      cancelRenameSession();
    } catch (error) {
      showNotice?.(
        "重命名失败",
        error instanceof Error ? error.message : "无法重命名",
      );
    }
  }

  useEffect(() => {
    if (!renamingSessionId) return;
    renameInputRef.current?.focus();
    renameInputRef.current?.select();
  }, [renamingSessionId]);

  async function handleDeleteSession(session: KnowledgeChatSession) {
    const confirmed = confirmAction
      ? await confirmAction(
          "删除对话？",
          `删除「${session.title || "未命名"}」后不可恢复。`,
          "danger",
        )
      : false;
    if (!confirmed) return;
    try {
      await deleteSession(session.id);
      setSessions((current) => {
        const next = current.filter((item) => item.id !== session.id);
        if (activeSessionId === session.id) {
          setActiveSessionId(next[0]?.id ?? null);
          setMessages([]);
        }
        return next;
      });
    } catch (error) {
      showNotice?.(
        "删除失败",
        error instanceof Error ? error.message : "无法删除会话",
      );
    }
  }

  async function handleSend() {
    const text = draft.trim();
    if (!text || asking) return;

    let sessionId = activeSessionId;
    if (!sessionId) {
      try {
        const session = await createSession();
        sessionId = session.id;
        setSessions((current) => [session, ...current]);
        setActiveSessionId(session.id);
      } catch (error) {
        showNotice?.(
          "创建会话失败",
          error instanceof Error ? error.message : "无法创建对话",
        );
        return;
      }
    }

    const optimisticUser: KnowledgeChatMessage = {
      id: `local-user-${Date.now()}`,
      role: "user",
      content: text,
      createdAt: new Date().toISOString(),
    };
    setMessages((current) => [...current, optimisticUser]);
    setDraft("");
    setAsking(true);
    setStreamingMessage({
      id: `local-assistant-${Date.now()}`,
      role: "assistant",
      content: "",
      citations: [],
      createdAt: new Date().toISOString(),
    });

    try {
      const result = await askKnowledgeChat(sessionId, text, {
        onCitations: (citations) => {
          setStreamingMessage((current) =>
            current ? { ...current, citations } : current,
          );
        },
        onAnswerChunk: (content) => {
          setStreamingMessage((current) =>
            current ? { ...current, content } : current,
          );
        },
      });

      const assistantMessage: KnowledgeChatMessage = {
        ...(result.message ?? {
          id: `assistant-${Date.now()}`,
          role: "assistant",
          content: result.content,
          citations: result.citations,
          createdAt: new Date().toISOString(),
        }),
        abstained: result.abstained,
        mode: result.mode,
        rounds: result.rounds,
        degradedReasons: result.degradedReasons,
        abstainReason: result.abstainReason,
        toolSteps: result.message?.toolSteps,
      };

      setStreamingMessage(null);
      setMessages((current) => [...current, assistantMessage]);
      if (result.degradedReasons?.length) {
        showNotice?.(
          "检索降级",
          result.degradedReasons.map(degradeLabel).join("；"),
        );
      }
      void refreshSessions();
    } catch (error) {
      setStreamingMessage(null);
      showNotice?.(
        "问答失败",
        error instanceof Error ? error.message : "知识库回答异常",
      );
    } finally {
      setAsking(false);
    }
  }

  const ready = stats?.ready ?? 0;
  const total = stats?.total ?? 0;

  return (
    <section className="knowledge-page">
      <header className="knowledge-topbar">
        <div>
          <span className="section-kicker">Knowledge RAG</span>
          <h2>知识库对话</h2>
          <p>
            基于已索引作品内容检索并回答问题。索引就绪后可对话；引用可展开查看摘录并跳转原文。
          </p>
        </div>
        <div className="knowledge-topbar-actions">
          <StatsPanel
            loading={statsLoading}
            ready={ready}
            total={total}
            failed={stats?.failed ?? 0}
          />
          <button
            type="button"
            className="secondary-button knowledge-reindex"
            onClick={() => void handleReindex()}
            disabled={reindexing}
          >
            <RefreshCcw size={15} />
            {reindexing ? "提交中…" : "重建索引"}
          </button>
        </div>
      </header>

      <nav className="knowledge-tabs" aria-label="知识库分区">
        <button
          type="button"
          className={mainTab === "chat" ? "active" : ""}
          onClick={() => setMainTab("chat")}
        >
          对话
        </button>
        <button
          type="button"
          className={mainTab === "library" ? "active" : ""}
          onClick={() => setMainTab("library")}
        >
          资料库
        </button>
        <button
          type="button"
          className={mainTab === "memory" ? "active" : ""}
          onClick={() => setMainTab("memory")}
        >
          长期记忆
        </button>
      </nav>

      {mainTab === "library" ? (
        <LibraryPanel showNotice={showNotice} confirmAction={confirmAction} />
      ) : mainTab === "memory" ? (
        <MemoryPanel showNotice={showNotice} confirmAction={confirmAction} />
      ) : (
        <div className="knowledge-chat-layout">
          <aside className="knowledge-session-list">
            <div className="knowledge-session-head">
              <strong>对话记录</strong>
              <button
                type="button"
                className="knowledge-new-chat"
                onClick={() => void handleNewSession()}
              >
                <Plus size={14} />
                新对话
              </button>
            </div>
            {sessionsLoading ? (
              <p className="knowledge-session-hint">加载中…</p>
            ) : sessions.length === 0 ? (
              <p className="knowledge-session-hint">
                暂无会话，点击「新对话」开始。
              </p>
            ) : (
              <div className="knowledge-session-items">
                {sessions.map((session) => {
                  const renaming = renamingSessionId === session.id;
                  return (
                    <div
                      key={session.id}
                      className={
                        session.id === activeSessionId
                          ? "knowledge-session-item active"
                          : "knowledge-session-item"
                      }
                    >
                      {renaming ? (
                        <div className="knowledge-session knowledge-session-editing">
                          <input
                            ref={renameInputRef}
                            className="knowledge-session-rename-input"
                            value={renameDraft}
                            aria-label="对话标题"
                            onChange={(event) => setRenameDraft(event.target.value)}
                            onBlur={() => {
                              if (skipRenameBlurRef.current) {
                                skipRenameBlurRef.current = false;
                                return;
                              }
                              void commitRenameSession(session);
                            }}
                            onKeyDown={(event) => {
                              if (event.key === "Enter") {
                                event.preventDefault();
                                skipRenameBlurRef.current = true;
                                void commitRenameSession(session);
                              } else if (event.key === "Escape") {
                                event.preventDefault();
                                skipRenameBlurRef.current = true;
                                cancelRenameSession();
                              }
                            }}
                          />
                          <small>{formatSessionTime(session.updatedAt)}</small>
                        </div>
                      ) : (
                        <button
                          type="button"
                          className="knowledge-session"
                          onClick={() => setActiveSessionId(session.id)}
                        >
                          <strong>{session.title || "未命名对话"}</strong>
                          <small>{formatSessionTime(session.updatedAt)}</small>
                        </button>
                      )}
                      <div className="knowledge-session-actions">
                        <button
                          type="button"
                          title="重命名"
                          aria-label="重命名"
                          disabled={renaming}
                          onClick={() => beginRenameSession(session)}
                        >
                          <Pencil size={12} />
                        </button>
                        <button
                          type="button"
                          title="删除"
                          aria-label="删除"
                          disabled={renaming}
                          onClick={() => void handleDeleteSession(session)}
                        >
                          <Trash2 size={12} />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </aside>

          <ChatPanel
            messagesLoading={messagesLoading}
            messages={messages}
            streamingMessage={asking ? streamingMessage : null}
            navigate={navigate}
            draft={draft}
            onDraftChange={setDraft}
            onSend={() => void handleSend()}
            asking={asking}
            messagesEndRef={messagesEndRef}
            showNotice={showNotice}
            promptAction={promptAction}
          />
        </div>
      )}
    </section>
  );
}

function StatsPanel({
  loading,
  ready,
  total,
  failed,
}: {
  loading: boolean;
  ready: number;
  total: number;
  failed: number;
}) {
  const indexed = !loading && total > 0 && ready === total && failed === 0;

  return (
    <div className={`knowledge-stats${indexed ? " is-ready" : ""}`}>
      <span className="knowledge-stats-icon" aria-hidden="true">
        <BookOpen size={15} />
      </span>
      <div>
        <strong>{loading ? "—" : `${ready} / ${total}`}</strong>
        <small>
          可检索 / 文档总数{failed > 0 ? ` · 失败 ${failed}` : ""}
        </small>
      </div>
    </div>
  );
}

function LibraryPanel({
  showNotice,
  confirmAction,
}: {
  showNotice?: (title: string, message?: string) => void;
  confirmAction?: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
}) {
  const [items, setItems] = useState<KnowledgeDocument[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [url, setUrl] = useState("");
  const [keyword, setKeyword] = useState("");
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [sourceFilter, setSourceFilter] = useState("");
  const [mediaFilter, setMediaFilter] = useState("");
  const [createdFrom, setCreatedFrom] = useState("");
  const [createdTo, setCreatedTo] = useState("");
  const [listPage, setListPage] = useState(1);
  const [pageSize, setPageSize] = useState<ListPageSize>(10);
  const [pageSizeOpen, setPageSizeOpen] = useState(false);
  const pageSizeRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setListPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    if (!pageSizeOpen) return;
    function onPointerDown(event: MouseEvent) {
      if (!pageSizeRef.current?.contains(event.target as Node)) {
        setPageSizeOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setPageSizeOpen(false);
    }
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [pageSizeOpen]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listKnowledgeDocuments({
        page: listPage,
        pageSize,
        sourceKind: sourceFilter || undefined,
        keyword: debouncedKeyword || undefined,
        mediaKind: mediaFilter || undefined,
        createdFrom: createdFrom || undefined,
        createdTo: createdTo || undefined,
      });
      setItems(result.items);
      setTotal(result.total);
      if (result.page !== listPage) {
        setListPage(result.page);
      }
    } catch (error) {
      setItems([]);
      setTotal(0);
      showNotice?.(
        "加载资料库失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    } finally {
      setLoading(false);
    }
  }, [
    createdFrom,
    createdTo,
    debouncedKeyword,
    listPage,
    mediaFilter,
    pageSize,
    showNotice,
    sourceFilter,
  ]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const listPageCount = Math.max(1, Math.ceil(total / pageSize));

  const handleUpload = async (fileList: FileList | null) => {
    if (!fileList?.length) return;
    setUploading(true);
    try {
      for (const file of Array.from(fileList)) {
        await uploadKnowledgeDocument(file);
      }
      showNotice?.("上传成功", "已入队索引，稍后可在列表查看状态");
      // 已在第 1 页则直接刷新；否则改页码由 effect 拉最新列表
      if (listPage === 1) {
        await refresh();
      } else {
        setListPage(1);
      }
    } catch (error) {
      showNotice?.(
        "上传失败",
        error instanceof Error ? error.message : "请检查文件类型与大小",
      );
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleIngestUrl = async () => {
    const trimmed = url.trim();
    if (!trimmed) {
      showNotice?.("请输入网址");
      return;
    }
    setUploading(true);
    try {
      await ingestKnowledgeWebPage(trimmed);
      setUrl("");
      showNotice?.("网页已登记", "正文抓取完成并已入队索引");
      if (listPage === 1) {
        await refresh();
      } else {
        setListPage(1);
      }
    } catch (error) {
      showNotice?.(
        "网页入库失败",
        error instanceof Error ? error.message : "抓取失败",
      );
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (document: KnowledgeDocument) => {
    const ok = confirmAction
      ? await confirmAction(
          "删除资料",
          `确定删除「${document.title || document.id}」？索引与原文附件将一并清理。`,
          "danger",
        )
      : window.confirm("确定删除该资料？");
    if (!ok) return;
    try {
      await deleteKnowledgeDocument(document.id);
      await refresh();
    } catch (error) {
      showNotice?.(
        "删除失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    }
  };

  const handleReindexOne = async (documentId: string) => {
    try {
      await reindexKnowledgeDocument(documentId);
      showNotice?.("已提交重建索引");
      await refresh();
    } catch (error) {
      showNotice?.(
        "重建失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    }
  };

  return (
    <article className="panel knowledge-library-panel">
      <div className="knowledge-library-head">
        <div>
          <span className="panel-label">资料库</span>
          <h3>手动上传与素材管理</h3>
          <p>
            支持 txt / md / docx / 数字 PDF；网页可粘贴 URL。引用展开时展示解析文本摘录。
          </p>
        </div>
        <div className="knowledge-library-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={() => void refresh()}
            disabled={loading}
          >
            <RefreshCcw size={14} />
            刷新
          </button>
          <button
            type="button"
            className="primary-button"
            disabled={uploading}
            onClick={() => fileInputRef.current?.click()}
          >
            {uploading ? "处理中…" : "上传文件"}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            hidden
            accept=".txt,.md,.markdown,.docx,.pdf,.pptx,.xlsx,.csv"
            multiple
            onChange={(event) => void handleUpload(event.target.files)}
          />
        </div>
      </div>

      <div className="knowledge-library-url">
        <input
          type="url"
          placeholder="粘贴网页 URL 入库"
          value={url}
          onChange={(event) => setUrl(event.target.value)}
          disabled={uploading}
        />
        <button
          type="button"
          className="secondary-button"
          disabled={uploading}
          onClick={() => void handleIngestUrl()}
        >
          抓取入库
        </button>
      </div>

      <div className="knowledge-library-filters">
        <input
          type="search"
          placeholder="按名称搜索"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          aria-label="按名称搜索"
        />
        <input
          type="date"
          value={createdFrom}
          onChange={(event) => {
            setCreatedFrom(event.target.value);
            setListPage(1);
          }}
          aria-label="入库日起"
        />
        <input
          type="date"
          value={createdTo}
          onChange={(event) => {
            setCreatedTo(event.target.value);
            setListPage(1);
          }}
          aria-label="入库日止"
        />
        <select
          value={mediaFilter}
          onChange={(event) => {
            setMediaFilter(event.target.value);
            setListPage(1);
          }}
          aria-label="按文件类型筛选"
        >
          <option value="">全部类型</option>
          <option value="article_markdown">文章</option>
          <option value="plain_text">纯文本</option>
          <option value="document">文档</option>
          <option value="web_page">网页</option>
          <option value="video">视频</option>
          <option value="image">图片</option>
        </select>
        <select
          value={sourceFilter}
          onChange={(event) => {
            setSourceFilter(event.target.value);
            setListPage(1);
          }}
          aria-label="按来源筛选"
        >
          <option value="">全部来源</option>
          <option value="manual_upload">手动资料</option>
          <option value="work_output">作品</option>
          <option value="source_item">信源</option>
          <option value="research_item">调研</option>
          <option value="radar_topic">雷达选题</option>
        </select>
      </div>

      <div className="knowledge-library-body">
        {loading ? (
          <p className="knowledge-library-empty">加载中…</p>
        ) : items.length === 0 ? (
          <p className="knowledge-library-empty">
            {debouncedKeyword ||
            sourceFilter ||
            mediaFilter ||
            createdFrom ||
            createdTo
              ? "没有符合筛选条件的资料。"
              : "暂无资料，先上传文件或抓取网页。"}
          </p>
        ) : (
          <div className="knowledge-library-list">
            {items.map((document) => (
              <div key={document.id} className="knowledge-library-item">
                <div className="knowledge-library-item-main">
                  <strong>
                    {displayCitationTitle(document.title) || document.id}
                  </strong>
                  <div className="knowledge-badges">
                    <span className="knowledge-badge">
                      {sourceKindLabel(document.sourceKind)}
                    </span>
                    {mediaKindLabel(document.mediaKind) ? (
                      <span className="knowledge-badge muted">
                        {mediaKindLabel(document.mediaKind)}
                      </span>
                    ) : null}
                    <span
                      className={`knowledge-badge status-${String(document.status).toLowerCase()}`}
                    >
                      {documentStatusLabel(document.status)}
                    </span>
                  </div>
                  {document.createdAt ? (
                    <small className="knowledge-library-meta">
                      入库时间 {formatSessionTime(document.createdAt)}
                    </small>
                  ) : null}
                  {document.errorMessage ? (
                    <small className="knowledge-library-error">
                      {document.errorMessage}
                    </small>
                  ) : null}
                </div>
                <div className="knowledge-library-item-actions">
                  {document.textFileId ? (
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => {
                        void openProtectedResource(
                          `/api/files/${encodeURIComponent(document.textFileId!)}`,
                        ).catch((error: unknown) => {
                          showNotice?.(
                            "打开失败",
                            error instanceof Error ? error.message : "无法打开解析文本",
                          );
                        });
                      }}
                    >
                      解析文本
                    </button>
                  ) : null}
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => void handleReindexOne(document.id)}
                  >
                    重建索引
                  </button>
                  <button
                    type="button"
                    className="secondary-button knowledge-library-danger"
                    onClick={() => void handleDelete(document)}
                  >
                    <Trash2 size={14} />
                    删除
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {total > 0 ? (
        <nav
          className="source-management-pager knowledge-library-pager"
          aria-label="资料库分页"
        >
          <span className="source-management-pager-total">共 {total} 条</span>
          <div className="source-management-pager-controls">
            <button
              type="button"
              className="source-management-pager-nav"
              disabled={listPage <= 1 || loading}
              aria-label="上一页"
              onClick={() => setListPage((page) => Math.max(1, page - 1))}
            >
              <ChevronLeft size={16} />
              上一页
            </button>
            {listPageNumbers(listPage, listPageCount).map((token, index) =>
              token === "ellipsis" ? (
                <span
                  className="source-management-pager-ellipsis"
                  key={`ellipsis-${index}`}
                >
                  …
                </span>
              ) : (
                <button
                  type="button"
                  key={token}
                  className={`source-management-pager-page${token === listPage ? " active" : ""}`}
                  disabled={loading}
                  aria-current={token === listPage ? "page" : undefined}
                  aria-label={`第 ${token} 页`}
                  onClick={() => setListPage(token)}
                >
                  {token}
                </button>
              ),
            )}
            <button
              type="button"
              className="source-management-pager-nav"
              disabled={listPage >= listPageCount || loading}
              aria-label="下一页"
              onClick={() =>
                setListPage((page) => Math.min(listPageCount, page + 1))
              }
            >
              下一页
              <ChevronRight size={16} />
            </button>
          </div>
          <div className="source-management-pager-size" ref={pageSizeRef}>
            <span>每页</span>
            <button
              type="button"
              className={`source-management-pager-size-trigger${pageSizeOpen ? " open" : ""}`}
              disabled={loading}
              aria-label="每页条数"
              aria-haspopup="listbox"
              aria-expanded={pageSizeOpen}
              onClick={() => setPageSizeOpen((openMenu) => !openMenu)}
            >
              <em>{pageSize} 条</em>
              <ChevronDown size={14} />
            </button>
            {pageSizeOpen ? (
              <ul
                className="source-management-pager-size-menu"
                role="listbox"
                aria-label="可选每页条数"
              >
                {LIST_PAGE_SIZE_OPTIONS.map((size) => {
                  const active = size === pageSize;
                  return (
                    <li key={size} role="option" aria-selected={active}>
                      <button
                        type="button"
                        className={active ? "active" : undefined}
                        onClick={() => {
                          setPageSize(size);
                          setListPage(1);
                          setPageSizeOpen(false);
                        }}
                      >
                        <span>{size} 条</span>
                        {active ? <Check size={14} /> : null}
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : null}
          </div>
        </nav>
      ) : null}
    </article>
  );
}

function MemoryPanel({
  showNotice,
  confirmAction,
}: {
  showNotice?: (title: string, message?: string) => void;
  confirmAction?: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
}) {
  const [items, setItems] = useState<KnowledgeMemory[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setItems(await listMemories());
    } catch (error) {
      showNotice?.(
        "加载记忆失败",
        error instanceof Error ? error.message : "无法读取长期记忆",
      );
    } finally {
      setLoading(false);
    }
  }, [showNotice]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <article className="panel knowledge-library-panel knowledge-memory-panel">
      <div className="knowledge-library-head">
        <div>
          <span className="panel-label">长期记忆</span>
          <h3>显式「请记住」写入的跨会话记忆</h3>
          <p>可暂停召回或删除；用户体系落地前共用默认作用域。</p>
        </div>
        <div className="knowledge-library-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={() => void refresh()}
            disabled={loading}
          >
            <RefreshCcw size={14} />
            刷新
          </button>
        </div>
      </div>
      {loading ? (
        <p className="knowledge-memory-empty">加载中…</p>
      ) : items.length === 0 ? (
        <p className="knowledge-memory-empty">
          暂无记忆。对话中说「请记住：…」即可写入。
        </p>
      ) : (
        <ul className="knowledge-memory-list">
          {items.map((item) => (
            <li key={item.id} className="knowledge-memory-item">
              <div>
                <p>{item.content}</p>
                <small>
                  {item.paused ? "已暂停 · " : ""}
                  {item.provenance || "user_explicit"}
                  {item.createdAt ? ` · ${formatSessionTime(item.createdAt)}` : ""}
                </small>
              </div>
              <div className="knowledge-memory-actions">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => {
                    void (async () => {
                      try {
                        await setMemoryPaused(item.id, !item.paused);
                        await refresh();
                      } catch (error) {
                        showNotice?.(
                          "更新失败",
                          error instanceof Error ? error.message : "无法更新记忆",
                        );
                      }
                    })();
                  }}
                >
                  {item.paused ? "恢复" : "暂停"}
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => {
                    void (async () => {
                      const confirmed = confirmAction
                        ? await confirmAction(
                            "删除这条记忆？",
                            "删除后不可恢复，相关对话将不再召回该记忆。",
                            "danger",
                          )
                        : false;
                      if (!confirmed) return;
                      try {
                        await deleteMemory(item.id);
                        await refresh();
                      } catch (error) {
                        showNotice?.(
                          "删除失败",
                          error instanceof Error ? error.message : "无法删除记忆",
                        );
                      }
                    })();
                  }}
                >
                  删除
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}

function ChatPanel({
  messagesLoading,
  messages,
  streamingMessage,
  navigate,
  draft,
  onDraftChange,
  onSend,
  asking,
  messagesEndRef,
  showNotice,
  promptAction,
}: {
  messagesLoading: boolean;
  messages: KnowledgeChatMessage[];
  streamingMessage: KnowledgeChatMessage | null;
  navigate: (path: string) => void;
  draft: string;
  onDraftChange: (value: string) => void;
  onSend: () => void;
  asking: boolean;
  messagesEndRef: React.RefObject<HTMLDivElement | null>;
  showNotice?: (title: string, message?: string) => void;
  promptAction?: KnowledgePageProps["promptAction"];
}) {
  return (
    <div className="knowledge-chat-main">
      <div className="knowledge-messages">
        {messagesLoading ? (
          <p className="knowledge-empty">加载消息…</p>
        ) : messages.length === 0 && !streamingMessage ? (
          <div className="knowledge-empty">
            <BookOpen size={28} strokeWidth={1.5} />
            <p>向知识库提问，可点选示例：</p>
            <div className="knowledge-example-chips">
              {EXAMPLE_PROMPTS.map((prompt) => (
                <button
                  key={prompt}
                  type="button"
                  className="knowledge-example-chip"
                  onClick={() => onDraftChange(prompt)}
                  disabled={asking}
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <MessageList
            messages={messages}
            streamingMessage={streamingMessage}
            navigate={navigate}
            showNotice={showNotice}
            promptAction={promptAction}
          />
        )}
        <div ref={messagesEndRef} />
      </div>
      <footer className="knowledge-composer">
        <div className="knowledge-composer-shell">
          <textarea
            rows={2}
            placeholder="输入问题，Enter 发送（Shift+Enter 换行）"
            value={draft}
            onChange={(event) => onDraftChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                onSend();
              }
            }}
            disabled={asking}
          />
          <button
            type="button"
            className="primary-button knowledge-send"
            onClick={onSend}
            disabled={asking || !draft.trim()}
          >
            {asking ? "回答中…" : "发送"}
          </button>
        </div>
      </footer>
    </div>
  );
}

function MessageList({
  messages,
  streamingMessage,
  navigate,
  showNotice,
  promptAction,
}: {
  messages: KnowledgeChatMessage[];
  streamingMessage: KnowledgeChatMessage | null;
  navigate: (path: string) => void;
  showNotice?: (title: string, message?: string) => void;
  promptAction?: KnowledgePageProps["promptAction"];
}) {
  return (
    <>
      {messages.map((message) => (
        <MessageBubble
          key={message.id}
          message={message}
          navigate={navigate}
          showNotice={showNotice}
          promptAction={promptAction}
        />
      ))}
      {streamingMessage ? (
        <MessageBubble
          message={streamingMessage}
          navigate={navigate}
          streaming
          showNotice={showNotice}
          promptAction={promptAction}
        />
      ) : null}
    </>
  );
}
