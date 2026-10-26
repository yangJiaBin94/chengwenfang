// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  copyPublishingHtml,
  downloadPublishingHtml,
  withPublishingPreviewBase,
} from "./model";

afterEach(() => vi.restoreAllMocks());

describe("withPublishingPreviewBase", () => {
  it("wraps fragments with base href for relative image urls", () => {
    const html = withPublishingPreviewBase(
      '<section><img src="/api/images/a"></section>',
      "http://localhost:5173",
    );
    expect(html).toContain('<base href="http://localhost:5173/">');
    expect(html).toContain('src="/api/images/a"');
  });

  it("injects base into existing html documents", () => {
    const html = withPublishingPreviewBase(
      "<html><head></head><body><p>x</p></body></html>",
      "https://example.com",
    );
    expect(html).toContain('<base href="https://example.com/">');
  });
});

describe("copyPublishingHtml", () => {
  it("writes both rich HTML and plain text MIME entries", async () => {
    const write = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: { write } });
    class MockClipboardItem {
      constructor(public entries: Record<string, Blob>) {}
    }
    vi.stubGlobal("ClipboardItem", MockClipboardItem);

    await expect(copyPublishingHtml("<strong>课程</strong>", "课程")).resolves.toBe("clipboard");
    const item = write.mock.calls[0][0][0] as MockClipboardItem;
    expect(Object.keys(item.entries).sort()).toEqual(["text/html", "text/plain"]);
  });

  it("uses the legacy selection path when ClipboardItem is unavailable", async () => {
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: undefined });
    vi.stubGlobal("ClipboardItem", undefined);
    Object.defineProperty(document, "execCommand", { configurable: true, value: vi.fn(() => true) });
    await expect(copyPublishingHtml("<p>兼容复制</p>", "兼容复制")).resolves.toBe("legacy");
    expect(document.execCommand).toHaveBeenCalledWith("copy");
  });
});

describe("downloadPublishingHtml", () => {
  it("removes invalid filename characters before download", () => {
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:test"), revokeObjectURL: vi.fn() });
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    downloadPublishingHtml("<p>正文</p>", "课程:第2课");
    expect(click).toHaveBeenCalledOnce();
    expect(document.querySelector("a")).toBeNull();
  });
});
