import { useCallback, useEffect, useRef, useState } from "react";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../api/pagination";

export type PagedListResult<TItem> = {
  items: TItem[];
  total: number;
  /** 后端可能夹页后返回的实际页码。 */
  page: number;
};

export type UsePagedListOptions<TItem> = {
  /** 初始每页条数；默认 5。 */
  initialPageSize?: ListPageSize;
  /** 搜索防抖毫秒；默认 300。 */
  debounceMs?: number;
  /** 拉取一页；keyword 为防抖后的检索词。 */
  loadPage: (args: {
    page: number;
    pageSize: ListPageSize;
    keyword: string;
  }) => Promise<PagedListResult<TItem>>;
  /**
   * 变化时：清空检索词可选、重置到第 1 页并重新加载。
   * 典型：themeId / topicId。
   */
  resetKeys?: readonly unknown[];
  /** 为 false 时不自动加载；默认 true。 */
  enabled?: boolean;
  /** 加载失败回调（默认清空列表）。 */
  onError?: (error: unknown) => void;
  /** resetKeys 变化时是否清空 query；默认 false。 */
  clearQueryOnReset?: boolean;
};

/**
 * 后端分页列表的通用状态：页码、每页条数、搜索防抖、点击外部关闭条数菜单。
 * 各页面的筛选条件通过闭包传入 loadPage，并用 resetKeys 触发重置。
 */
export function usePagedList<TItem>(options: UsePagedListOptions<TItem>) {
  const {
    initialPageSize = 5,
    debounceMs = 300,
    loadPage,
    resetKeys = [],
    enabled = true,
    onError,
    clearQueryOnReset = false,
  } = options;

  const [items, setItems] = useState<TItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [listPage, setListPage] = useState(1);
  const [pageSize, setPageSize] = useState<ListPageSize>(initialPageSize);
  const [pageSizeOpen, setPageSizeOpen] = useState(false);
  const pageSizeRef = useRef<HTMLDivElement | null>(null);

  const loadPageRef = useRef(loadPage);
  loadPageRef.current = loadPage;
  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;

  // 点击外部 / Escape 关闭每页条数菜单
  useEffect(() => {
    if (!pageSizeOpen) return;
    function onPointerDown(event: MouseEvent) {
      if (!pageSizeRef.current?.contains(event.target as Node)) {
        setPageSizeOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setPageSizeOpen(false);
    }
    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [pageSizeOpen]);

  // 搜索输入防抖后再打后端，并回到第一页
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setListPage(1);
    }, debounceMs);
    return () => window.clearTimeout(timer);
  }, [query, debounceMs]);

  // resetKeys 变化：可选清空检索，回到第一页
  useEffect(() => {
    if (clearQueryOnReset) {
      setQuery("");
      setDebouncedQuery("");
    }
    setListPage(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅跟随 resetKeys
  }, resetKeys);

  // 分页参数变化时加载当前页
  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const result = await loadPageRef.current({
          page: listPage,
          pageSize,
          keyword: debouncedQuery,
        });
        if (cancelled) return;
        setItems(result.items);
        setTotal(result.total);
        if (result.page !== listPage) {
          setListPage(result.page);
        }
      } catch (error) {
        if (!cancelled) {
          setItems([]);
          setTotal(0);
          onErrorRef.current?.(error);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- resetKeys 参与重载
  }, [enabled, listPage, pageSize, debouncedQuery, ...resetKeys]);

  const listPageCount = Math.max(1, Math.ceil(total / pageSize));
  const pageNumbers = listPageNumbers(listPage, listPageCount);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      const result = await loadPageRef.current({
        page: listPage,
        pageSize,
        keyword: debouncedQuery,
      });
      setItems(result.items);
      setTotal(result.total);
      if (result.page !== listPage) {
        setListPage(result.page);
      }
      return result;
    } catch (error) {
      setItems([]);
      setTotal(0);
      onErrorRef.current?.(error);
      return null;
    } finally {
      setLoading(false);
    }
  }, [listPage, pageSize, debouncedQuery]);

  return {
    items,
    setItems,
    total,
    setTotal,
    loading,
    setLoading,
    query,
    setQuery,
    debouncedQuery,
    listPage,
    setListPage,
    pageSize,
    setPageSize,
    pageSizeOpen,
    setPageSizeOpen,
    pageSizeRef,
    listPageCount,
    pageNumbers,
    reload,
    pageSizeOptions: LIST_PAGE_SIZE_OPTIONS,
  };
}
