import { useCallback, useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { BookOpen, Loader2, Plus, RefreshCcw } from "lucide-react";
import {
  askKnowledgeChat,
  createSession,
  listMessages,
  listSessions,
  loadKnowledgeStats,
  reindexKnowledge,
  type KnowledgeChatMessage,
  type KnowledgeChatSession,
  type KnowledgeCitation,
  type KnowledgeIndexStats,
} from "../api";

export type KnowledgePageProps = {
  navigate: (path: string) => void;
  showNotice?: (title: string, message?: string) => void;
};

type MainTab = "chat" | "library";

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
    manual: "手动",
    MANUAL: "手动",
  };
  return map[kind] ?? kind;
}

function CitationCard({
  citation,
  onNavigate,
}: {
  citation: KnowledgeCitation;
  onNavigate: (path: string) => void;
}) {
  const clickable = Boolean(citation.deepLink?.trim());
  return (
    <button
      type="button"
      className={`knowledge-citation${clickable ? " is-link" : ""}`}
      disabled={!clickable}
      onClick={() => {
        if (citation.deepLink?.trim()) {
          onNavigate(citation.deepLink.trim());
        }
      }}
    >
      <div className="knowledge-citation-head">
        <strong>{citation.title || "未命名文档"}</strong>
        <span className="knowledge-badges">
          <span className="knowledge-badge">{sourceKindLabel(citation.sourceKind)}</span>
          {citation.mediaKind ? (
            <span className="knowledge-badge muted">{citation.mediaKind}</span>
          ) : null}
        </span>
      </div>
      {citation.headingPath ? (
        <small className="knowledge-citation-path">{citation.headingPath}</small>
      ) : null}
      <p>{citation.snippet}</p>
    </button>
  );
}

function MessageBubble({
  message,
  navigate,
  streaming,
}: {
  message: KnowledgeChatMessage;
  navigate: (path: string) => void;
  streaming?: boolean;
}) {
  const assistant = roleIsAssistant(message.role);
  const citations = message.citations ?? [];
  return (
    <article
      className={`knowledge-message${assistant ? " is-assistant" : " is-user"}`}
    >
      <div className="knowledge-message-meta">
        <span>{assistant ? "助手" : "你"}</span>
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
            <ReactMarkdown remarkPlugins={[remarkGfm]}>
              {message.content || (streaming ? "…" : "")}
            </ReactMarkdown>
          </div>
        ) : (
          <p>{message.content}</p>
        )}
      </div>
      {assistant && citations.length > 0 ? (
        <div className="knowledge-citations">
          <span className="panel-label">引用来源</span>
          {citations.map((citation, index) => (
            <CitationCard
              key={`${citation.documentId}-${index}`}
              citation={citation}
              onNavigate={navigate}
            />
          ))}
        </div>
      ) : null}
    </article>
  );
}

export function KnowledgePage({ navigate, showNotice }: KnowledgePageProps) {
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
        `任务 ${job.id.slice(0, 8)}… 已入队，可在执行轨迹查看进度。`,
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

      const assistantMessage: KnowledgeChatMessage = result.message ?? {
        id: `assistant-${Date.now()}`,
        role: "assistant",
        content: result.content,
        citations: result.citations,
        createdAt: new Date().toISOString(),
      };

      setStreamingMessage(null);
      setMessages((current) => [...current, assistantMessage]);
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
            基于已索引作品内容检索并回答问题。索引就绪后可对话；引用卡片可跳转原文。
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
            className="trajectory-button"
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
      </nav>

      {mainTab === "library" ? (
        <LibraryPlaceholder />
      ) : (
        <div className="hot-execution-layout knowledge-chat-layout">
          <aside className="hot-execution-history knowledge-session-list">
            <div className="execution-panel-title knowledge-session-head">
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
              <p className="knowledge-empty">加载中…</p>
            ) : sessions.length === 0 ? (
              <p className="knowledge-empty">暂无会话，点击「新对话」开始。</p>
            ) : (
              <div className="knowledge-session-items">
                {sessions.map((session) => (
                  <button
                    key={session.id}
                    type="button"
                    className={
                      session.id === activeSessionId
                        ? "hot-execution-record knowledge-session active"
                        : "hot-execution-record knowledge-session"
                    }
                    onClick={() => setActiveSessionId(session.id)}
                  >
                    <strong>{session.title || "未命名对话"}</strong>
                    <small>{formatSessionTime(session.updatedAt)}</small>
                  </button>
                ))}
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
  return (
    <div className="knowledge-stats panel">
      <BookOpen size={16} />
      <div>
        <strong>{loading ? "—" : `${ready} / ${total}`}</strong>
        <small>
          可检索 / 文档总数{failed > 0 ? ` · 失败 ${failed}` : ""}
        </small>
      </div>
    </div>
  );
}

function LibraryPlaceholder() {
  return (
    <article className="panel knowledge-library-placeholder">
      <span className="panel-label">资料库</span>
      <h3>二期：手动上传与素材入库</h3>
      <p>
        当前版本仅支持从已发布作品与工作流产出自动入索引；上传与素材管理将在后续版本开放。
      </p>
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
}) {
  return (
    <div className="hot-execution-detail knowledge-chat-main">
      {messagesLoading ? (
        <p className="knowledge-empty">加载消息…</p>
      ) : messages.length === 0 && !streamingMessage ? (
        <p className="knowledge-empty">
          向知识库提问，例如：「某主题下写过哪些引流方法？」
        </p>
      ) : (
        <MessageList
          messages={messages}
          streamingMessage={streamingMessage}
          navigate={navigate}
        />
      )}
      <div ref={messagesEndRef} />
      <footer className="knowledge-composer">
        <textarea
          rows={3}
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
          className="save-settings"
          onClick={onSend}
          disabled={asking || !draft.trim()}
        >
          {asking ? "回答中…" : "发送"}
        </button>
      </footer>
    </div>
  );
}

function MessageList({
  messages,
  streamingMessage,
  navigate,
}: {
  messages: KnowledgeChatMessage[];
  streamingMessage: KnowledgeChatMessage | null;
  navigate: (path: string) => void;
}) {
  return (
    <>
      {messages.map((message) => (
        <MessageBubble
          key={message.id}
          message={message}
          navigate={navigate}
        />
      ))}
      {streamingMessage ? (
        <MessageBubble
          message={streamingMessage}
          navigate={navigate}
          streaming
        />
      ) : null}
    </>
  );
}
