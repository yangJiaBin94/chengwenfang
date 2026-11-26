import { ReactNode, useEffect, useState } from "react";
import { clearStoredToken, fetchAuthStatus, fetchMe, getStoredToken } from "./api";
import { LoginPage } from "./LoginPage";

type Props = {
  children: ReactNode;
};

type GateState =
  | { kind: "booting" }
  | { kind: "open" }
  | { kind: "login"; registerEnabled: boolean };

/**
 * 鉴权门禁：未登录一律展示登录页。
 */
export function AuthGate({ children }: Props) {
  const [state, setState] = useState<GateState>({ kind: "booting" });
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const status = await fetchAuthStatus();
        if (cancelled) {
          return;
        }
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
        setState({ kind: "open" });
      } catch {
        if (cancelled) {
          return;
        }
        // 后端短暂不可达：有 token 先放行；无 token 进登录页
        if (getStoredToken()) {
          setState({ kind: "open" });
        } else {
          setState({ kind: "login", registerEnabled: false });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [nonce]);

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

  return <>{children}</>;
}
