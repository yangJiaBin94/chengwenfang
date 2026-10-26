import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useRef } from "react";
import { Camera, Copy, Monitor, Send, Smartphone } from "lucide-react";
import {
  listLayoutThemes,
  loadArticleLayout,
  renderPublishingMarkdown,
  pushWechatMpDraft,
} from "../api";
import type { ArticleLayoutData, PublishingRenderData, LayoutThemeData } from "../api";
import { copyPublishingHtml, withPublishingPreviewBase } from "../model";

type Props = {
  articleId: string | null;
  /** 统一文档 ID（公众号轨）；缺省时回退 articleId（课程 1:1）。 */
  documentId?: string | null;
  articleVersionNo: number;
  initialMarkdown: string;
  onDirtyChange: (dirty: boolean) => void;
  /** 项目统一 AppDialog 通知（推送结果等）。 */
  showNotice: (title: string, message: string) => void;
  /** 可选：生成发布快照（热点模式使用）。 */
  onCreateSnapshot?: (themeVersionId: string) => Promise<void> | void;
  /** 可选：快照历史等附加区域。 */
  historySlot?: ReactNode;
};

/** Native public-account editor. Draft state is local until an explicit save. */
export function PublishingWorkspace({
  articleId,
  documentId,
  articleVersionNo,
  initialMarkdown,
  onDirtyChange,
  showNotice,
  onCreateSnapshot,
  historySlot,
}: Props) {
  const [markdown, setMarkdown] = useState(initialMarkdown);
  const [savedMarkdown, setSavedMarkdown] = useState(initialMarkdown);
  const [themes, setThemes] = useState<LayoutThemeData[]>([]);
  const [layout, setLayout] = useState<ArticleLayoutData | null>(null);
  const [rendered, setRendered] = useState<PublishingRenderData | null>(null);
  const [device, setDevice] = useState<"desktop" | "mobile">("desktop");
  const [message, setMessage] = useState("");
  const [editorWidth, setEditorWidth] = useState(46);
  const [snapshotBusy, setSnapshotBusy] = useState(false);
  const [pushBusy, setPushBusy] = useState(false);
  /** 本会话内是否已成功推送到公众号草稿箱（切文章/版本后重置）。 */
  const [pushedToWechat, setPushedToWechat] = useState(false);
  const bodyRef = useRef<HTMLDivElement | null>(null);
  // Historical layouts may point to a disabled theme. Only use a layout theme
  // when it is currently available; otherwise fall back to the sole active theme.
  const themeVersionId = themes.some((item) => item.currentVersionId === layout?.themeVersionId)
    ? layout?.themeVersionId
    : themes[0]?.currentVersionId;

  useEffect(() => {
    setMarkdown(initialMarkdown);
    setSavedMarkdown(initialMarkdown);
    setPushedToWechat(false);
  }, [initialMarkdown, articleId, articleVersionNo]);
  useEffect(() => {
    let cancelled = false;
    listLayoutThemes()
      .then((items) => {
        if (!cancelled) setThemes(items.filter((item) => item.status === "enabled"));
      })
      .catch((error) =>
        setMessage(error instanceof Error ? error.message : "主题加载失败"),
      );
    if (!articleId) return () => { cancelled = true; };
    // 有明确 documentId 才传；勿用 articleId 兜底（热点 wechat 文档 id ≠ articleId）
    loadArticleLayout(articleId, documentId || undefined)
      .then((item) => {
        if (!cancelled) setLayout(item);
      })
      .catch(() => undefined);
    return () => { cancelled = true; };
  }, [articleId, documentId]);
  useEffect(() => {
    if (!markdown.trim() || !themeVersionId) { setRendered(null); return; }
    const timer = window.setTimeout(
      () =>
        renderPublishingMarkdown(markdown, themeVersionId)
          .then(setRendered)
          .catch((error) =>
            setMessage(error instanceof Error ? error.message : "预览失败"),
          ),
      260,
    );
    return () => window.clearTimeout(timer);
  }, [markdown, themeVersionId]);

  const dirty = markdown !== savedMarkdown;
  const blocking = rendered?.diagnostics.some((item) => item.blocking === true) ?? false;
  const previewStyle = useMemo(() => ({ width: device === "mobile" ? 375 : "100%", maxWidth: "100%" }), [device]);
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false); }, [dirty, onDirtyChange]);
  // 推送后又改稿，去掉「已推送」以免误导
  useEffect(() => {
    if (dirty && pushedToWechat) setPushedToWechat(false);
  }, [dirty, pushedToWechat]);
  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => { if (dirty) event.preventDefault(); };
    window.addEventListener("beforeunload", guard);
    return () => window.removeEventListener("beforeunload", guard);
  }, [dirty]);

  async function copyRichText() {
    if (!rendered) return;
    try {
      const method = await copyPublishingHtml(rendered.html, rendered.plainText);
      setMessage(
        method === "clipboard"
          ? "已复制公众号富文本，可直接粘贴到编辑器"
          : "已使用兼容模式复制富文本",
      );
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "复制失败，请检查浏览器权限");
    }
  }

  async function createSnapshot() {
    if (!onCreateSnapshot || !themeVersionId || snapshotBusy) return;
    setSnapshotBusy(true);
    try {
      await onCreateSnapshot(themeVersionId);
      setMessage("发布快照已生成");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "生成快照失败");
    } finally {
      setSnapshotBusy(false);
    }
  }

  /** 将当前 Markdown 按所选主题推送到公众号草稿箱；同文复推覆盖已绑定草稿。 */
  async function pushToWechatDraft() {
    if (!articleId || !themeVersionId || !markdown.trim() || pushBusy || blocking) return;
    setPushBusy(true);
    try {
      // 有明确 documentId 才传；勿用 articleId 兜底（热点 wechat 文档 id ≠ articleId）
      const result = await pushWechatMpDraft({
        articleId,
        ...(documentId ? { documentId } : {}),
        markdown,
        themeVersionId,
      });
      setPushedToWechat(true);
      const action = result.updatedExisting ? "已更新" : "已新建";
      showNotice(
        "推送成功",
        `${action}公众号草稿「${result.title}」\n图片 ${result.uploadedImageCount} 张\n请到公众平台草稿箱确认发布。`,
      );
    } catch (error) {
      showNotice(
        "推送失败",
        error instanceof Error ? error.message : "推送草稿箱失败",
      );
    } finally {
      setPushBusy(false);
    }
  }
  function startResize(event: React.PointerEvent<HTMLButtonElement>) {
    const body = bodyRef.current;
    if (!body || window.innerWidth <= 1050) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    const bounds = body.getBoundingClientRect();
    const move = (moveEvent: PointerEvent) => setEditorWidth(Math.min(68, Math.max(32, ((moveEvent.clientX - bounds.left) / bounds.width) * 100)));
    const finish = () => { window.removeEventListener("pointermove", move); window.removeEventListener("pointerup", finish); };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", finish);
  }

  if (!articleId) return <div className="publishing-empty">当前课程还没有公众号文章，生成文章后即可进行原生排版。</div>;
  return (
    <>
      <div className="publishing-workspace">
        <header className="publishing-toolbar">
          <div>
            <span className="section-kicker">公众号排版</span>
            <h3>当前文章 V{articleVersionNo}</h3>
            <span className={dirty ? "publishing-dirty" : "publishing-saved"}>
              {dirty ? "有未保存修改" : "已保存"}
            </span>
            {pushedToWechat && (
              <span className="publishing-pushed" title="本会话已成功推送到公众号草稿箱">
                已推送公众号
              </span>
            )}
            {pushBusy && (
              <span className="publishing-pushing" role="status">
                正在推送…
              </span>
            )}
          </div>
          <div className="publishing-controls">
            <label>
              主题
              <select
                value={themeVersionId || ""}
                onChange={(event) =>
                  setLayout((current) => ({
                    ...(current || {
                      id: "",
                      articleId,
                      articleVersionNo,
                      options: {},
                      updatedAt: "",
                    }),
                    themeVersionId: event.target.value,
                  }))
                }
              >
                {themes.map((theme) => (
                  <option key={theme.currentVersionId} value={theme.currentVersionId}>
                    {theme.name}
                  </option>
                ))}
              </select>
            </label>
            <button
              className={device === "desktop" ? "tool-button active" : "tool-button"}
              onClick={() => setDevice("desktop")}
              title="桌面预览"
            >
              <Monitor size={15} />
            </button>
            <button
              className={device === "mobile" ? "tool-button active" : "tool-button"}
              onClick={() => setDevice("mobile")}
              title="手机预览"
            >
              <Smartphone size={15} />
            </button>
            <button
              className="primary-button"
              disabled={!rendered || blocking}
              onClick={() => void copyRichText()}
            >
              <Copy size={15} />
              复制富文本
            </button>
            <button
              className="secondary-button"
              disabled={!themeVersionId || !rendered || blocking || pushBusy}
              onClick={() => void pushToWechatDraft()}
              title="推送到公众平台草稿箱（需先在设置页配置 AppID / AppSecret）"
            >
              <Send size={15} />
              {pushBusy ? "推送中..." : "推送到公众号草稿箱"}
            </button>
            {onCreateSnapshot && (
              <button
                className="secondary-button"
                disabled={!themeVersionId || !rendered || snapshotBusy}
                onClick={() => void createSnapshot()}
              >
                <Camera size={15} />
                {snapshotBusy ? "生成中..." : "生成发布快照"}
              </button>
            )}
          </div>
        </header>
        {message && (
          <div className="publishing-message" role="status">
            {message}
          </div>
        )}
        <div
          className="publishing-body"
          ref={bodyRef}
          style={{ gridTemplateColumns: `${editorWidth}fr 8px ${100 - editorWidth}fr` }}
        >
          <section className="publishing-editor-pane">
            <div className="publishing-pane-title">Markdown 编辑</div>
            <textarea
              value={markdown}
              onChange={(event) => setMarkdown(event.target.value)}
              spellCheck={false}
            />
          </section>
          <button
            className="publishing-resizer"
            aria-label="调整编辑与预览宽度"
            title="拖动调整宽度"
            onPointerDown={startResize}
          />
          <section className="publishing-preview-pane">
            <div className="publishing-pane-title">公众号预览</div>
            <div className="publishing-preview-canvas">
              <iframe
                title="公众号文章预览"
                style={previewStyle}
                srcDoc={withPublishingPreviewBase(
                  rendered?.html || "<p>开始输入 Markdown 以查看预览</p>",
                )}
              />
            </div>
          </section>
        </div>
        {rendered && rendered.diagnostics.length > 0 && (
          <div className="publishing-diagnostics">
            <strong>格式检查</strong>
            {rendered.diagnostics.map((item, index) => (
              <span
                key={index}
                className={item.blocking ? "diagnostic-error" : "diagnostic-warning"}
              >
                {String(item.message || item.code || "提示")}
              </span>
            ))}
          </div>
        )}
      </div>
      {historySlot}
    </>
  );
}
