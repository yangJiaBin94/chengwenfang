import { unwrapApiResult } from "../../shared/api/http";

export type NotificationData = {
  id: string;
  type:
    | "course_generation_succeeded"
    | "course_generation_failed"
    | "hot_news_generation_succeeded"
    | "hot_news_generation_failed"
    | "job_terminal_succeeded"
    | "job_terminal_failed";
  level: "success" | "error";
  title: string;
  message: string;
  courseId: string | null;
  jobId: string | null;
  workflowRunId: string | null;
  seriesName: string;
  courseTitle: string;
  lessonNo: number;
  status: "unread" | "read";
  metadata: Record<string, unknown>;
  createdAt: string;
  readAt: string | null;
};

export type NotificationSummary = {
  unreadCount: number;
  latestCreatedAt: string | null;
};

async function payload<T>(response: Response): Promise<T> {
  return unwrapApiResult<T>(response);
}

export async function listNotifications(status: "all" | "unread") {
  return payload<{
    items: NotificationData[];
    nextCursor: string | null;
    unreadCount: number;
  }>(
    await fetch(`/api/notifications?status=${status}&limit=30`, {
      cache: "no-store",
    }),
  );
}

export async function loadNotificationSummary() {
  return payload<NotificationSummary>(
    await fetch("/api/notifications/summary", { cache: "no-store" }),
  );
}

export async function setNotificationRead(id: string, read: boolean) {
  return payload<NotificationData>(
    await fetch(`/api/notifications/${encodeURIComponent(id)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ read }),
    }),
  );
}

export async function readAllNotifications() {
  return payload<NotificationSummary>(
    await fetch("/api/notifications/read-all", { method: "POST" }),
  );
}
