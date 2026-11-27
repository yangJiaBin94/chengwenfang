import { ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { AUTH_SESSION_EXPIRED_EVENT } from "../../shared/api/http";
import { clearStoredToken, fetchAuthStatus, fetchMe, getStoredToken, type CurrentUser } from "./api";
import { AuthUserContext } from "./AuthUserContext";
import { LoginPage } from "./LoginPage";

type Props = {
  children: ReactNode;
};

type GateState =
  | { kind: "booting" }
  | { kind: "open"; user: CurrentUser | null }
  | { kind: "login"; registerEnabled: boolean };

/**
 * 鉴权门禁：未登录一律展示登录页；会话 401 失效时立即卸子树回登录，挡住后续裸请求。
 */
export function AuthGate({ children }: Props) {
  const [state, setState] = useState<GateState>({ kind: "booting" });
  const [nonce, setNonce] = useState(0);
  const registerEnabledRef = useRef(false);

  useEffect(() => {
    function onSessionExpired() {
      // 立刻卸掉业务树，避免清 token 后轮询/并行请求继续打受保护接口
      setState({ kind: "login", registerEnabled: registerEnabledRef.current });
    }
    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, onSessionExpired);
    return () => {
      window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, onSessionExpired);
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const status = await fetchAuthStatus();
        if (cancelled) {
          return;
        }
        registerEnabledRef.current = status.registerEnabled;
        if (!getStoredToken()) {
          setState({ kind: "login", registerEnabled: status.registerEnabled });
          return;
        }
        const me = await fetchMe();
        if (cancelled) {
          return;
        }
        if (!me) {
          clearStoredToken();
          setState({ kind: "login", registerEnabled: status.registerEnabled });
          return;
        }
        setState({ kind: "open", user: me });
      } catch {
        if (cancelled) {
          return;
        }
        // 后端短暂不可达：有 token 先放行；无 token 进登录页
        if (getStoredToken()) {
          setState({ kind: "open", user: null });
        } else {
          setState({ kind: "login", registerEnabled: registerEnabledRef.current });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [nonce]);

  const setUser = useCallback((user: CurrentUser | null) => {
    setState((previous) => (previous.kind === "open" ? { kind: "open", user } : previous));
  }, []);

  const refreshUser = useCallback(async () => {
    const me = await fetchMe();
    if (!me) {
      return;
    }
    setState((previous) => (previous.kind === "open" ? { kind: "open", user: me } : previous));
  }, []);

  if (state.kind === "booting") {
    return (
      <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", color: "#64748b" }}>
        加载中…
      </div>
    );
  }

  if (state.kind === "login") {
    return (
      <LoginPage
        registerEnabled={state.registerEnabled}
        onSuccess={() => setNonce((value) => value + 1)}
      />
    );
  }

  return (
    <AuthUserContext.Provider value={{ user: state.user, setUser, refreshUser }}>
      {children}
    </AuthUserContext.Provider>
  );
}
