/**
 * pushState / popstate 封装：导航与未保存离开确认（从 App navigate / syncRoute 抽出）。
 */

import {
  useEffect,
  useRef,
  type DependencyList,
} from "react";

/** 公众号排版未保存离开确认的文案（与原 App.navigate 一致）。 */
export const UNSAVED_PUBLISHING_LEAVE_TITLE = "离开公众号排版？";
export const UNSAVED_PUBLISHING_LEAVE_MESSAGE =
  "文章内容有未保存修改。离开后这些修改会丢失。";

/** useRouter 配置。 */
export type UseRouterOptions = {
  /** 根据当前 location 同步应用状态（原 syncRoute）。 */
  syncRoute: () => void;
  /** 与原 useEffect 依赖一致：主题列表等变化时重新同步。 */
  syncDeps: DependencyList;
  /** 当前是否在公众号排版页签。 */
  isMdniceTab: boolean;
  /** 排版内容是否有未保存修改。 */
  isPublishingDirty: boolean;
  /** 确认对话框（原 confirmAction）。 */
  confirmLeave: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  /** 用户确认离开后清除 dirty 标记。 */
  clearPublishingDirty: () => void;
};

/**
 * 执行 pushState 并触发 popstate，使 syncRoute 跑一遍。
 *
 * @param path 目标路径（可含 query）
 */
export function pushPath(path: string): void {
  window.history.pushState({}, "", path);
  window.scrollTo(0, 0);
  window.dispatchEvent(new PopStateEvent("popstate"));
}

/**
 * 带未保存提示的导航；确认失败则不跳转。
 *
 * @param path 目标路径
 * @param guard 未保存守卫状态与回调
 * @return 是否已完成跳转
 */
export async function navigateWithUnsavedGuard(
  path: string,
  guard: {
    needsConfirm: boolean;
    confirmLeave: () => Promise<boolean>;
    clearDirty: () => void;
  },
): Promise<boolean> {
  if (guard.needsConfirm) {
    const leave = await guard.confirmLeave();
    if (!leave) {
      return false;
    }
    guard.clearDirty();
  }
  pushPath(path);
  return true;
}

/**
 * 订阅 popstate 并提供 navigate（保留排版未保存提示）。
 *
 * @param options 同步与守卫配置
 * @return navigate 函数
 */
export function useRouter(options: UseRouterOptions): {
  navigate: (path: string) => Promise<void>;
} {
  const optionsRef = useRef(options);
  optionsRef.current = options;

  useEffect(() => {
    const onPopState = () => {
      optionsRef.current.syncRoute();
    };
    onPopState();
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
    // 依赖与原 App syncRoute effect 对齐
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, options.syncDeps);

  async function navigate(path: string): Promise<void> {
    const current = optionsRef.current;
    await navigateWithUnsavedGuard(path, {
      needsConfirm: current.isMdniceTab && current.isPublishingDirty,
      confirmLeave: () =>
        current.confirmLeave(
          UNSAVED_PUBLISHING_LEAVE_TITLE,
          UNSAVED_PUBLISHING_LEAVE_MESSAGE,
          "danger",
        ),
      clearDirty: current.clearPublishingDirty,
    });
  }

  return { navigate };
}
