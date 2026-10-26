/** 后端统一响应信封。 */
export type ApiResult<T> = {
  code: string;
  msg: string;
  data: T;
};

const OK = "ok";

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
