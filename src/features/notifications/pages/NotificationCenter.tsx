import { useEffect, useRef, useState } from "react";
import {
  AlertTriangle,
  ArrowRight,
  Bell,
  CheckCircle2,
  Mail,
  MailOpen,
  RefreshCcw,
  X,
} from "lucide-react";
import {
  listNotifications,
  loadNotificationSummary,
  readAllNotifications,
  setNotificationRead,
} from "../api";
import type { NotificationData } from "../api";
import {
  getHotNewsArticle,
  withDocumentIdQuery,
} from "../../hotnews/api";
import { loadJob } from "../../../shared/api/jobs";
import { unwrapApiResult } from "../../../shared/api/http";
import { formatNotificationRelativeTime } from "../../../shared/format/time";
import { usePolling } from "../../../shared/hooks/usePolling";
import {
  resolveCourseThemePathKey,
  resolveHotNewsThemeSlug,
} from "../../../shared/theme/themeSlug";
import { toUserFailureMessage } from "../../../shared/job-failure-message";

type Props = {
  onNavigate: (path: string) => void;
  onRetry: (jobId: string) => Promise<void>;
  confirmRetry: (title: string, message: string) => Promise<boolean>;
};

/** 从 metadata 读取 jobType。 */
function resolveJobType(metadata: Record<string, unknown> | undefined): string {
  return metadata?.jobType != null ? String(metadata.jobType).trim() : "";
}

/** 是否为热点文章相关通知（成文/改写/审核；旧数据看 type 或 articleId）。 */
function isHotNewsArticleNotification(item: Pick<NotificationData, "type" | "metadata">) {
  if (
    item.type === "hot_news_generation_succeeded" ||
    item.type === "hot_news_generation_failed"
  ) {
    return true;
  }
  const jobType = resolveJobType(item.metadata);
  if (
    jobType === "hot_news_article_generation" ||
    jobType === "hot_news_article_rewrite" ||
    jobType === "hot_article_review" ||
    jobType === "hot_article_review_repair" ||
    jobType === "hot_article_review_revalidate"
  ) {
    return true;
  }
  return (
    item.metadata?.mode === "hot_news" &&
    item.metadata?.articleId != null &&
    String(item.metadata.articleId).trim() !== ""
  );
}

/** 解析热点文章跳转用的 articleId（metadata 优先；成文类才回退 courseId）。 */
function resolveHotNewsArticleId(item: NotificationData): string {
  const fromMeta =
    item.metadata?.articleId != null ? String(item.metadata.articleId).trim() : "";
  if (fromMeta) return fromMeta;
  if (
    item.type === "hot_news_generation_succeeded" ||
    item.type === "hot_news_generation_failed"
  ) {
    return (item.courseId || "").trim();
  }
  return "";
}

/** 从通知 metadata 读取公众号 documentId（同步，可能为空）。 */
function resolveHotNewsDocumentIdFromMeta(item: NotificationData): string {
  return item.metadata?.documentId != null
    ? String(item.metadata.documentId).trim()
    : "";
}

/**
 * 解析热点跳转用 documentId：metadata → job.input 投影 → 空。
 * 旧通知缺 documentId 时用 jobId 回查。
 */
async function resolveHotNewsDocumentId(
  item: NotificationData,
): Promise<string | null> {
  const fromMeta = resolveHotNewsDocumentIdFromMeta(item);
  if (fromMeta) {
    return fromMeta;
  }
  if (!item.jobId) {
    return null;
  }
  try {
    const job = await loadJob(item.jobId);
    return job.documentId || null;
  } catch {
    return null;
  }
}

/** 旧通知缺 theme 元数据时，按课程反查所属主题。 */
async function resolveThemePathKeyForCourse(
  courseId: string,
  metadata: Record<string, unknown>,
): Promise<string> {
  const fromMeta = resolveCourseThemePathKey(metadata);
  if (
    fromMeta !== "ai-self-study-zero" ||
    metadata.themeCode != null ||
    metadata.themeId != null
  ) {
    return fromMeta;
  }
  try {
    const response = await fetch(`/api/courses/${encodeURIComponent(courseId)}`, {
      cache: "no-store",
    });
    const course = await unwrapApiResult<{ themeId?: string | null }>(response);
    if (course.themeId) {
      return course.themeId;
    }
  } catch {
    /* keep fallback */
  }
  return fromMeta;
}

export function NotificationCenter({
  onNavigate,
  onRetry,
  confirmRetry,
}: Props) {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState<"all" | "unread">("all");
  const [items, setItems] = useState<NotificationData[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [toast, setToast] = useState<NotificationData | null>(null);
  const [detail, setDetail] = useState<NotificationData | null>(null);
  const [retryingId, setRetryingId] = useState<string | null>(null);
  const knownLatest = useRef<string | null>(null);
  const initialized = useRef(false);
  const closeRef = useRef<HTMLButtonElement>(null);

  async function refreshList(nextFilter = filter) {
    setLoading(true);
    setError("");
    try {
      const result = await listNotifications(nextFilter);
      setItems(result.items);
      setUnread(result.unreadCount);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "通知加载失败");
    } finally {
      setLoading(false);
    }
  }

  usePolling(
    async () => {
      try {
        const summary = await loadNotificationSummary();
        setUnread(summary.unreadCount);
        if (
          initialized.current &&
          summary.latestCreatedAt &&
          knownLatest.current &&
          summary.latestCreatedAt !== knownLatest.current
        ) {
          const latest = await listNotifications("all");
          const newest = latest.items[0];
          if (newest?.createdAt === summary.latestCreatedAt) setToast(newest);
          if (open) {
            setItems(
              filter === "all"
                ? latest.items
                : latest.items.filter((item) => item.status === "unread"),
            );
          }
        }
        knownLatest.current = summary.latestCreatedAt;
        initialized.current = true;
      } catch {
        /* The bell remains usable and retries on the next interval. */
      }
    },
    30_000,
    [filter, open],
    { listenFocus: true },
  );

  useEffect(() => {
    if (open) {
      void refreshList();
      window.setTimeout(() => closeRef.current?.focus(), 0);
    }
  }, [open, filter]);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(
      () => setToast(null),
      toast.level === "success" ? 4_000 : 8_000,
    );
    return () => window.clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", key);
    return () => window.removeEventListener("keydown", key);
  }, []);

  async function mark(item: NotificationData, read: boolean) {
    const updated = await setNotificationRead(item.id, read);
    setItems((current) =>
      filter === "unread" && read
        ? current.filter((entry) => entry.id !== item.id)
        : current.map((entry) => (entry.id === item.id ? updated : entry)),
    );
    setUnread((count) => Math.max(0, count + (read ? -1 : 1)));
  }

  async function activate(item: NotificationData) {
    if (item.status === "unread") await mark(item, true);
    setOpen(false);
    const jobType = resolveJobType(item.metadata);
    const topicId =
      item.metadata?.topicId != null ? String(item.metadata.topicId).trim() : "";
    const themeSlug = resolveHotNewsThemeSlug(item.metadata);

    // 雷达采集 → 主题热点雷达页（不进采集轨迹）
    if (jobType === "hot_news_fetch") {
      if (themeSlug) {
        onNavigate(
          `/modes/hot-news/themes/${encodeURIComponent(themeSlug)}/radar`,
        );
        return;
      }
      onNavigate("/modes/hot-news");
      return;
    }
    // 选题研究 / 资料池视频解析 → 研究页
    if (
      jobType === "hot_news_topic_research" ||
      jobType === "hot_news_video_parse"
    ) {
      if (topicId) {
        onNavigate(
          `/modes/hot-news/topics/${encodeURIComponent(topicId)}/research`,
        );
        return;
      }
    }
    // 信源视频解析 → 主题雷达
    if (jobType === "hot_news_source_video_parse" && themeSlug) {
      onNavigate(
        `/modes/hot-news/themes/${encodeURIComponent(themeSlug)}/radar`,
      );
      return;
    }
    // 课表生成 → 主题向导
    if (jobType === "theme_curriculum_generation") {
      const themeKey = resolveCourseThemePathKey(item.metadata);
      onNavigate(
        `/modes/course-system/themes/${encodeURIComponent(themeKey)}/wizard`,
      );
      return;
    }

    if (isHotNewsArticleNotification(item)) {
      const articleId = resolveHotNewsArticleId(item);
      if (!articleId) {
        /* fall through */
      } else if (item.level === "success") {
        const documentId = await resolveHotNewsDocumentId(item);
        let resolvedTopicId = topicId;
        // 旧通知可能缺 topicId，回查文章后再进工作区
        if (!resolvedTopicId) {
          try {
            const article = await getHotNewsArticle(articleId, documentId);
            resolvedTopicId = (article.topicId || "").trim();
          } catch {
            /* keep empty */
          }
        }
        if (resolvedTopicId) {
          onNavigate(
            withDocumentIdQuery(
              `/modes/hot-news/topics/${encodeURIComponent(resolvedTopicId)}/articles/${encodeURIComponent(articleId)}`,
              documentId,
            ),
          );
          return;
        }
        onNavigate(
          withDocumentIdQuery(
            `/modes/hot-news/articles/${encodeURIComponent(articleId)}/execution`,
            documentId,
          ),
        );
        return;
      } else {
        const documentId = await resolveHotNewsDocumentId(item);
        onNavigate(
          withDocumentIdQuery(
            `/modes/hot-news/articles/${encodeURIComponent(articleId)}/execution${
              item.jobId ? `?job=${encodeURIComponent(item.jobId)}` : ""
            }`,
            documentId,
          ),
        );
        return;
      }
    }
    // 课程全文成功 → 课程页（草稿/公众号等同 courseId 且成功时也可进）
    if (
      item.level === "success" &&
      item.courseId &&
      (item.type === "course_generation_succeeded" ||
        jobType === "course_content_generation" ||
        jobType === "course_draft_generation" ||
        jobType === "wechat_article_generation")
    ) {
      const themeKey = await resolveThemePathKeyForCourse(
        item.courseId,
        item.metadata || {},
      );
      onNavigate(
        `/modes/course-system/themes/${encodeURIComponent(themeKey)}/courses/${encodeURIComponent(item.courseId)}`,
      );
      return;
    }
    if (item.jobId) {
      onNavigate(
        `/executions?job=${encodeURIComponent(item.jobId)}${item.courseId ? `&course=${encodeURIComponent(item.courseId)}` : ""}`,
      );
    }
  }

  async function retry(item: NotificationData) {
    if (!item.jobId || retryingId) return;
    if (
      !(await confirmRetry(
        "重新执行任务？",
        `将重新执行《${item.courseTitle || item.title}》的失败步骤。`,
      ))
    )
      return;
    setRetryingId(item.id);
    setError("");
    try {
      await onRetry(item.jobId);
      try {
        if (item.status === "unread") await mark(item, true);
      } catch {
        /* Retry already succeeded; read state can be corrected on next refresh. */
      }
      setDetail(null);
      setOpen(false);
      if (isHotNewsArticleNotification(item)) {
        const articleId = resolveHotNewsArticleId(item);
        if (articleId) {
          const documentId = await resolveHotNewsDocumentId(item);
          onNavigate(
            withDocumentIdQuery(
              `/modes/hot-news/articles/${encodeURIComponent(articleId)}/execution${
                item.jobId ? `?job=${encodeURIComponent(item.jobId)}` : ""
              }`,
              documentId,
            ),
          );
        }
      }
    } catch (reason) {
      setError(
        reason instanceof Error
          ? `重新执行失败：${reason.message}`
          : "重新执行失败，请稍后重试",
      );
    } finally {
      setRetryingId(null);
    }
  }

  async function openDetail(item: NotificationData) {
    setDetail(item);
    if (item.status === "unread") {
      try {
        await mark(item, true);
      } catch {
        /* Details remain available if the read-state request is unavailable. */
      }
    }
  }

  return (
    <>
      <button
        className="notification-bell"
        aria-label={`消息通知，${unread} 条未读`}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Bell size={19} />
        {unread > 0 && (
          <span className="notification-badge">
            {unread > 99 ? "99+" : unread}
          </span>
        )}
      </button>
      {open && (
        <div
          className="notification-backdrop"
          onMouseDown={() => setOpen(false)}
        >
          <aside
            className="notification-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="notification-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header className="notification-header">
              <div>
                <h2 id="notification-title">消息通知</h2>
                <span>{unread} 条未读</span>
              </div>
              <button
                ref={closeRef}
                className="notification-close"
                aria-label="关闭通知"
                onClick={() => setOpen(false)}
              >
                <X size={18} />
              </button>
            </header>
            <div className="notification-tools">
              <div className="notification-tabs">
                <button
                  className={filter === "all" ? "active" : ""}
                  onClick={() => setFilter("all")}
                >
                  全部
                </button>
                <button
                  className={filter === "unread" ? "active" : ""}
                  onClick={() => setFilter("unread")}
                >
                  未读
                </button>
              </div>
              <button
                className="notification-read-all"
                disabled={!unread}
                onClick={async () => {
                  const result = await readAllNotifications();
                  setUnread(result.unreadCount);
                  setItems((current) =>
                    filter === "unread"
                      ? []
                      : current.map((item) => ({ ...item, status: "read" })),
                  );
                }}
              >
                全部标为已读
              </button>
            </div>
            <div className="notification-list">
              {loading && !items.length ? (
                <NotificationSkeleton />
              ) : error && !items.length ? (
                <div className="notification-empty">
                  <AlertTriangle size={24} />
                  <p>{error}</p>
                  <button onClick={() => void refreshList()}>
                    <RefreshCcw size={15} />
                    重试
                  </button>
                </div>
              ) : !items.length ? (
                <div className="notification-empty">
                  <Bell size={25} />
                  <p>{filter === "unread" ? "没有未读消息" : "暂无消息"}</p>
                </div>
              ) : (
                <>
                  {error && (
                    <div className="notification-inline-error" role="alert">
                      <AlertTriangle size={14} />
                      <span>{error}</span>
                      <button
                        onClick={() => setError("")}
                        aria-label="关闭错误提示"
                      >
                        <X size={14} />
                      </button>
                    </div>
                  )}
                  {items.map((item) => (
                    <article
                      key={item.id}
                      className={`notification-item ${item.status}`}
                    >
                      <button
                        className="notification-body"
                        onClick={() => void activate(item)}
                      >
                        <span className={`notification-status ${item.level}`}>
                          {item.level === "success" ? (
                            <CheckCircle2 size={19} />
                          ) : (
                            <AlertTriangle size={19} />
                          )}
                        </span>
                        <span className="notification-copy">
                          <strong>{item.title}</strong>
                          <span
                            className={
                              item.level === "error" ? "error-copy" : ""
                            }
                          >
                            {item.level === "error"
                              ? toUserFailureMessage(item.message)
                              : item.message}
                          </span>
                          <small>
                            {item.seriesName} · {formatNotificationRelativeTime(item.createdAt)}
                          </small>
                        </span>
                      </button>
                      <div className="notification-actions">
                        <button
                          onClick={() =>
                            item.level === "error"
                              ? void openDetail(item)
                              : void activate(item)
                          }
                        >
                          {item.level === "success" ? "查看结果" : "查看原因"}
                          <ArrowRight size={13} />
                        </button>
                        {item.level === "error" && (
                          <button
                            className="retry"
                            disabled={retryingId === item.id}
                            onClick={() => void retry(item)}
                          >
                            <RefreshCcw size={13} />
                            {retryingId === item.id ? "执行中" : "重新执行"}
                          </button>
                        )}
                        <button
                          className="notification-read-toggle"
                          title={
                            item.status === "unread" ? "标为已读" : "标为未读"
                          }
                          aria-label={
                            item.status === "unread" ? "标为已读" : "标为未读"
                          }
                          onClick={() =>
                            void mark(item, item.status === "unread")
                          }
                        >
                          {item.status === "unread" ? (
                            <MailOpen size={15} />
                          ) : (
                            <Mail size={15} />
                          )}
                        </button>
                      </div>
                    </article>
                  ))}
                </>
              )}
            </div>
          </aside>
        </div>
      )}
      {detail && (
        <div
          className="notification-detail-backdrop"
          role="presentation"
          onMouseDown={() => setDetail(null)}
        >
          <section
            className="notification-detail"
            role="dialog"
            aria-modal="true"
            aria-labelledby="notification-detail-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <header>
              <div>
                <span className={`notification-detail-icon ${detail.level}`}>
                  <AlertTriangle size={17} />
                </span>
                <div>
                  <h3 id="notification-detail-title">{detail.title}</h3>
                  <small>
                    {formatNotificationRelativeTime(detail.createdAt)} ·{" "}
                    {detail.seriesName || "任务通知"}
                  </small>
                </div>
              </div>
              <button aria-label="关闭详情" onClick={() => setDetail(null)}>
                <X size={17} />
              </button>
            </header>
            <div className="notification-detail-content">
              <strong>失败原因</strong>
              <p>
                {toUserFailureMessage(
                  detail.message || "任务执行失败，暂无更多错误信息。",
                )}
              </p>
              <dl>
                <div>
                  <dt>任务</dt>
                  <dd>{detail.courseTitle || "未命名任务"}</dd>
                </div>
              </dl>
            </div>
            <footer>
              <button
                className="secondary"
                onClick={() => {
                  setDetail(null);
                  if (isHotNewsArticleNotification(detail)) {
                    const articleId = resolveHotNewsArticleId(detail);
                    if (articleId) {
                      void resolveHotNewsDocumentId(detail).then((documentId) => {
                        onNavigate(
                          withDocumentIdQuery(
                            `/modes/hot-news/articles/${encodeURIComponent(articleId)}/execution${
                              detail.jobId
                                ? `?job=${encodeURIComponent(detail.jobId)}`
                                : ""
                            }`,
                            documentId,
                          ),
                        );
                      });
                      return;
                    }
                  }
                  if (detail.jobId) {
                    onNavigate(
                      `/executions?job=${encodeURIComponent(detail.jobId)}${detail.courseId ? `&course=${encodeURIComponent(detail.courseId)}` : ""}`,
                    );
                  }
                }}
              >
                进入执行详情
              </button>
              {detail.jobId && (
                <button
                  className="retry"
                  disabled={retryingId === detail.id}
                  onClick={() => void retry(detail)}
                >
                  <RefreshCcw size={14} />
                  重新执行
                </button>
              )}
            </footer>
          </section>
        </div>
      )}
      {toast && (
        <div className={`notification-toast ${toast.level}`} role="status">
          <span>
            {toast.level === "success" ? (
              <CheckCircle2 size={19} />
            ) : (
              <AlertTriangle size={19} />
            )}
          </span>
          <div>
            <strong>{toast.title}</strong>
            <p>{toast.message}</p>
            <button
              onClick={() => {
                if (toast.level === "error") void openDetail(toast);
                else void activate(toast);
                setToast(null);
              }}
            >
              {toast.level === "success" ? "查看结果" : "查看原因"}
            </button>
          </div>
          <button aria-label="关闭提示" onClick={() => setToast(null)}>
            <X size={16} />
          </button>
        </div>
      )}
    </>
  );
}

function NotificationSkeleton() {
  return (
    <div className="notification-skeleton">
      {[1, 2, 3].map((item) => (
        <span key={item} />
      ))}
    </div>
  );
}
