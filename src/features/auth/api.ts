import {
  AUTH_TOKEN_STORAGE_KEY,
  invalidateAuthSession,
  unwrapApiResult,
  withAuthHeaders,
} from "../../shared/api/http";

/** 当前用户视图（与后端 CurrentUserView 对齐）。 */
export type CurrentUser = {
  id: string;
  username: string;
  displayName: string;
  avatarUrl?: string | null;
  roles: string[];
  permissions: string[];
};

/** 用户管理列表项。 */
export type UserListItem = {
  id: string;
  username: string;
  displayName: string;
  avatarUrl?: string | null;
  enabled: boolean;
  roles: string[];
  lastLoginAt?: string | null;
  lastLoginIp?: string | null;
  lastLoginRegion?: string | null;
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

/** 与后端 {@code AuthPermissions.USER_MANAGE} 对齐。 */
export const USER_MANAGE_PERMISSION = "user:manage";

/** 是否可操作用户管理（与后端 user:manage 对齐）。 */
export function canManageUsers(user: CurrentUser | null | undefined): boolean {
  return Boolean(user?.permissions?.includes(USER_MANAGE_PERMISSION));
}

/** 鉴权探测超时（毫秒）：后端未起时避免 Vite 代理挂死导致整页「加载中」。 */
const AUTH_PROBE_TIMEOUT_MS = 8_000;

/** 查询鉴权公开状态（无需登录）。 */
export async function fetchAuthStatus(): Promise<AuthStatus> {
  const response = await fetch("/api/auth/status", {
    cache: "no-store",
    signal: AbortSignal.timeout(AUTH_PROBE_TIMEOUT_MS),
  });
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

/** 保存访问令牌（仅 login 内部使用）。 */
function storeToken(token: string): void {
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

/** 登出并清本地令牌，通知门禁回登录页。 */
export async function logout(): Promise<void> {
  try {
    await fetch("/api/auth/logout", {
      method: "POST",
      headers: withAuthHeaders(),
    });
  } finally {
    invalidateAuthSession();
  }
}

/** 改密成功后带到登录页的一次性提示键。 */
export const AUTH_LOGIN_NOTICE_KEY = "chengwenfang.auth.loginNotice";

/**
 * 本人修改密码；成功后服务端踢会话，本地清令牌并回登录页。
 *
 * @param currentPassword 当前密码
 * @param newPassword 新密码
 */
export async function changePassword(
  currentPassword: string,
  newPassword: string,
): Promise<void> {
  const response = await fetch("/api/auth/change-password", {
    method: "POST",
    headers: withAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ currentPassword, newPassword }),
  });
  await unwrapApiResult<null>(response);
  try {
    sessionStorage.setItem(AUTH_LOGIN_NOTICE_KEY, "密码已修改，请重新登录");
  } catch {
    // 忽略无 sessionStorage 环境
  }
  invalidateAuthSession();
}

/**
 * 校验当前密码是否正确（不改密、不踢会话）。
 *
 * @param currentPassword 当前密码
 */
export async function verifyCurrentPassword(currentPassword: string): Promise<void> {
  const response = await fetch("/api/auth/verify-password", {
    method: "POST",
    headers: withAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ currentPassword }),
  });
  await unwrapApiResult<null>(response);
}

/** 拉取当前用户；未登录返回 null。 */
export async function fetchMe(): Promise<CurrentUser | null> {
  const response = await fetch("/api/me", {
    headers: withAuthHeaders(),
    signal: AbortSignal.timeout(AUTH_PROBE_TIMEOUT_MS),
  });
  if (response.status === 401) {
    // 与 apiFetch 一致：清令牌并通知 AuthGate（启动探测时门禁本身会落到登录页）
    invalidateAuthSession();
    return null;
  }
  return unwrapApiResult<CurrentUser | null>(response);
}

/**
 * 更新当前用户展示名。
 *
 * @param displayName 新展示名
 */
export async function updateProfile(displayName: string): Promise<CurrentUser> {
  const response = await fetch("/api/me", {
    method: "PATCH",
    headers: withAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ displayName }),
  });
  return unwrapApiResult<CurrentUser>(response);
}

/**
 * 上传头像。
 *
 * @param file 图片文件
 */
export async function uploadAvatar(file: File): Promise<CurrentUser> {
  const form = new FormData();
  form.append("file", file);
  const response = await fetch("/api/me/avatar", {
    method: "POST",
    headers: withAuthHeaders(),
    body: form,
  });
  return unwrapApiResult<CurrentUser>(response);
}

/** 用户分页结果。 */
export type UserPageResult = {
  items: UserListItem[];
  total: number;
  page: number;
  pageSize: number;
};

/**
 * 管理员：用户分页列表。
 *
 * @param page 页码，从 1 开始
 * @param pageSize 每页条数
 */
export async function listUsersPage(page: number, pageSize: number): Promise<UserPageResult> {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  });
  const response = await fetch(`/api/users?${params.toString()}`, {
    headers: withAuthHeaders(),
  });
  return unwrapApiResult<UserPageResult>(response);
}

/**
 * 管理员：创建用户。
 *
 * @param input 创建参数
 */
export async function createUser(input: {
  username: string;
  password: string;
  displayName?: string;
  role: string;
}): Promise<UserListItem> {
  const response = await fetch("/api/users", {
    method: "POST",
    headers: withAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(input),
  });
  return unwrapApiResult<UserListItem>(response);
}

/** 重置密码结果（明文仅本次可见）。 */
export type ResetPasswordResult = {
  userId: string;
  username: string;
  password: string;
};

/**
 * 管理员：启停用户。
 *
 * @param userId 用户 ID
 * @param enabled 是否启用
 */
export async function setUserEnabled(userId: string, enabled: boolean): Promise<UserListItem> {
  const response = await fetch(`/api/users/${encodeURIComponent(userId)}/enabled`, {
    method: "PATCH",
    headers: withAuthHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify({ enabled }),
  });
  return unwrapApiResult<UserListItem>(response);
}

/**
 * 管理员：重置用户密码。
 *
 * @param userId 用户 ID
 */
export async function resetUserPassword(userId: string): Promise<ResetPasswordResult> {
  const response = await fetch(`/api/users/${encodeURIComponent(userId)}/reset-password`, {
    method: "POST",
    headers: withAuthHeaders(),
  });
  return unwrapApiResult<ResetPasswordResult>(response);
}
