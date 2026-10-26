import { useEffect, useRef, useState } from "react";
import {
  Activity,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  FileText,
  Globe2,
  Maximize2,
  RefreshCcw,
  Rss,
  Search,
  ShieldCheck,
  X,
} from "lucide-react";
import { loadJob, type JobData } from "../../../shared/api/jobs";
import { formatPublishDate } from "../../../shared/format/time";
import { usePolling } from "../../../shared/hooks/usePolling";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";
import {
  listHotNewsRuns,
  listHotNewsSources,
  listHotNewsTopicSources,
  listHotNewsEvidence,
  listHotNewsBriefs,
  listHotNewsArticles,
  listHotNewsRadarCollectRuns,
  pageHotNewsTopics,
  ensureHotNewsArticle,
  startHotNewsFetch,
  updateHotNewsTopicStatus,
  withDocumentIdQuery,
} from "../api";
import type {
  HotNewsArticle,
  HotNewsBrief,
  HotNewsEvidence,
  HotNewsRadarTopicFilter,
  HotNewsRadarTopicSort,
  HotNewsRun,
  HotNewsSource,
  HotNewsTopic,
  HotNewsTopicSource,
} from "../api";
import {
  ChannelType,
  channelTypeShortLabel,
  isAiThemeSeedTopic,
  TOPIC_ORIGIN_LABELS,
  TopicOriginKind,
} from "../model";
import {
  assertWorkflowFeatureAvailable,
  WorkflowFeatureKey,
} from "../../workflow/api";
import { formatRadarCollectOuterStatus } from "./HotNewsRadarCollectExecutionPage";
import {
  formatTopicPublishRange,
  displayHotTopicTitle,
  buildHotTopicGlance,
  evidenceFactBrief,
  topicOriginTags,
  originTagClass,
  editorialJudgement,
  topicWritingDimensions,
  isConfirmedTopic,
  CHANNEL_RUN_STATUS,
  EVIDENCE_STATUS,
} from "../components/topicDisplay";
import { HotNewsPageView } from "../components/HotNewsPageView";


const RUN_STATUS: Record<string, string> = {
  pending: "等待执行",
  running: "采集中",
  succeeded: "采集成功",
  partial: "部分成功",
  failed: "采集失败",
};

/** 簇卡片：单日或时间段；均无则「未知」。 */


export function HotNewsPage({
  themeId,
  themeSlug,
  themeName,
  showNotice,
  navigate,
}: {
  themeId: string;
  themeSlug: string;
  themeName: string;
  showNotice: (title: string, message: string) => void;
  navigate: (path: string) => void;
}) {
  const sourcesPath = `/modes/hot-news/themes/${themeSlug}/sources`;
  const [sources, setSources] = useState<HotNewsSource[]>([]);
  const [runs, setRuns] = useState<HotNewsRun[]>([]);
  const [topics, setTopics] = useState<HotNewsTopic[]>([]);
  const [topicTotal, setTopicTotal] = useState(0);
  const [candidateCount, setCandidateCount] = useState(0);
  const [topicCount, setTopicCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [topicsLoading, setTopicsLoading] = useState(true);
  const [fetching, setFetching] = useState(false);
  const [filter, setFilter] = useState<HotNewsRadarTopicFilter>("today");
  const [sortBy, setSortBy] = useState<HotNewsRadarTopicSort>("writing");
  const [topicQuery, setTopicQuery] = useState("");
  const [debouncedTopicQuery, setDebouncedTopicQuery] = useState("");
  const [listPage, setListPage] = useState(1);
  const [pageSize, setPageSize] = useState<ListPageSize>(5);
  const [pageSizeOpen, setPageSizeOpen] = useState(false);
  const pageSizeRef = useRef<HTMLDivElement | null>(null);
  const [detailTopic, setDetailTopic] = useState<HotNewsTopic | null>(null);
  const [topicSources, setTopicSources] = useState<HotNewsTopicSource[]>([]);
  const [evidence, setEvidence] = useState<HotNewsEvidence[]>([]);
  const [briefs, setBriefs] = useState<HotNewsBrief[]>([]);
  const [articles, setArticles] = useState<HotNewsArticle[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [articleBusy, setArticleBusy] = useState(false);
  const [detailTab, setDetailTab] = useState<"evidence" | "brief" | "article">(
    "evidence",
  );
  /** 进行中的雷达采集 Job（外层进度条）。 */
  const [collectJob, setCollectJob] = useState<JobData | null>(null);
  const [collectMessage, setCollectMessage] = useState("");
  const [collectStageTitle, setCollectStageTitle] = useState("");
  const [collectProgress, setCollectProgress] = useState(0);
  const previousCollectStatus = useRef("");

  const collectRunning = Boolean(
    collectJob &&
      (collectJob.status === "pending" || collectJob.status === "running"),
  );

  async function refreshMeta(silent = false) {
    if (!silent) setLoading(true);
    try {
      const [nextSources, nextRuns] = await Promise.all([
        listHotNewsSources(themeId),
        listHotNewsRuns(themeId),
      ]);
      setSources(nextSources);
      setRuns(nextRuns);
      return { sources: nextSources, runs: nextRuns };
    } catch (error) {
      showNotice(
        "热点资讯加载失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
      return null;
    } finally {
      if (!silent) setLoading(false);
    }
  }

  async function loadTopicsPage(page = listPage, silent = false) {
    if (!silent) setTopicsLoading(true);
    try {
      const result = await pageHotNewsTopics(themeId, {
        page,
        pageSize,
        keyword: debouncedTopicQuery,
        filter,
        sortBy,
      });
      setTopics(result.items);
      setTopicTotal(result.total);
      setCandidateCount(result.candidateCount);
      setTopicCount(result.topicCount);
      // 后端可能夹页码（删除/筛选后末页变空）
      if (result.page !== page) {
        setListPage(result.page);
      }
      return result;
    } catch (error) {
      showNotice(
        "热点选题加载失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
      return null;
    } finally {
      if (!silent) setTopicsLoading(false);
    }
  }

  async function refresh(silent = false) {
    await Promise.all([refreshMeta(silent), loadTopicsPage(listPage, silent)]);
  }

  useEffect(() => {
    void refreshMeta();
  }, [themeId]);

  // 搜索输入防抖后再打后端，并回到第一页
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedTopicQuery(topicQuery.trim());
      setListPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [topicQuery]);

  // 切换主题时回到第一页，避免沿用上一主题页码
  useEffect(() => {
    setListPage(1);
  }, [themeId]);

  // 分页参数变化时请求当前页
  useEffect(() => {
    let cancelled = false;
    (async () => {
      setTopicsLoading(true);
      try {
        const result = await pageHotNewsTopics(themeId, {
          page: listPage,
          pageSize,
          keyword: debouncedTopicQuery,
          filter,
          sortBy,
        });
        if (cancelled) return;
        setTopics(result.items);
        setTopicTotal(result.total);
        setCandidateCount(result.candidateCount);
        setTopicCount(result.topicCount);
        if (result.page !== listPage) {
          setListPage(result.page);
        }
      } catch (error) {
        if (!cancelled) {
          showNotice(
            "热点选题加载失败",
            error instanceof Error ? error.message : "请稍后重试",
          );
        }
      } finally {
        if (!cancelled) setTopicsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [themeId, listPage, pageSize, debouncedTopicQuery, filter, sortBy, showNotice]);

  // 轮询主题下进行中的雷达采集 Job，外层展示进度（对齐课程页 detail-task-status）
  usePolling(
    async () => {
      try {
        const runs = await listHotNewsRadarCollectRuns(themeId);
        const active =
          runs.find((job) =>
            ["pending", "running"].includes(String(job.status)),
          ) || null;
        if (!active) {
          const wasActive = ["pending", "running"].includes(
            previousCollectStatus.current,
          );
          previousCollectStatus.current = "";
          setCollectJob(null);
          setCollectMessage("");
          setCollectStageTitle("");
          setCollectProgress(0);
          if (wasActive) {
            // 采集刚结束：刷新选题与最近采集指标
            void refresh(true);
          }
          return;
        }
        let job = active;
        try {
          job = await loadJob(active.id);
        } catch {
          /* 列表态足够展示 */
        }
        previousCollectStatus.current = String(job.status);
        const status = formatRadarCollectOuterStatus(job);
        setCollectJob(job);
        setCollectMessage(status.message);
        setCollectStageTitle(status.stageTitle);
        setCollectProgress(status.progress);
      } catch {
        /* 保持上次可见状态 */
      }
    },
    2_500,
    [themeId],
  );

  async function startFetch() {
    if (collectRunning) {
      navigate(
        `/modes/hot-news/themes/${encodeURIComponent(themeSlug || themeId)}/collect-execution?job=${encodeURIComponent(collectJob!.id)}`,
      );
      return;
    }
    if (!sources.some((item) => item.enabled)) {
      showNotice("还没有可用来源", "请先添加并启用至少一个来源。");
      return;
    }
    setFetching(true);
    try {
      // 点击时反查「热点雷达采集」工作流；停用则提示并中止
      await assertWorkflowFeatureAvailable(
        WorkflowFeatureKey.HOT_NEWS_RADAR_COLLECT,
      );
      const job = await startHotNewsFetch(themeId);
      const status = formatRadarCollectOuterStatus(job);
      setCollectJob(job);
      setCollectMessage(status.message);
      setCollectStageTitle(status.stageTitle);
      setCollectProgress(status.progress);
      previousCollectStatus.current = String(job.status);
      showNotice("已开始采集", "雷达页会同步进度；也可打开执行记录查看节点明细。");
      navigate(
        `/modes/hot-news/themes/${encodeURIComponent(themeSlug || themeId)}/collect-execution?job=${encodeURIComponent(job.id)}`,
      );
    } catch (error) {
      showNotice(
        "热点采集启动失败",
        error instanceof Error ? error.message : "请检查来源配置后重试",
      );
    } finally {
      setFetching(false);
    }
  }

  async function openResearchPool(topic: HotNewsTopic) {
    if (topic.status !== "confirmed" && topic.status !== "writing") {
      showNotice("请先确认选题", "进入资料池前请先确认当前选题。");
      return;
    }
    // 只进入资料池，不自动启动补充；补充由资料池页内按钮触发
    navigate(
      `/modes/hot-news/topics/${encodeURIComponent(topic.id)}/research`,
    );
  }

  async function setStatus(topic: HotNewsTopic, status: string) {
    try {
      const updated = await updateHotNewsTopicStatus(topic.id, status);
      if (detailTopic?.id === updated.id) setDetailTopic(updated);
      // 状态变更可能影响筛选结果与页码，重新拉当前页
      await loadTopicsPage(listPage, true);
      if (status === "confirmed") {
        showNotice(
          "选题已确认",
          "接下来可进入选题资料池补充资料，或检查来源并更新简报。",
        );
        await openTopicDetail(updated);
      }
    } catch (error) {
      showNotice(
        "热点状态更新失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    }
  }

  async function openTopicDetail(topic: HotNewsTopic) {
    setDetailTopic(topic);
    setDetailTab("evidence");
    setDetailLoading(true);
    try {
      const [nextSources, nextEvidence, nextBriefs, nextArticles] =
        await Promise.all([
          listHotNewsTopicSources(topic.id),
          listHotNewsEvidence(topic.id),
          listHotNewsBriefs(topic.id),
          listHotNewsArticles(topic.id),
        ]);
      setTopicSources(nextSources);
      setEvidence(nextEvidence);
      setBriefs(nextBriefs);
      setArticles(nextArticles);
    } catch (error) {
      showNotice(
        "选题详情加载失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    } finally {
      setDetailLoading(false);
    }
  }

  async function enterArticleWorkspace(topic: HotNewsTopic) {
    setArticleBusy(true);
    try {
      const article = await ensureHotNewsArticle(topic.id);
      const nextArticles = await listHotNewsArticles(topic.id);
      setArticles(nextArticles);
      await refresh(true);
      navigate(
        withDocumentIdQuery(
          `/modes/hot-news/topics/${encodeURIComponent(topic.id)}/articles/${encodeURIComponent(article.id)}`,
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

  const listPageCount = Math.max(1, Math.ceil(topicTotal / pageSize));
  const listBusy = loading || topicsLoading;

  const latestRun = runs[0];
  const successChannels =
    latestRun?.channelRuns.filter((item) => item.status === "succeeded")
      .length ?? 0;
  const failedChannels =
    latestRun?.channelRuns.filter((item) => item.status === "failed").length ??
    0;
  const coverageRate =
    latestRun && latestRun.channelRuns.length
      ? Math.round((successChannels / latestRun.channelRuns.length) * 100)
      : null;

  return (
    <HotNewsPageView
      articleBusy={articleBusy}
      articles={articles}
      briefs={briefs}
      candidateCount={candidateCount}
      collectJob={collectJob}
      collectMessage={collectMessage}
      collectProgress={collectProgress}
      collectRunning={collectRunning}
      collectStageTitle={collectStageTitle}
      coverageRate={coverageRate}
      detailLoading={detailLoading}
      detailTab={detailTab}
      detailTopic={detailTopic}
      enterArticleWorkspace={enterArticleWorkspace}
      evidence={evidence}
      failedChannels={failedChannels}
      fetching={fetching}
      filter={filter}
      latestRun={latestRun}
      listBusy={listBusy}
      listPage={listPage}
      listPageCount={listPageCount}
      loading={loading}
      navigate={navigate}
      openResearchPool={openResearchPool}
      openTopicDetail={openTopicDetail}
      pageSize={pageSize}
      pageSizeOpen={pageSizeOpen}
      pageSizeRef={pageSizeRef}
      refresh={refresh}
      setDetailTab={setDetailTab}
      setDetailTopic={setDetailTopic}
      setFilter={setFilter}
      setListPage={setListPage}
      setPageSize={setPageSize}
      setPageSizeOpen={setPageSizeOpen}
      setSortBy={setSortBy}
      setStatus={setStatus}
      setTopicQuery={setTopicQuery}
      sortBy={sortBy}
      sources={sources}
      sourcesPath={sourcesPath}
      startFetch={startFetch}
      successChannels={successChannels}
      themeId={themeId}
      themeName={themeName}
      themeSlug={themeSlug}
      topicCount={topicCount}
      topicQuery={topicQuery}
      topicSources={topicSources}
      topicTotal={topicTotal}
      topics={topics}
    />
  );
}
