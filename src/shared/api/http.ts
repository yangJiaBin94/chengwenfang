/** 后端统一响应信封。 */
export type ApiResult<T> = {
  code: string;
  msg: string;
  data: T;
};

const OK = "ok";

/** 与 `features/auth/api` 共用的本地令牌键。 */
export const AUTH_TOKEN_STORAGE_KEY = "chengwenfang.auth.token";

/**
 * 合并 Authorization Bearer（若本地有令牌）。
 *
 * @param headers 原有请求头
 * @return 含鉴权头的对象
 */
export function withAuthHeaders(headers: Record<string, string> = {}): Record<string, string> {
  try {
    const token = localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  } catch {
    // 忽略无 localStorage 环境
  }
  return headers;
}

/**
 * 给无法自定义 Header 的资源 URL（如 Markdown 里的 &lt;img&gt;）追加 satoken 查询参数。
 * 与 EventSource / 后端 {@code SaTokenConfigure#bindQueryTokenIfPresent} 对齐。
 *
 * @param url 原始 URL；非本站受保护资源原样返回
 * @return 可能带 satoken 的 URL
 */
export function withAuthQuery(url: string): string {
  const raw = (url || "").trim();
  if (!needsAuthQuery(raw)) {
    return raw;
  }
  const hashIndex = raw.indexOf("#");
  const beforeHash = hashIndex >= 0 ? raw.slice(0, hashIndex) : raw;
  const hash = hashIndex >= 0 ? raw.slice(hashIndex) : "";
  if (/(?:^|[?&])satoken=/.test(beforeHash)) {
    return raw;
  }
  try {
    const token = localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
    if (!token) {
      return raw;
    }
    const join = beforeHash.includes("?") ? "&" : "?";
    return `${beforeHash}${join}satoken=${encodeURIComponent(token)}${hash}`;
  } catch {
    return raw;
  }
}

/**
 * 是否为本站受登录保护的文件/配图路径。
 *
 * @param url 原始 URL
 * @return true 需要 satoken
 */
function needsAuthQuery(url: string): boolean {
  if (!url) {
    return false;
  }
  if (url.startsWith("/api/images/") || url.startsWith("/api/files/")) {
    return true;
  }
  try {
    if (typeof window === "undefined" || !window.location?.origin) {
      return false;
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      return false;
    }
    const parsed = new URL(url, window.location.origin);
    if (parsed.origin !== window.location.origin) {
      return false;
    }
    return (
      parsed.pathname.startsWith("/api/images/")
      || parsed.pathname.startsWith("/api/files/")
    );
  } catch {
    return false;
  }
}

/**
 * 给 HTML 中的 /api/images|/api/files 的 src 补 satoken（iframe srcDoc 预览用）。
 * 故意不改 href，避免点击链接把 token 写进地址栏。
 *
 * @param html HTML 片段或文档
 * @return 改写后的 HTML
 */
export function withAuthQueryInHtml(html: string): string {
  if (!html) {
    return html;
  }
  // 相对路径与同站绝对 URL（withAuthQuery 内再校验 origin）
  return html.replace(
    /\bsrc=(["'])((?:https?:\/\/[^"'/]+)?\/api\/(?:images|files)\/[^"']+)\1/gi,
    (_match, quote: string, resourceUrl: string) =>
      `src=${quote}${withAuthQuery(resourceUrl)}${quote}`,
  );
}

/**
 * 用鉴权 Header 拉取受保护文件并在新标签打开（避免把 token 写进地址栏）。
 *
 * @param url /api/files/... 或 /api/images/...
 */
export async function openProtectedResource(url: string): Promise<void> {
  const response = await apiFetch(url);
  if (!response.ok) {
    throw new Error(`打开失败：HTTP ${response.status}`);
  }
  const blob = await response.blob();
  const objectUrl = URL.createObjectURL(blob);
  // 不用 noopener：带 noopener 时 open 返回值恒为 null，会误触发下载兜底
  const opened = window.open(objectUrl, "_blank");
  if (opened) {
    opened.opener = null;
    try {
      opened.addEventListener("beforeunload", () => {
        URL.revokeObjectURL(objectUrl);
      });
    } catch {
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 30 * 60_000);
    }
    return;
  }
  // 弹窗被拦时退化为下载
  const anchor = document.createElement("a");
  anchor.href = objectUrl;
  const fileName = url.split(/[?#]/)[0].split("/").filter(Boolean).pop() || "download";
  anchor.download = decodeURIComponent(fileName);
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
}

/**
 * 带鉴权头的 fetch 封装（业务 API 需走此方法）。
 *
 * @param input 请求 URL
 * @param init  请求选项
 */
export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  const auth = withAuthHeaders();
  if (auth.Authorization) {
    headers.set("Authorization", auth.Authorization);
  }
  const response = await fetch(input, { ...init, headers });
  if (response.status === 401) {
    try {
      localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
    } catch {
      // ignore
    }
  }
  return response;
}

/**
 * 解析 JSON 响应：校验 HTTP 与业务 code，成功返回 data。
 * 代理失败 / 后端未启动时常返回空体，这里给出可读错误，避免 JSON.parse 抛原生异常。
 */
export async function unwrapApiResult<T>(response: Response): Promise<T> {
  const raw = await response.text();
  if (!raw.trim()) {
    throw new Error(
      response.status === 502 || response.status === 503 || response.status === 504
        ? "后端暂时不可用，请确认服务已在 :8001 启动"
        : response.ok
          ? "后端返回空响应，请确认服务已在 :8001 启动"
          : `请求失败：HTTP ${response.status}（无响应体，请确认后端已启动）`,
    );
  }
  let payload: ApiResult<T> & { message?: string; error?: string };
  try {
    payload = JSON.parse(raw) as ApiResult<T> & {
      message?: string;
      error?: string;
    };
  } catch {
    throw new Error(
      `后端响应不是合法 JSON（HTTP ${response.status}），请确认服务已在 :8001 启动`,
    );
  }
  const message =
    payload.msg || payload.message || payload.error || `请求失败：${response.status}`;
  if (!response.ok) {
    // 错误文案只给用户可读信息；traceId 仍在响应头，日志可查
    throw new Error(message);
  }
  if (payload.code && payload.code !== OK) {
    throw new Error(message);
  }
  return payload.data;
}
