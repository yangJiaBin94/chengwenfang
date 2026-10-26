/** @vitest-environment jsdom */
import { describe, expect, it, vi } from "vitest";
import {
  navigateWithUnsavedGuard,
  UNSAVED_PUBLISHING_LEAVE_MESSAGE,
  UNSAVED_PUBLISHING_LEAVE_TITLE,
} from "./useRouter";

describe("navigateWithUnsavedGuard", () => {
  it("pushes path when no confirm needed", async () => {
    const pushState = vi.spyOn(window.history, "pushState").mockImplementation(() => {});
    const scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    const dispatch = vi.spyOn(window, "dispatchEvent");

    const clearDirty = vi.fn();
    const ok = await navigateWithUnsavedGuard("/settings", {
      needsConfirm: false,
      confirmLeave: async () => false,
      clearDirty,
    });

    expect(ok).toBe(true);
    expect(clearDirty).not.toHaveBeenCalled();
    expect(pushState).toHaveBeenCalledWith({}, "", "/settings");
    expect(scrollTo).toHaveBeenCalledWith(0, 0);
    expect(dispatch).toHaveBeenCalled();

    pushState.mockRestore();
    scrollTo.mockRestore();
    dispatch.mockRestore();
  });

  it("cancels when confirm returns false", async () => {
    const pushState = vi.spyOn(window.history, "pushState").mockImplementation(() => {});
    const clearDirty = vi.fn();
    const ok = await navigateWithUnsavedGuard("/settings", {
      needsConfirm: true,
      confirmLeave: async () => false,
      clearDirty,
    });
    expect(ok).toBe(false);
    expect(clearDirty).not.toHaveBeenCalled();
    expect(pushState).not.toHaveBeenCalled();
    pushState.mockRestore();
  });

  it("clears dirty and navigates when confirm succeeds", async () => {
    const pushState = vi.spyOn(window.history, "pushState").mockImplementation(() => {});
    const scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    const clearDirty = vi.fn();
    const ok = await navigateWithUnsavedGuard("/", {
      needsConfirm: true,
      confirmLeave: async () => true,
      clearDirty,
    });
    expect(ok).toBe(true);
    expect(clearDirty).toHaveBeenCalled();
    expect(pushState).toHaveBeenCalledWith({}, "", "/");
    pushState.mockRestore();
    scrollTo.mockRestore();
  });

  it("keeps unsaved leave copy", () => {
    expect(UNSAVED_PUBLISHING_LEAVE_TITLE).toBe("离开公众号排版？");
    expect(UNSAVED_PUBLISHING_LEAVE_MESSAGE).toBe(
      "文章内容有未保存修改。离开后这些修改会丢失。",
    );
  });
});
