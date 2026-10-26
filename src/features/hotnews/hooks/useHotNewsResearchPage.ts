// @ts-nocheck
import { useEffect, useRef, useState } from "react";
import { loadJob } from "../../../shared/api/jobs";
import {
  addHotNewsManualResearchItem,
  buildHotNewsArticleSkeleton,
  ensureHotNewsArticle,
  getHotNewsArticleSkeleton,
  listHotNewsArticles,
  listHotNewsBriefs,
  listHotNewsResearchConflicts,
  listHotNewsResearchRuns,
  pageHotNewsResearchItems,
  startHotNewsTopicResearch,
  withDocumentIdQuery,
  type HotNewsArticle,
  type HotNewsArticleSkeleton,
  type HotNewsBrief,
  type HotNewsResearchItemPage,
} from "../api";
import { type ListPageSize } from "../../../shared/api/pagination";
import { useHotNewsResearchVideoParse } from "./useHotNewsResearchVideoParse";

/** 热点调研页状态与副作用。 */
export function useHotNewsResearchPage({
  topicId,
  themeSlug,
  showNotice,
  confirmAction,
  navigate,
}: {
  topicId: string;
  themeSlug: string;
  showNotice: (title: string, message: string) => void;
  confirmAction: (
    title: string,
    message: string,
    tone?: "default" | "danger",
  ) => Promise<boolean>;
  navigate: (path: string) => void;
}) {
  const selectedPath = `/modes/hot-news/themes/${themeSlug}/selected`;
  /** 当前页资料（后端分页）。 */
  const [items, setItems] = useState<Array<Record<string, unknown>>>([]);
  /** 当前筛选条件下的总条数。 */
  const [total, setTotal] = useState(0);
  /** 选题下资料总数（不受列表筛选影响）。 */
  const [itemCount, setItemCount] = useState(0);
  const [articleCount, setArticleCount] = useState(0);
  const [videoCount, setVideoCount] = useState(0);
  const [pendingVideoCount, setPendingVideoCount] = useState(0);
  const [listLoading, setListLoading] = useState(false);
  const [runs, setRuns] = useState<Array<Record<string, unknown>>>([]);
  const [conflicts, setConflicts] = useState<Array<Record<string, unknown>>>(
    [],
  );
  const [skeleton, setSkeleton] = useState<HotNewsArticleSkeleton | null>(null);
  const [brief, setBrief] = useState<HotNewsBrief | null>(null);
  const [skeletonOpen, setSkeletonOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [skeletonBusy, setSkeletonBusy] = useState(false);
  const [articleBusy, setArticleBusy] = useState(false);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [mediaFilter, setMediaFilter] = useState<"all" | "article" | "video">(
    "all",
  );
  const [listPage, setListPage] = useState(1);
  const [pageSize, setPageSize] = useState<ListPageSize>(5);
  const [pageSizeOpen, setPageSizeOpen] = useState(false);
  const pageSizeRef = useRef<HTMLDivElement | null>(null);
  const [, setArticles] = useState<HotNewsArticle[]>([]);
  /** 来自 Job 的真实进度（research-runs 本身不含 progress）。 */
  const [jobProgress, setJobProgress] = useState(0);
  const [jobStep, setJobStep] = useState("");
  const [resultItem, setResultItem] = useState<Record<string, unknown> | null>(
    null,
  );
  const [manualOpen, setManualOpen] = useState(false);
  const [manualUrl, setManualUrl] = useState("");
  const [manualBusy, setManualBusy] = useState(false);

  /** 把分页接口结果写入列表与选题级统计。 */
  function applyItemPage(result: HotNewsResearchItemPage) {
    setItems(result.items);
    setTotal(result.total);
    setItemCount(result.itemCount);
    setArticleCount(result.articleCount);
    setVideoCount(result.videoCount);
    setPendingVideoCount(result.pendingVideoCount);
    if (result.page !== listPage) {
      setListPage(result.page);
    }
  }

  /** 拉取当前页资料（后端 LIMIT/OFFSET）。 */
  async function loadItemPage(page = listPage) {
    setListLoading(true);
    try {
      const result = await pageHotNewsResearchItems(topicId, {
        page,
        pageSize,
        keyword: debouncedQuery,
        media: mediaFilter,
      });
      applyItemPage(result);
      return result;
    } catch (error) {
      showNotice(
        "资料列表加载失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
      return null;
    } finally {
      setListLoading(false);
    }
  }

  /** 刷新运行态、冲突、骨架与简报（不含资料列表）。 */
  async function refreshMeta() {
    const [nextRuns, nextConflicts, nextArticles, nextSkeleton, nextBriefs] =
      await Promise.all([
        listHotNewsResearchRuns(topicId),
        listHotNewsResearchConflicts(topicId),
        listHotNewsArticles(topicId),
        getHotNewsArticleSkeleton(topicId),
        listHotNewsBriefs(topicId),
      ]);
    setRuns(nextRuns);
    setConflicts(nextConflicts);
    setArticles(nextArticles);
    setSkeleton(nextSkeleton);
    setBrief(nextBriefs[0] ?? null);
  }

  /** 刷新资料当前页与周边元数据。 */
  async function refresh() {
    try {
      const [, itemPage] = await Promise.all([refreshMeta(), loadItemPage()]);
      return itemPage;
    } catch (error) {
      showNotice(
        "资料池加载失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
      return null;
    }
  }

  const {
    videoParseBusy,
    videoParseItemId,
    videoParseMessage,
    videoParseMeta,
    videoParseProgress,
    videoParseScope,
    videoParseStep,
    startBatchVideoParse,
    startItemVideoParse,
    friendlyParseError,
  } = useHotNewsResearchVideoParse({
    topicId,
    pendingVideoCount,
    items,
    setItems,
    refresh,
    showNotice,
  });


  // 搜索输入防抖后再打后端，并回到第一页
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setListPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  // 切换选题时重置筛选与页码
  useEffect(() => {
    setQuery("");
    setDebouncedQuery("");
    setMediaFilter("all");
    setListPage(1);
  }, [topicId]);

  // 形态/每页条数变化时回到第一页
  useEffect(() => {
    setListPage(1);
  }, [mediaFilter, pageSize]);

  // 分页参数变化时加载当前页
  useEffect(() => {
    let cancelled = false;
    (async () => {
      setListLoading(true);
      try {
        const result = await pageHotNewsResearchItems(topicId, {
          page: listPage,
          pageSize,
          keyword: debouncedQuery,
          media: mediaFilter,
        });
        if (cancelled) return;
        setItems(result.items);
        setTotal(result.total);
        setItemCount(result.itemCount);
        setArticleCount(result.articleCount);
        setVideoCount(result.videoCount);
        setPendingVideoCount(result.pendingVideoCount);
        if (result.page !== listPage) {
          setListPage(result.page);
        }
      } catch (error) {
        if (!cancelled) {
          showNotice(
            "资料列表加载失败",
            error instanceof Error ? error.message : "请稍后重试",
          );
        }
      } finally {
        if (!cancelled) setListLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [topicId, listPage, pageSize, debouncedQuery, mediaFilter, showNotice]);

  // 选题切换时加载运行态等元数据
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [nextRuns, nextConflicts, nextArticles, nextSkeleton, nextBriefs] =
          await Promise.all([
            listHotNewsResearchRuns(topicId),
            listHotNewsResearchConflicts(topicId),
            listHotNewsArticles(topicId),
            getHotNewsArticleSkeleton(topicId),
            listHotNewsBriefs(topicId),
          ]);
        if (cancelled) return;
        setRuns(nextRuns);
        setConflicts(nextConflicts);
        setArticles(nextArticles);
        setSkeleton(nextSkeleton);
        setBrief(nextBriefs[0] ?? null);
      } catch (error) {
        if (!cancelled) {
          showNotice(
            "资料池加载失败",
            error instanceof Error ? error.message : "请稍后重试",
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [topicId, showNotice]);

  const latest = runs[0];
  const latestStatus = String(latest?.status || "");
  const previousStatus = useRef("");
  useEffect(() => {
    if (!new Set(["pending", "running"]).has(latestStatus)) return;
    const timer = window.setInterval(() => void refresh(), 2_000);
    return () => window.clearInterval(timer);
  }, [latestStatus, topicId]);

  // 轮询调研 Job 进度
  useEffect(() => {
    const jobId = latest?.jobId ? String(latest.jobId) : "";
    const running = ["pending", "running"].includes(latestStatus);
    if (!running || !jobId) {
      if (!running) {
        setJobProgress(0);
        setJobStep("");
      }
      return;
    }
    let cancelled = false;
    async function poll() {
      try {
        const job = await loadJob(jobId);
        if (cancelled) return;
        setJobProgress(Number(job.progress || 0));
        setJobStep(String(job.currentStep || ""));
      } catch {
        /* keep last */
      }
    }
    void poll();
    const timer = window.setInterval(() => void poll(), 2_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [latest?.jobId, latestStatus]);

  useEffect(() => {
    const wasActive = ["pending", "running"].includes(previousStatus.current);
    if (wasActive && ["succeeded", "partial", "failed", "cancelled"].includes(latestStatus)) {
      void (async () => {
        const page = await refresh();
        if (latestStatus === "succeeded" || latestStatus === "partial") {
          showNotice(
            latestStatus === "partial" ? "资料补充部分完成" : "资料补充已完成",
            `文章 ${page?.articleCount ?? articleCount} 条，视频 ${page?.videoCount ?? videoCount} 条。视频需解析后才可作为成文参考。`,
          );
        }
      })();
    }
    previousStatus.current = latestStatus;
  }, [latestStatus, showNotice]);


  async function research() {
    // 视频解析进行中不可再启补充资料，避免与解析任务抢资源、覆盖素材
    if (videoParseBusy) {
      showNotice("请稍候", "视频解析进行中，完成后可再补充资料与核验。");
      return;
    }
    // 资料池已有素材时二次确认，避免误触覆盖自动检索结果
    if (itemCount > 0) {
      const confirmed = await confirmAction(
        "重新补充资料？",
        `当前资料池已有 ${itemCount} 条素材。继续将重新检索并替换自动补充的资料（手动添加的素材会保留）。`,
        "danger",
      );
      if (!confirmed) return;
    }
    setBusy(true);
    try {
      await startHotNewsTopicResearch(topicId);
      await refresh();
      showNotice("已开始补充资料", "完成后可在下方解析视频素材。");
    } catch (error) {
      showNotice(
        "启动失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    } finally {
      setBusy(false);
    }
  }

  async function buildSkeleton() {
    setSkeletonBusy(true);
    try {
      const next = await buildHotNewsArticleSkeleton(topicId);
      setSkeleton(next);
      setSkeletonOpen(true);
      // 后端已按骨架同步简报，刷新页面简报区
      await refresh();
      showNotice("文章骨架已拟定", next.workingTitle || "可查看范围与结构后进入工作区。");
    } catch (error) {
      showNotice(
        "拟定骨架失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    } finally {
      setSkeletonBusy(false);
    }
  }

  /**
   * 进入文章工作区；资料/骨架未就绪时友好确认，仍允许强行进入。
   */
  async function enterWorkspace() {
    const hasSkeleton = Boolean(skeleton?.coreThesis);
    const missing: string[] = [];
    if (active) {
      missing.push(
        indeterminate
          ? "资料补充还在进行中"
          : `资料补充还在进行中（${progress}%）`,
      );
    } else if (itemCount <= 0) {
      missing.push("资料池还没有素材");
    }
    if (!hasSkeleton) {
      missing.push("尚未拟定文章骨架");
    }
    if (missing.length > 0) {
      const tip =
        missing.length === 1
          ? `${missing[0]}。工作区生成会缺少依据，建议先按页顶流程补齐。`
          : `${missing.join("，")}。建议先补充资料并拟定骨架，再进工作区按工作流生成。`;
      const confirmed = await confirmAction(
        "现在进入工作区？",
        `${tip}\n\n也可以先进入看看；生成前仍可回到资料池补齐。`,
      );
      if (!confirmed) return;
    }
    setArticleBusy(true);
    try {
      const article = await ensureHotNewsArticle(topicId);
      navigate(
        withDocumentIdQuery(
          `/modes/hot-news/topics/${encodeURIComponent(topicId)}/articles/${encodeURIComponent(article.id)}`,
          article.documentId,
        ),
      );
    } catch (error) {
      showNotice(
        "进入工作区失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    } finally {
      setArticleBusy(false);
    }
  }

  async function submitManualItem() {
    const url = manualUrl.trim();
    if (!url) {
      showNotice("请输入链接", "粘贴文章或视频页面地址后点确定。");
      return;
    }
    setManualBusy(true);
    try {
      const created = await addHotNewsManualResearchItem(topicId, url);
      setManualOpen(false);
      setManualUrl("");
      await refresh();
      const site = String(created.siteLabel || "站点");
      const media = String(created.mediaLabel || "资料");
      showNotice(
        "已添加素材",
        `已采集「${String(created.title || url)}」（${site} · ${media}）。视频仍需解析后才可成文引用。`,
      );
    } catch (error) {
      showNotice(
        "添加失败",
        error instanceof Error ? error.message : "请检查链接后重试",
      );
    } finally {
      setManualBusy(false);
    }
  }


  const statusLabels: Record<string, string> = {
    pending: "排队中",
    running: "执行中",
    succeeded: "已完成",
    partial: "部分完成",
    failed: "失败",
    cancelled: "已取消",
  };
  const stepLabels: Record<string, string> = {
    queued: "等待后台任务开始",
    recovered: "任务已恢复，等待重新执行",
    starting: "正在初始化任务",
    research_search: "正在检索相关公开资料",
    research_extract: "正在抓取网页并提取正文",
    research_analyze: "正在进行 AI 评分与冲突分析",
    research_finished: "资料整理完成",
    completed: "资料补充已完成",
  };
  const active = ["pending", "running"].includes(latestStatus);
  const progress = active ? Math.max(0, Math.min(100, jobProgress)) : 0;
  const currentStep = jobStep || (active ? "queued" : "");
  const indeterminate = active && progress <= 0;

  // 点击外部关闭每页条数菜单
  useEffect(() => {
    if (!pageSizeOpen) return;
    function onPointerDown(event: MouseEvent) {
      if (!pageSizeRef.current?.contains(event.target as Node)) {
        setPageSizeOpen(false);
      }
    }
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, [pageSizeOpen]);

  const listPageCount = Math.max(1, Math.ceil(total / pageSize));

  return {
    active,
    articleBusy,
    articleCount,
    brief,
    buildSkeleton,
    busy,
    conflicts,
    currentStep,
    enterWorkspace,
    friendlyParseError,
    indeterminate,
    itemCount,
    items,
    latest,
    latestStatus,
    listLoading,
    listPage,
    listPageCount,
    manualBusy,
    manualOpen,
    manualUrl,
    mediaFilter,
    navigate,
    pageSize,
    pageSizeOpen,
    pageSizeRef,
    pendingVideoCount,
    progress,
    query,
    refresh,
    research,
    resultItem,
    statusLabels,
    stepLabels,
    selectedPath,
    setListPage,
    setManualOpen,
    setManualUrl,
    setMediaFilter,
    setPageSize,
    setPageSizeOpen,
    setQuery,
    setResultItem,
    setSkeletonOpen,
    skeleton,
    skeletonBusy,
    skeletonOpen,
    startBatchVideoParse,
    startItemVideoParse,
    submitManualItem,
    total,
    videoCount,
    videoParseBusy,
    videoParseItemId,
    videoParseMessage,
    videoParseMeta,
    videoParseProgress,
    videoParseScope,
    videoParseStep,
  };
}
