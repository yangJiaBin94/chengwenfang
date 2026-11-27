// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  AUTH_SESSION_EXPIRED_EVENT,
  AUTH_TOKEN_STORAGE_KEY,
  invalidateAuthSession,
} from "./http";

describe("invalidateAuthSession", () => {
  afterEach(() => {
    localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  });

  it("clears token and dispatches session-expired event", () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "stale-token");
    const onExpired = vi.fn();
    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, onExpired);

    invalidateAuthSession();

    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull();
    expect(onExpired).toHaveBeenCalledTimes(1);
    window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, onExpired);
  });
});
