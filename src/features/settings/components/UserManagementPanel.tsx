import { FormEvent, useEffect, useRef, useState } from "react";
import { Check, ChevronDown, ChevronLeft, ChevronRight, Copy, KeyRound, Plus, Search, X } from "lucide-react";
import {
  canManageUsers,
  createUser,
  listUsersPage,
  resetUserPassword,
  setUserEnabled,
  type UserListItem,
} from "../../auth/api";
import { useAuthUser } from "../../auth/AuthUserContext";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";
import { withAuthQuery } from "../../../shared/api/http";
import { formatConfirmTime } from "../../../shared/format/time";

/** 与后端 UserRole 闭集对齐。 */
const USER_ROLE = {
  ADMIN: "ADMIN",
  EDITOR: "EDITOR",
  VIEWER: "VIEWER",
} as const;

const ROLE_OPTIONS = [
  { value: USER_ROLE.EDITOR, label: "EDITOR（编辑）" },
  { value: USER_ROLE.VIEWER, label: "VIEWER（只读）" },
  { value: USER_ROLE.ADMIN, label: "ADMIN（管理员）" },
] as const;

/**
 * 管理员用户管理：分页列表 + 弹窗创建。
 */
export function UserManagementPanel() {
  const currentUser = useAuthUser();
  const manageUsers = canManageUsers(currentUser);
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [listPage, setListPage] = useState(1);
  const [pageSize, setPageSize] = useState<ListPageSize>(10);
  const [pageSizeOpen, setPageSizeOpen] = useState(false);
  const pageSizeRef = useRef<HTMLDivElement | null>(null);
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState("");
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");

  const [createOpen, setCreateOpen] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [role, setRole] = useState<string>(USER_ROLE.EDITOR);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState("");

  const [togglingId, setTogglingId] = useState("");
  const [resettingId, setResettingId] = useState("");
  const [resetResult, setResetResult] = useState<{ username: string; password: string } | null>(null);
  const [resetError, setResetError] = useState("");
  const [copied, setCopied] = useState(false);

  const listPageCount = Math.max(1, Math.ceil(total / pageSize));

  async function reload(page = listPage, size = pageSize, keyword = debouncedQuery) {
    setListError("");
    setLoading(true);
    try {
      const result = await listUsersPage(page, size, keyword);
      setUsers(result.items ?? []);
      setTotal(Number(result.total) || 0);
      setListPage(Number(result.page) || page);
    } catch (caught) {
      setListError(caught instanceof Error ? caught.message : "加载用户失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setListPage(1);
    }, 280);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    if (!manageUsers) {
      return;
    }
    void reload(listPage, pageSize, debouncedQuery);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 随页码/页大小/昵称搜索拉取
  }, [manageUsers, listPage, pageSize, debouncedQuery]);

  useEffect(() => {
    if (!pageSizeOpen) {
      return;
    }
    function onDocumentClick(event: MouseEvent) {
      if (!pageSizeRef.current?.contains(event.target as Node)) {
        setPageSizeOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocumentClick);
    return () => document.removeEventListener("mousedown", onDocumentClick);
  }, [pageSizeOpen]);

  function resetCreateForm() {
    setUsername("");
    setPassword("");
    setDisplayName("");
    setRole(USER_ROLE.EDITOR);
    setCreateError("");
  }

  function openCreateModal() {
    resetCreateForm();
    setCreateOpen(true);
  }

  function closeCreateModal() {
    setCreateOpen(false);
    resetCreateForm();
  }

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    setCreateError("");
    if (password.length < 6) {
      setCreateError("密码至少 6 位");
      return;
    }
    setCreating(true);
    try {
      await createUser({
        username: username.trim(),
        password,
        displayName: displayName.trim() || undefined,
        role,
      });
      closeCreateModal();
      setListPage(1);
      await reload(1, pageSize, debouncedQuery);
    } catch (caught) {
      setCreateError(caught instanceof Error ? caught.message : "创建失败");
    } finally {
      setCreating(false);
    }
  }

  async function onToggleEnabled(item: UserListItem) {
    if (togglingId || item.id === currentUser?.id) {
      return;
    }
    setListError("");
    setTogglingId(item.id);
    try {
      const updated = await setUserEnabled(item.id, !item.enabled);
      setUsers((previous) =>
        previous.map((row) => (row.id === updated.id ? { ...row, ...updated } : row)),
      );
    } catch (caught) {
      setListError(caught instanceof Error ? caught.message : "更新状态失败");
    } finally {
      setTogglingId("");
    }
  }

  async function onResetPassword(item: UserListItem) {
    if (resettingId) {
      return;
    }
    setResetError("");
    setCopied(false);
    setResettingId(item.id);
    try {
      const result = await resetUserPassword(item.id);
      setResetResult({ username: result.username, password: result.password });
    } catch (caught) {
      setResetError(caught instanceof Error ? caught.message : "重置密码失败");
      setResetResult(null);
    } finally {
      setResettingId("");
    }
  }

  async function onCopyPassword() {
    if (!resetResult?.password) {
      return;
    }
    try {
      await navigator.clipboard.writeText(resetResult.password);
      setCopied(true);
    } catch {
      setCopied(false);
      setResetError("复制失败，请手动选中密码");
    }
  }

  function closeResetModal() {
    setResetResult(null);
    setResetError("");
    setCopied(false);
  }

  if (!manageUsers) {
    return (
      <div className="user-mgmt-panel">
        <div className="settings-hero user-mgmt-hero">
          <div>
            <span className="section-kicker">User Management</span>
            <h2>用户管理</h2>
            <p>仅管理员可查看与操作。</p>
          </div>
        </div>
        <p className="user-mgmt-forbidden">无权限</p>
      </div>
    );
  }

  return (
    <div className="user-mgmt-panel">
      <div className="settings-hero user-mgmt-hero">
        <div>
          <span className="section-kicker">User Management</span>
          <h2>用户管理</h2>
          <p>查看系统用户并创建新账号。仅管理员可操作。</p>
        </div>
        <button type="button" className="primary-button user-mgmt-create-btn" onClick={openCreateModal}>
          <Plus size={16} />
          创建用户
        </button>
      </div>

      <section className="user-mgmt-card user-mgmt-list" aria-label="用户列表">
        <div className="user-mgmt-list-head">
          <h3>用户列表</h3>
          <label className="user-mgmt-search-box">
            <Search size={15} />
            <input
              aria-label="按昵称搜索"
              placeholder="按昵称搜索"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
            {query ? (
              <button
                type="button"
                className="user-mgmt-search-clear"
                aria-label="清除昵称搜索"
                onClick={() => setQuery("")}
              >
                <X size={14} />
              </button>
            ) : null}
          </label>
        </div>
        {listError ? <p className="user-mgmt-error">{listError}</p> : null}
        <div
          className="user-mgmt-table-wrap"
          style={{ ["--user-mgmt-page-size" as string]: pageSize }}
        >
          <div className="user-mgmt-table">
            <div className="user-mgmt-table-head">
              <span>头像</span>
              <span>用户名</span>
              <span>昵称</span>
              <span>角色</span>
              <span>状态</span>
              <span>最近登录</span>
              <span>最近登录地</span>
              <span>操作</span>
            </div>
            {loading && users.length === 0 ? (
              <div className="user-mgmt-table-empty">正在加载用户…</div>
            ) : users.length === 0 ? (
              <div className="user-mgmt-table-empty">暂无用户</div>
            ) : (
              users.map((item) => {
                const avatarSrc = item.avatarUrl ? withAuthQuery(item.avatarUrl) : null;
                const initial = (item.displayName || item.username || "?").slice(0, 1).toUpperCase();
                return (
                  <div className="user-mgmt-table-row" key={item.id}>
                    <span className="user-mgmt-avatar-cell">
                      {avatarSrc ? (
                        <img src={avatarSrc} alt="" className="user-mgmt-avatar-thumb" />
                      ) : (
                        <span className="user-mgmt-avatar-fallback">{initial}</span>
                      )}
                    </span>
                    <strong>{item.username}</strong>
                    <span className="user-mgmt-display-name">{item.displayName || "—"}</span>
                    <span className="user-mgmt-roles">
                      {item.roles?.length
                        ? item.roles.map((roleName) => (
                            <em className="user-mgmt-role-badge" key={roleName}>
                              {roleName}
                            </em>
                          ))
                        : "—"}
                    </span>
                    <button
                      type="button"
                      className={`layout-theme-status user-mgmt-status-toggle ${item.enabled ? "is-enabled" : "is-disabled"}`}
                      disabled={togglingId === item.id || item.id === currentUser?.id}
                      title={
                        item.id === currentUser?.id
                          ? "不能切换当前登录账号状态"
                          : item.enabled
                            ? "点击停用"
                            : "点击启用"
                      }
                      aria-pressed={item.enabled}
                      onClick={() => void onToggleEnabled(item)}
                    >
                      {togglingId === item.id ? "…" : item.enabled ? "启用" : "停用"}
                    </button>
                    <span className="user-mgmt-last-login">
                      {item.lastLoginAt ? (
                        <>
                          <em>{formatConfirmTime(item.lastLoginAt)}</em>
                          {item.lastLoginIp ? <small>{item.lastLoginIp}</small> : null}
                        </>
                      ) : (
                        <em>尚未登录</em>
                      )}
                    </span>
                    <span className="user-mgmt-last-region">
                      {item.lastLoginRegion || (item.lastLoginAt ? "未知" : "—")}
                    </span>
                    <span className="user-mgmt-actions">
                      <button
                        type="button"
                        className="secondary-button"
                        disabled={resettingId === item.id}
                        onClick={() => void onResetPassword(item)}
                      >
                        {resettingId === item.id ? "重置中…" : "重置密码"}
                      </button>
                    </span>
                  </div>
                );
              })
            )}
          </div>
        </div>

        <nav className="source-management-pager user-mgmt-pager" aria-label="用户列表分页">
          <span className="source-management-pager-total">共 {total} 条</span>
          <div className="source-management-pager-controls">
            <button
              type="button"
              className="source-management-pager-nav"
              disabled={listPage <= 1 || loading}
              aria-label="上一页"
              onClick={() => setListPage((page) => Math.max(1, page - 1))}
            >
              <ChevronLeft size={16} />
              上一页
            </button>
            {listPageNumbers(listPage, listPageCount).map((token, index) =>
              token === "ellipsis" ? (
                <span key={`e-${index}`} className="source-management-pager-ellipsis">
                  …
                </span>
              ) : (
                <button
                  type="button"
                  key={token}
                  className={`source-management-pager-page${token === listPage ? " active" : ""}`}
                  disabled={loading}
                  aria-current={token === listPage ? "page" : undefined}
                  aria-label={`第 ${token} 页`}
                  onClick={() => setListPage(token)}
                >
                  {token}
                </button>
              ),
            )}
            <button
              type="button"
              className="source-management-pager-nav"
              disabled={listPage >= listPageCount || loading}
              aria-label="下一页"
              onClick={() => setListPage((page) => Math.min(listPageCount, page + 1))}
            >
              下一页
              <ChevronRight size={16} />
            </button>
          </div>
          <div className="source-management-pager-size" ref={pageSizeRef}>
            <span>每页</span>
            <button
              type="button"
              className={`source-management-pager-size-trigger${pageSizeOpen ? " open" : ""}`}
              disabled={loading}
              aria-label="每页条数"
              aria-haspopup="listbox"
              aria-expanded={pageSizeOpen}
              onClick={() => setPageSizeOpen((openMenu) => !openMenu)}
            >
              <em>{pageSize} 条</em>
              <ChevronDown size={14} />
            </button>
            {pageSizeOpen ? (
              <ul className="source-management-pager-size-menu" role="listbox" aria-label="可选每页条数">
                {LIST_PAGE_SIZE_OPTIONS.map((size) => {
                  const active = size === pageSize;
                  return (
                    <li key={size} role="option" aria-selected={active}>
                      <button
                        type="button"
                        className={active ? "active" : undefined}
                        onClick={() => {
                          setPageSize(size);
                          setListPage(1);
                          setPageSizeOpen(false);
                        }}
                      >
                        <span>{size} 条</span>
                        {active ? <Check size={14} /> : null}
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : null}
          </div>
        </nav>
      </section>

      {resetResult || resetError ? (
        <div className="modal-backdrop" onClick={closeResetModal}>
          <section
            className="modal-card user-mgmt-reset-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="user-mgmt-reset-title"
            onClick={(event) => event.stopPropagation()}
          >
            <button
              type="button"
              className="user-mgmt-reset-close"
              aria-label="关闭"
              onClick={closeResetModal}
            >
              <X size={16} />
            </button>
            <div className="user-mgmt-reset-icon" aria-hidden="true">
              <KeyRound size={22} />
            </div>
            <h3 id="user-mgmt-reset-title">重置密码成功</h3>
            {resetError ? <p className="user-mgmt-error">{resetError}</p> : null}
            {resetResult ? (
              <div className="user-mgmt-reset-body">
                <p className="user-mgmt-reset-lead">
                  用户 <strong>{resetResult.username}</strong> 的新密码已生成
                </p>
                <div className="user-mgmt-reset-password-card">
                  <span className="user-mgmt-reset-password-label">新密码</span>
                  <code>{resetResult.password}</code>
                  <button
                    type="button"
                    className={`user-mgmt-reset-copy${copied ? " is-copied" : ""}`}
                    onClick={() => void onCopyPassword()}
                  >
                    {copied ? <Check size={15} /> : <Copy size={15} />}
                    {copied ? "已复制" : "复制密码"}
                  </button>
                </div>
                <p className="user-mgmt-reset-tip">请立即复制并妥善保管，关闭后将无法再次查看。</p>
              </div>
            ) : null}
            <div className="user-mgmt-reset-actions">
              <button type="button" className="primary-button" onClick={closeResetModal}>
                我已保存，关闭
              </button>
            </div>
          </section>
        </div>
      ) : null}

      {createOpen ? (
        <div className="modal-backdrop" onClick={closeCreateModal}>
          <section
            className="modal-card user-mgmt-create-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="user-mgmt-create-title"
            onClick={(event) => event.stopPropagation()}
          >
            <header className="modal-header">
              <h3 id="user-mgmt-create-title">创建用户</h3>
              <button type="button" className="ghost-button" aria-label="关闭" onClick={closeCreateModal}>
                <X size={16} />
              </button>
            </header>
            <form className="user-mgmt-form" onSubmit={onCreate} autoComplete="off">
              <label>
                用户名
                <input
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  minLength={3}
                  maxLength={64}
                  autoComplete="off"
                  required
                />
              </label>
              <label>
                密码
                <input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  minLength={6}
                  maxLength={72}
                  autoComplete="new-password"
                  required
                />
              </label>
              <label>
                昵称
                <input
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                  maxLength={128}
                  placeholder="可空，默认用用户名"
                  autoComplete="off"
                />
              </label>
              <label>
                角色
                <select value={role} onChange={(event) => setRole(event.target.value)}>
                  {ROLE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>
              {createError ? <p className="user-mgmt-error">{createError}</p> : null}
              <div className="modal-actions">
                <button type="button" onClick={closeCreateModal}>
                  取消
                </button>
                <button type="submit" className="primary-button" disabled={creating}>
                  {creating ? "创建中…" : "创建"}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </div>
  );
}
