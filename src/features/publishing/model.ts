/**
 * 为 srcDoc iframe 注入 base，使 /api/images 等相对路径按当前站点解析。
 *
 * @param html 渲染后的正文 HTML 片段或文档
 * @param origin 站点 origin，默认取 window.location.origin
 */
export function withPublishingPreviewBase(
  html: string,
  origin: string = typeof window !== "undefined" ? window.location.origin : "",
): string {
  const body = String(html || "").trim() || "<p></p>";
  const base = String(origin || "").replace(/\/$/, "");
  if (!base) {
    return body;
  }
  if (/<base\b/i.test(body)) {
    return body;
  }
  if (/<html[\s>]/i.test(body)) {
    if (/<head[\s>]/i.test(body)) {
      return body.replace(/<head([^>]*)>/i, `<head$1><base href="${base}/">`);
    }
    return body.replace(/<html([^>]*)>/i, `<html$1><head><base href="${base}/"></head>`);
  }
  return `<!DOCTYPE html><html><head><meta charset="utf-8"><base href="${base}/"></head><body>${body}</body></html>`;
}

/** Copy rich HTML with a legacy selection fallback for restricted browsers. */
export async function copyPublishingHtml(html: string, plainText: string): Promise<"clipboard" | "legacy"> {
  if (navigator.clipboard?.write && typeof ClipboardItem !== "undefined") {
    await navigator.clipboard.write([
      new ClipboardItem({
        "text/html": new Blob([html], { type: "text/html" }),
        "text/plain": new Blob([plainText], { type: "text/plain" }),
      }),
    ]);
    return "clipboard";
  }

  const container = document.createElement("div");
  container.contentEditable = "true";
  container.style.position = "fixed";
  container.style.left = "-10000px";
  container.innerHTML = html;
  document.body.appendChild(container);
  const selection = window.getSelection();
  const range = document.createRange();
  range.selectNodeContents(container);
  selection?.removeAllRanges();
  selection?.addRange(range);
  const copied = document.execCommand("copy");
  selection?.removeAllRanges();
  container.remove();
  if (!copied) throw new Error("浏览器拒绝了复制操作，请使用下载 HTML");
  return "legacy";
}

/** Download a self-contained publication HTML file without sending content elsewhere. */
export function downloadPublishingHtml(html: string, filename: string): void {
  const safeName = filename.replace(/[\\/:*?"<>|\u0000-\u001f]/g, "-").slice(0, 120);
  const url = URL.createObjectURL(new Blob([html], { type: "text/html;charset=utf-8" }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `${safeName || "公众号文章"}.html`;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}
