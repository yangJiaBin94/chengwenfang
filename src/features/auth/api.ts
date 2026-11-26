import { AUTH_TOKEN_STORAGE_KEY, unwrapApiResult, withAuthHeaders } from "../../shared/api/http";

/** 当前用户视图（与后端 CurrentUserView 对齐）。 */
export type CurrentUser = {
  id: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
};

/** 登录结果。 */
export type LoginResult = {
  token: string;
  tokenName: string;
  user: CurrentUser;
};

/** 鉴权公开状态（注册开关等）。 */
export type AuthStatus = {
  registerEnabled: boolean;
};

/** 查询鉴权公开状态（无需登录）。 */
export async function fetchAuthStatus(): Promise<AuthStatus> {
  const response = await fetch("/api/auth/status", { cache: "no-store" });
  return unwrapApiResult<AuthStatus>(response);
}

/** 读取本地保存的访问令牌。 */
export function getStoredToken(): string | null {
  try {
    return localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

/** 保存访问令牌。 */
export function storeToken(token: string): void {
  localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token);
}

/** 清除访问令牌。 */
export function clearStoredToken(): void {
  localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
}

/**
 * 账号密码登录并缓存令牌。
 *
 * @param username 登录名
 * @param password 密码
 */
export async function login(username: string, password: string): Promise<LoginResult> {
  const response = await fetch("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  const result = await unwrapApiResult<LoginResult>(response);
  storeToken(result.token);
  return result;
}

/** 登出并清本地令牌。 */
export async function logout(): Promise<void> {
  try {
    await fetch("/api/auth/logout", {
      method: "POST",
      headers: withAuthHeaders(),
    });
  } finally {
    clearStoredToken();
  }
}

/** 拉取当前用户；未登录返回 null。 */
export async function fetchMe(): Promise<CurrentUser | null> {
  const response = await fetch("/api/me", {
    headers: withAuthHeaders(),
  });
  if (response.status === 401) {
    clearStoredToken();
    return null;
  }
  return unwrapApiResult<CurrentUser | null>(response);
}
