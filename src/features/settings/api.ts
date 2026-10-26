import type { AiProviderConfig, AiScene, AiSettings, AiSettingsKind } from "./model";
import { unwrapApiResult } from "../../shared/api/http";

/** 排版主题 CRUD（设置页使用；实现仍在课程内容 API）。 */
export {
  listLayoutThemes,
  createLayoutTheme,
  updateLayoutTheme,
  cloneLayoutTheme,
  setLayoutThemeStatus,
  deleteLayoutTheme,
  type LayoutThemeData,
} from "../course/api";

/* ---- AI settings ---- */

/** Internal request contract for the generic text endpoint. */
type TextRequest = {
  config: AiProviderConfig;
  scene: AiScene;
  systemPrompt: string;
  userPrompt: string;
};

/** 后端 AiSettingsView 字段（protocol 为下划线风格）。 */
type BackendProvider = {
  kind?: string;
  name?: string;
  model?: string;
  baseUrl?: string;
  protocol?: string;
  timeoutSeconds?: number;
  maxRetries?: number;
  temperature?: number | null;
  maxOutputTokens?: number | null;
  imageQuality?: string | null;
  enabled?: boolean;
  apiKeyConfigured?: boolean;
};

type BackendAiSettings = {
  text?: BackendProvider;
  image?: BackendProvider;
  vision?: BackendProvider;
};

type AiSettingsBundle = {
  configured: boolean;
  settings: AiSettings;
  updatedAt: string;
};

function toFeProtocol(
  raw: string | undefined,
  kind: AiSettingsKind,
): AiProviderConfig["protocol"] {
  if (kind === "image") return "openai-images";
  if (raw === "openai_responses" || raw === "openai-responses") return "openai-responses";
  return "openai-chat";
}

function toBackendProtocol(raw: string | undefined): string {
  if (raw === "openai-responses" || raw === "openai_responses") return "openai_responses";
  if (raw === "openai-images") return "openai_chat";
  return "openai_chat";
}

function defaultName(kind: AiSettingsKind): string {
  if (kind === "text") return "文本 AI";
  if (kind === "image") return "图片 AI";
  return "多模态 AI";
}

function mapProvider(raw: BackendProvider | undefined, kind: AiSettingsKind): AiProviderConfig {
  return {
    name: raw?.name || defaultName(kind),
    model: raw?.model || "",
    baseUrl: raw?.baseUrl || "",
    apiKey: "",
    apiKeyConfigured: !!raw?.apiKeyConfigured,
    protocol: toFeProtocol(raw?.protocol, kind),
    timeoutSeconds: raw?.timeoutSeconds,
    maxRetries: raw?.maxRetries,
    temperature: raw?.temperature ?? null,
    maxOutputTokens: raw?.maxOutputTokens ?? null,
    imageQuality: (raw?.imageQuality as AiProviderConfig["imageQuality"]) ?? null,
    enabled: raw?.enabled ?? true,
  };
}

function mapSettings(data: BackendAiSettings): AiSettings {
  return {
    text: mapProvider(data.text, "text"),
    image: mapProvider(data.image, "image"),
    vision: mapProvider(data.vision, "vision"),
  };
}

function toBackendBody(settings: AiSettings) {
  return {
    text: {
      name: settings.text.name,
      model: settings.text.model,
      baseUrl: settings.text.baseUrl,
      protocol: toBackendProtocol(settings.text.protocol),
      apiKey: settings.text.apiKey,
      timeoutSeconds: settings.text.timeoutSeconds,
      enabled: settings.text.enabled,
    },
    image: {
      name: settings.image.name,
      model: settings.image.model,
      baseUrl: settings.image.baseUrl,
      protocol: "openai_chat",
      apiKey: settings.image.apiKey,
      timeoutSeconds: settings.image.timeoutSeconds,
      enabled: settings.image.enabled,
      imageQuality: settings.image.imageQuality,
    },
    vision: {
      name: settings.vision.name,
      model: settings.vision.model,
      baseUrl: settings.vision.baseUrl,
      protocol: toBackendProtocol(settings.vision.protocol),
      apiKey: settings.vision.apiKey,
      timeoutSeconds: settings.vision.timeoutSeconds,
      enabled: settings.vision.enabled,
    },
  };
}

/** Invoke a generic text scene; dedicated workflows should use their own endpoints. */
export async function requestTextAi(request: TextRequest) {
  const response = await fetch("/api/ai/text", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      scene: request.scene,
      prompt: [request.systemPrompt, request.userPrompt].filter(Boolean).join("\n\n"),
    }),
  });
  const payload = await unwrapApiResult<{ text?: string }>(response);
  if (!payload.text?.trim()) throw new Error("文本 AI 未返回可用内容。");
  return payload.text.trim();
}

/** Perform a real provider request using the current settings form values. */
export async function testAiConnection(config: AiProviderConfig, kind: AiSettingsKind) {
  const response = await fetch(`/api/settings/ai/${kind}/test`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config }),
  });
  const payload = await unwrapApiResult<{
    preview?: string;
    details?: { url?: string; b64Json?: string };
    ok?: boolean;
  }>(response);
  return {
    message: payload.preview || "连接成功",
    imagePreview:
      payload.details?.url ||
      (payload.details?.b64Json ? `data:image/png;base64,${payload.details.b64Json}` : ""),
  };
}

/** Generate an image through the backend so API keys never reach upstream from the browser. */
export async function requestImageAi(request: {
  config: AiProviderConfig;
  scene: AiScene;
  prompt: string;
  size?: string;
}) {
  const response = await fetch("/api/ai/image", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      prompt: request.prompt,
      size: request.size || "1024x1024",
      dryRun: false,
    }),
  });
  return unwrapApiResult<{ b64Json?: string; url?: string; model?: string }>(response);
}

/** 从数据库加载 AI 设置（含 configured 标记，兼容 App 调用）。 */
export async function loadAiSettingsFromDatabase(): Promise<AiSettingsBundle> {
  const data = await unwrapApiResult<BackendAiSettings>(
    await fetch("/api/settings/ai", { cache: "no-store" }),
  );
  const settings = mapSettings(data);
  return {
    configured: !!(
      settings.text.apiKeyConfigured ||
      settings.image.apiKeyConfigured ||
      settings.vision.apiKeyConfigured
    ),
    settings,
    updatedAt: new Date().toISOString(),
  };
}

/** 保存 AI 设置到数据库。 */
export async function saveAiSettingsToDatabase(settings: AiSettings): Promise<AiSettingsBundle> {
  const data = await unwrapApiResult<BackendAiSettings>(
    await fetch("/api/settings/ai", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(toBackendBody(settings)),
    }),
  );
  const mapped = mapSettings(data);
  return {
    configured: true,
    settings: mapped,
    updatedAt: new Date().toISOString(),
  };
}

/* ---- Skills ---- */

export type SkillNodeBinding = {
  id?: string;
  workflowType: string;
  nodeKey: string;
  priority: number;
  enabled: boolean;
};

export type SkillWorkflowNode = {
  workflowType: string;
  nodeKey: string;
  name: string;
  group: string;
  modelType: "text" | "image";
};

export type Skill = {
  id: string;
  name: string;
  description: string;
  scope: "system" | "scene";
  skillType: "capability" | "instruction";
  locked: boolean;
  scene: string;
  systemInstructions: string;
  taskInstructions: string;
  priority: number;
  enabled: boolean;
  nodeBindings: SkillNodeBinding[];
  applications: Array<{
    modeId: string;
    modeName: string;
    workflowId: string;
    workflowName: string;
    nodeKey: string;
    nodeName: string;
    bindingState: "published";
  }>;
  updatedAt: string;
};

async function parseSkillResponse(response: Response, _fallback: string): Promise<Skill> {
  return unwrapApiResult<Skill>(response);
}

export async function listSkills(): Promise<Skill[]> {
  const response = await fetch("/api/settings/skills");
  return unwrapApiResult<Skill[]>(response);
}

/** Skill 列表分页结果。 */
export type SkillPage = {
  items: Skill[];
  total: number;
  page: number;
  pageSize: number;
};

/** 分页查询 Skill（后端 LIMIT/OFFSET，含关键字筛选）。 */
export async function pageSkills(
  params: {
    page?: number;
    pageSize?: number;
    keyword?: string;
  } = {},
): Promise<SkillPage> {
  const search = new URLSearchParams();
  search.set("page", String(params.page ?? 1));
  search.set("pageSize", String(params.pageSize ?? 10));
  const keyword = params.keyword?.trim();
  if (keyword) search.set("keyword", keyword);
  const response = await fetch(`/api/settings/skills/page?${search.toString()}`, {
    cache: "no-store",
  });
  return unwrapApiResult<SkillPage>(response);
}

export async function listSkillWorkflowNodes(): Promise<SkillWorkflowNode[]> {
  const response = await fetch("/api/settings/skills/workflow-nodes");
  return unwrapApiResult<SkillWorkflowNode[]>(response);
}

export async function createSkill(data: Partial<Skill>): Promise<Skill> {
  const response = await fetch("/api/settings/skills", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
  return parseSkillResponse(response, "创建 Skill 失败");
}

export async function updateSkill(skill: Skill): Promise<Skill> {
  const response = await fetch(`/api/settings/skills/${skill.id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(skill),
  });
  return parseSkillResponse(response, "更新 Skill 失败");
}

export async function deleteSkill(id: string): Promise<void> {
  const response = await fetch(`/api/settings/skills/${id}`, { method: "DELETE" });
  if (!response.ok) throw new Error("删除 Skill 失败");
}

/* ---- WeChat MP ---- */

/** 公众号配置（前端表单；不含明文 Secret）。 */
export type WechatMpSettings = {
  accountName: string;
  appId: string;
  appSecret: string;
  appSecretConfigured: boolean;
  defaultAuthor: string;
  lastDraftMediaId: string | null;
  lastPushedAt: string | null;
  updatedAt: string | null;
};

/** 连通性探测结果。 */
export type WechatMpConnectionTest = {
  ok: boolean;
  message: string;
};

/** 推送草稿结果。 */
export type WechatMpDraftPushResult = {
  mediaId: string;
  title: string;
  uploadedImageCount: number;
  pushedAt: string | null;
  /** true=覆盖已有草稿；false=新建草稿。 */
  updatedExisting: boolean;
};

/** 后端公众号配置视图字段。 */
type BackendWechatMpSettings = {
  accountName?: string | null;
  appId?: string | null;
  appSecretConfigured?: boolean;
  defaultAuthor?: string | null;
  lastDraftMediaId?: string | null;
  lastPushedAt?: string | null;
  updatedAt?: string | null;
};

/**
 * 将后端视图映射为前端表单模型（Secret 恒为空串）。
 *
 * @param raw 后端响应
 */
function mapWechatSettings(raw: BackendWechatMpSettings | null | undefined): WechatMpSettings {
  return {
    accountName: raw?.accountName || "",
    appId: raw?.appId || "",
    appSecret: "",
    appSecretConfigured: !!raw?.appSecretConfigured,
    defaultAuthor: raw?.defaultAuthor || "",
    lastDraftMediaId: raw?.lastDraftMediaId || null,
    lastPushedAt: raw?.lastPushedAt || null,
    updatedAt: raw?.updatedAt || null,
  };
}

/** 从数据库加载公众号配置。 */
export async function loadWechatMpSettings(): Promise<WechatMpSettings> {
  const data = await unwrapApiResult<BackendWechatMpSettings>(
    await fetch("/api/settings/wechat-mp", { cache: "no-store" }),
  );
  return mapWechatSettings(data);
}

/**
 * 保存公众号配置；空 Secret 表示保持已保存密钥。
 *
 * @param settings 表单值
 */
export async function saveWechatMpSettings(
  settings: Pick<WechatMpSettings, "accountName" | "appId" | "appSecret" | "defaultAuthor">,
): Promise<WechatMpSettings> {
  const data = await unwrapApiResult<BackendWechatMpSettings>(
    await fetch("/api/settings/wechat-mp", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        accountName: settings.accountName,
        appId: settings.appId,
        appSecret: settings.appSecret || "",
        defaultAuthor: settings.defaultAuthor,
      }),
    }),
  );
  return mapWechatSettings(data);
}

/**
 * 测试公众号凭证连通性。
 *
 * @param draft 当前表单草稿（可用未保存的 AppID/Secret）
 */
export async function testWechatMpConnection(
  draft: Pick<WechatMpSettings, "appId" | "appSecret">,
): Promise<WechatMpConnectionTest> {
  return unwrapApiResult<WechatMpConnectionTest>(
    await fetch("/api/settings/wechat-mp/test", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        appId: draft.appId,
        appSecret: draft.appSecret || "",
      }),
    }),
  );
}

/**
 * 将当前排版文章推送到公众号草稿箱。
 *
 * @param body 推送参数
 */
export async function pushWechatMpDraft(body: {
  /** 成文坊文章 ID；用于同文复推覆盖同一公众号草稿。 */
  articleId: string;
  /** 统一文档 ID（公众号轨）；有则后端优先写入下游。 */
  documentId?: string | null;
  markdown: string;
  themeVersionId: string;
  title?: string;
  digest?: string;
  author?: string;
  coverSrc?: string;
}): Promise<WechatMpDraftPushResult> {
  return unwrapApiResult<WechatMpDraftPushResult>(
    await fetch("/api/publishing/wechat-mp/draft", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        ...body,
        documentId: body.documentId || undefined,
      }),
    }),
  );
}

/* ---- System ---- */

/** 内置 Skill / 工作流系统就绪视图。 */
export type BuiltinReadyView = {
  ready: boolean;
  issues: string[];
  bootstrapHint: string;
  skillCount: number;
  expectedSkillCount: number;
  workflowReady: boolean;
};

/**
 * 拉取内置配置就绪体检结果。
 *
 * @returns 就绪视图
 */
export async function fetchBuiltinReady(): Promise<BuiltinReadyView> {
  const response = await fetch("/api/system/builtin-ready", { cache: "no-store" });
  return unwrapApiResult<BuiltinReadyView>(response);
}
