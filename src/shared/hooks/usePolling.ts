import { useEffect, useRef } from "react";

export type UsePollingOptions = {
  /** 为 false 时不启动轮询；默认 true。 */
  enabled?: boolean;
  /** 窗口 focus 时额外触发一次；默认 false。 */
  listenFocus?: boolean;
};

/**
 * 固定间隔轮询：挂载或依赖变化时立即执行一次，再按 intervalMs 重复。
 * 间隔值由调用方传入，保持各页面原值不变。
 *
 * @param callback 每次轮询执行的函数（内部自行 try/catch）
 * @param intervalMs 间隔毫秒
 * @param deps 变化时重启轮询（等同 useEffect 依赖）
 * @param options enabled / listenFocus
 */
export function usePolling(
  callback: () => void | Promise<void>,
  intervalMs: number,
  deps: readonly unknown[],
  options: UsePollingOptions = {},
) {
  const { enabled = true, listenFocus = false } = options;
  const callbackRef = useRef(callback);
  callbackRef.current = callback;

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;

    async function tick() {
      if (cancelled) return;
      await callbackRef.current();
    }

    void tick();
    const timer = window.setInterval(() => void tick(), intervalMs);
    const onFocus = listenFocus
      ? () => {
          void tick();
        }
      : null;
    if (onFocus) {
      window.addEventListener("focus", onFocus);
    }
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      if (onFocus) {
        window.removeEventListener("focus", onFocus);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- deps 由调用方显式传入
  }, [intervalMs, enabled, listenFocus, ...deps]);
}
