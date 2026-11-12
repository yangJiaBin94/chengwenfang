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
  sourceKind: string;
  mediaKind: string;
  title: string;
  snippet: string;
  deepLink?: string | null;
  headingPath?: string | null;
};

/** 对话消息。 */
export type KnowledgeChatMessage = {
  id: string;
  role: string;
  content: string;
  citations?: KnowledgeCitation[] | null;
  createdAt: string;
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
): Promise<KnowledgeAskResult> {
  const response = await fetch(
    `/api/knowledge/chats/${encodeURIComponent(sessionId)}/messages`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify({ content: content.trim() }),
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

  const dispatchBlock = (block: string) => {
    const event = parseSseBlock(block);
    if (!event) return;
    if (event.event === "citations") {
      const parsed = JSON.parse(event.data) as KnowledgeCitation[];
      citations = Array.isArray(parsed) ? parsed : [];
      handlers?.onCitations?.(citations);
      return;
    }
    if (event.event === "answer") {
      const answer = parseAnswerPayload(event.data);
      contentText = answer.content;
      message = answer.message;
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

  return { citations, content: contentText, message };
}
