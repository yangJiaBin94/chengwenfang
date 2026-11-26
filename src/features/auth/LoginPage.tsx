import { FormEvent, useState } from "react";
import { login } from "./api";

type Props = {
  /** 登录成功回调。 */
  onSuccess: () => void;
  /** 是否显示注册提示（仅文案；自助注册另开）。 */
  registerEnabled?: boolean;
};

/**
 * 登录页：与主站纸色书斋气质对齐。
 */
export function LoginPage({ onSuccess, registerEnabled = false }: Props) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await login(username.trim(), password);
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-page__atmosphere" aria-hidden="true" />
      <div className="login-page__grain" aria-hidden="true" />

      <div className="login-page__stage">
        <header className="login-page__brand">
          <p className="login-page__mark">成文坊</p>
          <p className="login-page__tagline">把选题、成文与发布收进同一张工作台</p>
        </header>

        <form className="login-page__panel" onSubmit={onSubmit}>
          <div className="login-page__panel-head">
            <h1>登录</h1>
            {registerEnabled ? <p>当前已开放自助注册。</p> : null}
          </div>

          <label className="login-page__field">
            <span>用户名</span>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
            />
          </label>

          <label className="login-page__field">
            <span>密码</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>

          {error ? <div className="login-page__error" role="alert">{error}</div> : null}

          <button className="login-page__submit" type="submit" disabled={pending}>
            {pending ? "登录中…" : "进入工作台"}
          </button>
        </form>
      </div>
    </div>
  );
}
