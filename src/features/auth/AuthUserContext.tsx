import { createContext, useContext } from "react";
import type { CurrentUser } from "./api";

/** 登录会话：当前用户 + 刷新。 */
export type AuthSession = {
  user: CurrentUser | null;
  setUser: (user: CurrentUser | null) => void;
  refreshUser: () => Promise<void>;
};

const noopSession: AuthSession = {
  user: null,
  setUser: () => undefined,
  refreshUser: async () => undefined,
};

/** 已登录后的会话上下文；未登录时 AuthGate 不渲染子树。 */
export const AuthUserContext = createContext<AuthSession>(noopSession);

/** 读取当前登录用户；门禁外调用会得到 null。 */
export function useAuthUser(): CurrentUser | null {
  return useContext(AuthUserContext).user;
}

/** 读取完整会话（含刷新）。 */
export function useAuthSession(): AuthSession {
  return useContext(AuthUserContext);
}
