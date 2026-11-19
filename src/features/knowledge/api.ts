import { unwrapApiResult } from "../../shared/api/http";
import type { JobData } from "../../shared/api/jobs";

/** 索引按来源计数。 */
export type KnowledgeSourceCount = {
  sourceKind: string;
  count: number;
};

/** 知识库索引统计。 */
export type KnowledgeIndexStats = {
  total: number;
  ready: number;
  failed: number;
  indexing?: number;
  pending?: number;
  bySource?: KnowledgeSourceCount[];
  bySourceKind?: KnowledgeSourceCount[];
};

/** 对话会话。 */
export type KnowledgeChatSession = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};

/** RAG 引用片段。 */
export type KnowledgeCitation = {
  documentId: string;
  chunkId?: string;
  sourceKind: string;
  mediaKind: string;
  title: string;
  snippet: string;
  deepLink?: string | null;
  headingPath?: string | null;
  charSpanStart?: number | null;
  charSpanEnd?: number | null;
  score?: number | null;
};

/** 工具步骤信封。 */
export type KnowledgeToolStep = {
  name: string;
  args?: string;
  resultSummary?: string;
  latencyMs?: number;
  ok?: boolean;
};

/** 对话消息。 */
export type KnowledgeChatMessage = {
  id: string;
  role: string;
  content: string;
  citations?: KnowledgeCitation[] | null;
  createdAt: string;
  abstained?: boolean;
  mode?: string;
  rounds?: number;
  degradedReasons?: string[];
  abstainReason?: string;
  toolSteps?: KnowledgeToolStep[];
};

/** SSE 问答过程中的增量回调。 */
export type KnowledgeAskStreamHandlers = {
  onCitations?: (citations: KnowledgeCitation[]) => void;
  onAnswerChunk?: (content: string) => void;
  onDone?: () => void;
};

/** 问答完成后的汇总结果。 */
export type KnowledgeAskResult = {
  citations: KnowledgeCitation[];
  content: string;
  message?: KnowledgeChatMessage;
  abstained?: boolean;
  mode?: string;
  rounds?: number;
  degradedReasons?: string[];
  abstainReason?: string;
};

/** 长期记忆条目。 */
export type KnowledgeMemory = {
  id: string;
  content: string;
  provenance?: string | null;
  paused?: boolean;
  expiresAt?: string | null;
  createdAt?: string;
};

function normalizeSourceCounts(
  stats: KnowledgeIndexStats,
): KnowledgeSourceCount[] {
  return stats.bySourceKind ?? stats.bySource ?? [];
}

/** 加载知识库索引统计。 */
export async function loadKnowledgeStats(): Promise<KnowledgeIndexStats> {
  const data = await unwrapApiResult<KnowledgeIndexStats>(
    await fetch("/api/knowledge/stats", { cache: "no-store" }),
  );
  return { ...data, bySource: normalizeSourceCounts(data) };
}

/** 触发全量或按来源重建索引。 */
export async function reindexKnowledge(
  sourceKind?: string,
): Promise<JobData> {
  const body =
    sourceKind && sourceKind.trim()
      ? JSON.stringify({ sourceKind: sourceKind.trim() })
      : "{}";
  return unwrapApiResult<JobData>(
    await fetch("/api/knowledge/reindex", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
    }),
  );
}

/** 知识库文档。 */
export type KnowledgeDocument = {
  id: string;
  sourceKind: string;
  sourceRefId: string;
  mediaKind: string;
  title: string;
  themeId?: string | null;
  status: string;
  chunkCount?: number | null;
  contentHash?: string | null;
  sourceVersionStamp?: string | null;
  deepLink?: string | null;
  originFileId?: string | null;
  textFileId?: string | null;
  errorMessage?: string | null;
  createdAt?: string;
  updatedAt?: string;
};

/** 知识库文档分页结果。 */
export type KnowledgeDocumentPage = {
  items: KnowledgeDocument[];
  total: number;
  page: number;
  pageSize: number;
};

/** 知识库文档分页查询参数。 */
export type KnowledgeDocumentListParams = {
  page?: number;
  pageSize?: number;
  sourceKind?: string;
  keyword?: string;
  mediaKind?: string;
  createdFrom?: string;
  createdTo?: string;
};

/** 分页列出知识库文档。 */
export async function listKnowledgeDocuments(
  params: KnowledgeDocumentListParams = {},
): Promise<KnowledgeDocumentPage> {
  const search = new URLSearchParams();
  search.set("page", String(params.page ?? 1));
  search.set("pageSize", String(params.pageSize ?? 10));
  const sourceKind = params.sourceKind?.trim();
  if (sourceKind) search.set("sourceKind", sourceKind);
  const keyword = params.keyword?.trim();
  if (keyword) search.set("keyword", keyword);
  const mediaKind = params.mediaKind?.trim();
  if (mediaKind) search.set("mediaKind", mediaKind);
  const createdFrom = params.createdFrom?.trim();
  if (createdFrom) search.set("createdFrom", createdFrom);
  const createdTo = params.createdTo?.trim();
  if (createdTo) search.set("createdTo", createdTo);
  return unwrapApiResult<KnowledgeDocumentPage>(
    await fetch(`/api/knowledge/documents?${search.toString()}`, {
      cache: "no-store",
    }),
  );
}

/** 上传资料文件入库。 */
export async function uploadKnowledgeDocument(
  file: File,
): Promise<KnowledgeDocument> {
  const form = new FormData();
  form.append("file", file);
  return unwrapApiResult<KnowledgeDocument>(
    await fetch("/api/knowledge/documents/upload", {
      method: "POST",
      body: form,
    }),
  );
}

/** 网页 URL 入库。 */
export async function ingestKnowledgeWebPage(
  url: string,
  title?: string,
): Promise<KnowledgeDocument> {
  return unwrapApiResult<KnowledgeDocument>(
    await fetch("/api/knowledge/documents/ingest-url", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        url: url.trim(),
        title: title?.trim() || undefined,
      }),
    }),
  );
}

/** 删除知识库文档。 */
export async function deleteKnowledgeDocument(
  documentId: string,
): Promise<void> {
  await unwrapApiResult<null>(
    await fetch(
      `/api/knowledge/documents/${encodeURIComponent(documentId)}`,
      { method: "DELETE" },
    ),
  );
}

/** 单文档重建索引。 */
export async function reindexKnowledgeDocument(
  documentId: string,
): Promise<JobData> {
  return unwrapApiResult<JobData>(
    await fetch(
      `/api/knowledge/documents/${encodeURIComponent(documentId)}/reindex`,
      { method: "POST" },
    ),
  );
}

/** 列出对话会话。 */
export async function listSessions(): Promise<KnowledgeChatSession[]> {
  return unwrapApiResult<KnowledgeChatSession[]>(
    await fetch("/api/knowledge/chats", { cache: "no-store" }),
  );
}

/** 创建新对话会话。 */
export async function createSession(
  title?: string,
): Promise<KnowledgeChatSession> {
  const body = title?.trim() ? JSON.stringify({ title: title.trim() }) : "{}";
  return unwrapApiResult<KnowledgeChatSession>(
    await fetch("/api/knowledge/chats", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
    }),
  );
}

/** 重命名对话会话。 */
export async function renameSession(
  sessionId: string,
  title: string,
): Promise<KnowledgeChatSession> {
  return unwrapApiResult<KnowledgeChatSession>(
    await fetch(`/api/knowledge/chats/${encodeURIComponent(sessionId)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ title }),
    }),
  );
}

/** 删除对话会话及其消息。 */
export async function deleteSession(sessionId: string): Promise<void> {
  await unwrapApiResult<null>(
    await fetch(`/api/knowledge/chats/${encodeURIComponent(sessionId)}`, {
      method: "DELETE",
    }),
  );
}

/** 列出会话消息历史。 */
export async function listMessages(
  sessionId: string,
): Promise<KnowledgeChatMessage[]> {
  return unwrapApiResult<KnowledgeChatMessage[]>(
    await fetch(
      `/api/knowledge/chats/${encodeURIComponent(sessionId)}/messages`,
      { cache: "no-store" },
    ),
  );
}

/**
 * 解析单个 SSE 事件块。
 *
 * @param block 以空行分隔的事件文本
 */
function parseSseBlock(
  block: string,
): { event: string; data: string } | null {
  const trimmed = block.trim();
  if (!trimmed) return null;
  let event = "message";
  const dataLines: string[] = [];
  for (const line of trimmed.split("\n")) {
    if (line.startsWith("event:")) {
      event = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trimStart());
    }
  }
  if (!dataLines.length) return null;
  return { event, data: dataLines.join("\n") };
}

/**
 * 从 answer 事件的 data 解析助手正文。
 *
 * @param raw SSE data 字段
 */
function parseAnswerPayload(raw: string): {
  content: string;
  message?: KnowledgeChatMessage;
} {
  const parsed = JSON.parse(raw) as
    | string
    | KnowledgeChatMessage
    | { content?: string; message?: KnowledgeChatMessage };
  if (typeof parsed === "string") {
    return { content: parsed };
  }
  if ("content" in parsed && typeof parsed.content === "string") {
    return { content: parsed.content, message: parsed as KnowledgeChatMessage };
  }
  if ("message" in parsed && parsed.message?.content) {
    return {
      content: parsed.message.content,
      message: parsed.message,
    };
  }
  return { content: "" };
}

/**
 * 知识库问答：POST + SSE（citations → answer → done）。
 *
 * @param sessionId 会话 ID
 * @param content 用户问题
 * @param handlers 流式回调（可选）
 */
export async function askKnowledgeChat(
  sessionId: string,
  content: string,
  handlers?: KnowledgeAskStreamHandlers,
  options?: { sourceKinds?: string[] },
): Promise<KnowledgeAskResult> {
  const payload: { content: string; sourceKinds?: string[] } = {
    content: content.trim(),
  };
  if (options?.sourceKinds?.length) {
    payload.sourceKinds = options.sourceKinds;
  }
  const response = await fetch(
    `/api/knowledge/chats/${encodeURIComponent(sessionId)}/messages`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify(payload),
    },
  );

  if (!response.ok) {
    const message = await response.text().catch(() => "");
    throw new Error(
      message.trim() || `知识库问答失败：HTTP ${response.status}`,
    );
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error("浏览器不支持流式响应");
  }

  const decoder = new TextDecoder();
  let buffer = "";
  let citations: KnowledgeCitation[] = [];
  let contentText = "";
  let message: KnowledgeChatMessage | undefined;
  let abstained = false;
  let mode: string | undefined;
  let rounds: number | undefined;
  let degradedReasons: string[] = [];
  let abstainReason: string | undefined;
  let toolSteps: KnowledgeToolStep[] = [];

  const dispatchBlock = (block: string) => {
    const event = parseSseBlock(block);
    if (!event) return;
    if (event.event === "citations") {
      const parsed = JSON.parse(event.data) as KnowledgeCitation[];
      citations = Array.isArray(parsed) ? parsed : [];
      handlers?.onCitations?.(citations);
      return;
    }
    if (event.event === "meta") {
      const meta = JSON.parse(event.data) as {
        abstained?: boolean;
        mode?: string;
        rounds?: number;
        degradedReasons?: string[];
        abstainReason?: string;
        toolSteps?: KnowledgeToolStep[];
        message?: KnowledgeChatMessage;
      };
      abstained = Boolean(meta.abstained);
      mode = meta.mode;
      rounds = meta.rounds;
      degradedReasons = Array.isArray(meta.degradedReasons)
        ? meta.degradedReasons
        : [];
      abstainReason = meta.abstainReason;
      if (Array.isArray(meta.toolSteps)) {
        toolSteps = meta.toolSteps;
      } else if (Array.isArray(meta.message?.toolSteps)) {
        toolSteps = meta.message.toolSteps ?? [];
      }
      return;
    }
    if (event.event === "answer") {
      const answer = parseAnswerPayload(event.data);
      contentText = answer.content;
      const fromMessage = answer.message?.toolSteps;
      message = answer.message
        ? {
            ...answer.message,
            abstained,
            mode,
            rounds,
            degradedReasons,
            abstainReason,
            toolSteps: fromMessage?.length ? fromMessage : toolSteps,
          }
        : answer.message;
      handlers?.onAnswerChunk?.(contentText);
      return;
    }
    if (event.event === "done") {
      handlers?.onDone?.();
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    buffer = buffer.replace(/\r\n/g, "\n");
    let splitAt = buffer.indexOf("\n\n");
    while (splitAt >= 0) {
      const block = buffer.slice(0, splitAt);
      buffer = buffer.slice(splitAt + 2);
      dispatchBlock(block);
      splitAt = buffer.indexOf("\n\n");
    }
  }

  if (buffer.trim()) {
    dispatchBlock(buffer);
  }

  if (!contentText && !citations.length) {
    throw new Error("未收到知识库回答");
  }

  if (message && !message.toolSteps?.length && toolSteps.length) {
    message = { ...message, toolSteps };
  }

  return {
    citations,
    content: contentText,
    message,
    abstained,
    mode,
    rounds,
    degradedReasons,
    abstainReason,
  };
}

/** 提交答案赞踩。 */
export async function submitAnswerFeedback(
  messageId: string,
  rating: "up" | "down",
  note?: string,
): Promise<{ goldenCandidateJson?: string | null }> {
  return unwrapApiResult<{ goldenCandidateJson?: string | null }>(
    await fetch(
      `/api/knowledge/messages/${encodeURIComponent(messageId)}/feedback`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ rating, note }),
      },
    ),
  );
}

/** 列出长期记忆。 */
export async function listMemories(): Promise<KnowledgeMemory[]> {
  return unwrapApiResult<KnowledgeMemory[]>(
    await fetch("/api/knowledge/memories", { cache: "no-store" }),
  );
}

/** 删除长期记忆。 */
export async function deleteMemory(memoryId: string): Promise<void> {
  await unwrapApiResult<null>(
    await fetch(`/api/knowledge/memories/${encodeURIComponent(memoryId)}`, {
      method: "DELETE",
    }),
  );
}

/** 暂停或恢复长期记忆。 */
export async function setMemoryPaused(
  memoryId: string,
  paused: boolean,
): Promise<void> {
  await unwrapApiResult<null>(
    await fetch(`/api/knowledge/memories/${encodeURIComponent(memoryId)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ paused }),
    }),
  );
}
