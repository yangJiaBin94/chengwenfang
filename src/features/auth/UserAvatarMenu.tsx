import { FormEvent, useEffect, useRef, useState } from "react";
import { Camera, KeyRound, LogOut, UserRound, Users } from "lucide-react";
import { withAuthQuery } from "../../shared/api/http";
import {
  canManageUsers,
  changePassword,
  logout,
  updateProfile,
  uploadAvatar,
  verifyCurrentPassword,
} from "./api";
import { useAuthSession } from "./AuthUserContext";

type Props = {
  /** 页面跳转。 */
  navigate: (path: string) => void | Promise<void>;
};

/**
 * 顶栏头像菜单：改昵称 / 头像 / 密码；管理员可进用户管理。
 */
export function UserAvatarMenu({ navigate }: Props) {
  const { user, setUser } = useAuthSession();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const [displayNameDraft, setDisplayNameDraft] = useState(user?.displayName || "");
  const [profileMessage, setProfileMessage] = useState("");
  const [profileError, setProfileError] = useState("");
  const [profileSaving, setProfileSaving] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [currentPasswordError, setCurrentPasswordError] = useState("");
  const [currentPasswordChecking, setCurrentPasswordChecking] = useState(false);
  const [passwordSubmitting, setPasswordSubmitting] = useState(false);

  useEffect(() => {
    setDisplayNameDraft(user?.displayName || "");
  }, [user?.displayName]);

  useEffect(() => {
    if (!open) {
      return;
    }
    function onDocumentClick(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocumentClick);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onDocumentClick);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  if (!user) {
    return null;
  }

  const avatarSrc = user.avatarUrl ? withAuthQuery(user.avatarUrl) : null;
  const initial = (user.displayName || user.username || "?").slice(0, 1).toUpperCase();
  const manageUsers = canManageUsers(user);

  async function onSaveDisplayName(event: FormEvent) {
    event.preventDefault();
    setProfileError("");
    setProfileMessage("");
    const nextName = displayNameDraft.trim();
    if (!nextName) {
      setProfileError("显示名不能为空");
      return;
    }
    setProfileSaving(true);
    try {
      const updated = await updateProfile(nextName);
      setUser(updated);
      setProfileMessage("显示名已更新");
    } catch (caught) {
      setProfileError(caught instanceof Error ? caught.message : "更新失败");
    } finally {
      setProfileSaving(false);
    }
  }

  async function onAvatarSelected(file: File | undefined) {
    if (!file) {
      return;
    }
    setProfileError("");
    setProfileMessage("");
    if (file.size > 2 * 1024 * 1024) {
      setProfileError("头像不能超过 2MB");
      return;
    }
    setAvatarUploading(true);
    try {
      const updated = await uploadAvatar(file);
      setUser(updated);
      setProfileMessage("头像已更新");
    } catch (caught) {
      setProfileError(caught instanceof Error ? caught.message : "头像上传失败");
    } finally {
      setAvatarUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  }

  async function onCurrentPasswordBlur() {
    const value = currentPassword.trim();
    setCurrentPasswordError("");
    if (!value) {
      return;
    }
    setCurrentPasswordChecking(true);
    try {
      await verifyCurrentPassword(value);
    } catch (caught) {
      setCurrentPasswordError(caught instanceof Error ? caught.message : "当前密码错误");
    } finally {
      setCurrentPasswordChecking(false);
    }
  }

  async function onChangePassword(event: FormEvent) {
    event.preventDefault();
    setPasswordError("");
    if (currentPasswordError) {
      setPasswordError(currentPasswordError);
      return;
    }
    if (newPassword.length < 6) {
      setPasswordError("新密码至少 6 位");
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError("两次输入的新密码不一致");
      return;
    }
    setPasswordSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
    } catch (caught) {
      setPasswordError(caught instanceof Error ? caught.message : "修改密码失败");
    } finally {
      setPasswordSubmitting(false);
    }
  }

  return (
    <div className="user-avatar-menu" ref={rootRef}>
      <button
        type="button"
        className="user-avatar-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label="账户菜单"
        onClick={() => setOpen((previous) => !previous)}
      >
        {avatarSrc ? (
          <img src={avatarSrc} alt="" className="user-avatar-img" />
        ) : (
          <span className="user-avatar-fallback">{initial}</span>
        )}
        <span className="user-avatar-trigger-name">{user.displayName || user.username}</span>
      </button>

      {open ? (
        <div className="user-avatar-popover" role="dialog" aria-label="账户设置">
          <div className="user-avatar-popover-body">
            <div className="user-avatar-popover-header">
              <button
                type="button"
                className="user-avatar-large"
                aria-label="更换头像"
                disabled={avatarUploading}
                onClick={() => fileInputRef.current?.click()}
              >
                {avatarSrc ? (
                  <img src={avatarSrc} alt="" className="user-avatar-img" />
                ) : (
                  <span className="user-avatar-fallback large">{initial}</span>
                )}
                <span className="user-avatar-camera">
                  <Camera size={14} />
                </span>
              </button>
              <strong>{user.displayName || user.username}</strong>
              <span>{user.username}</span>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                hidden
                onChange={(event) => void onAvatarSelected(event.target.files?.[0])}
              />
            </div>

            <form className="user-avatar-section" onSubmit={onSaveDisplayName}>
              <div className="user-avatar-section-title">
                <UserRound size={14} />
                显示名
              </div>
              <div className="user-avatar-inline-row">
                <label className="user-avatar-grow">
                  昵称
                  <input
                    value={displayNameDraft}
                    onChange={(event) => setDisplayNameDraft(event.target.value)}
                    maxLength={128}
                    required
                  />
                </label>
                <button type="submit" className="primary-button" disabled={profileSaving}>
                  {profileSaving ? "…" : "保存"}
                </button>
              </div>
            </form>

            <form className="user-avatar-section" onSubmit={onChangePassword}>
              <div className="user-avatar-section-title">
                <KeyRound size={14} />
                修改密码
              </div>
              <label>
                当前密码
                <input
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={(event) => {
                    setCurrentPassword(event.target.value);
                    setCurrentPasswordError("");
                  }}
                  onBlur={() => {
                    void onCurrentPasswordBlur();
                  }}
                  aria-invalid={Boolean(currentPasswordError)}
                  required
                />
              </label>
              {currentPasswordChecking ? (
                <p className="user-avatar-ok">正在校验当前密码…</p>
              ) : null}
              {currentPasswordError ? (
                <p className="user-avatar-error">{currentPasswordError}</p>
              ) : null}
              <label>
                新密码
                <input
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  required
                />
              </label>
              <label>
                确认新密码
                <input
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  required
                />
              </label>
              {passwordError ? <p className="user-avatar-error">{passwordError}</p> : null}
              <button type="submit" className="primary-button user-avatar-submit" disabled={passwordSubmitting}>
                {passwordSubmitting ? "提交中…" : "修改密码"}
              </button>
            </form>

            {profileError ? <p className="user-avatar-error">{profileError}</p> : null}
            {profileMessage ? <p className="user-avatar-ok">{profileMessage}</p> : null}
          </div>

          <div className="user-avatar-footer">
            {manageUsers ? (
              <button
                type="button"
                className="ghost-button"
                onClick={() => {
                  setOpen(false);
                  navigate("/settings?section=users");
                }}
              >
                <Users size={14} />
                用户管理
              </button>
            ) : null}
            <button
              type="button"
              className="ghost-button"
              onClick={() => {
                void logout();
              }}
            >
              <LogOut size={14} />
              退出
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
