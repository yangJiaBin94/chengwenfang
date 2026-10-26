import { useEffect, useRef, useState } from "react";
import {
  Activity,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  Globe2,
  KeyRound,
  Pencil,
  Plus,
  Radar,
  RefreshCcw,
  Rss,
  Search,
  Sparkles,
  Trash2,
  Wifi,
  X,
} from "lucide-react";
import {
  createHotNewsSource,
  deleteHotNewsSource,
  generateHotNewsSourcesByAi,
  pageHotNewsSources,
  patchHotNewsSource,
  testHotNewsSource,
} from "../api";
import type { HotNewsSource } from "../api";
import {
  CHANNEL_TYPE_LABELS,
  ChannelType,
  GithubSort,
  GITHUB_SEARCH_API_URL,
  MAX_CHANNELS_PER_THEME,
  MAX_WEB_URLS_PER_CHANNEL,
  SEARCH_ENGINE_FORM_META,
  SEARXNG_DEFAULT_MAX_RESULTS,
  TAVILY_DEFAULT_MAX_RESULTS,
  TAVILY_DEFAULT_SEARCH_DEPTH,
  TAVILY_DEFAULT_TOPIC,
  TAVILY_SEARCH_API_URL,
  isSearchEngineType,
  type ChannelFetchConfig,
  type ChannelTypeValue,
  type GithubSortValue,
} from "../model";
import {
  LIST_PAGE_SIZE_OPTIONS,
  listPageNumbers,
  type ListPageSize,
} from "../../../shared/api/pagination";
import { formatSourceSuccessTime } from "../../../shared/format/time";

/** 无主题关键词时的回退搜索词（AI 前沿种子主题）。 */
const FALLBACK_SEARCH_QUERIES = [
  "AI 热点 OR 人工智能 资讯 OR AI 最新消息",
  "大模型 发布 OR 开源大模型 发布 OR LLM release OR model launch",
  "OpenAI 发布 OR ChatGPT 更新 OR OpenAI announces OR ChatGPT news",
  "Claude 发布 OR Anthropic 更新 OR Claude Code news",
  "Gemini 发布 OR Google AI 更新 OR DeepMind news",
  "DeepSeek 发布 OR 深度求索 更新 OR DeepSeek release",
  "通义 发布 OR 千问 更新 OR 智谱 发布 OR Kimi 更新 OR 月之暗面",
  "AI 融资 OR AI 收购 OR 大模型 融资 OR AI startup funding",
  "AI 监管 OR AI 安全 事件 OR AI 争议 OR AI regulation",
  "AI agent 落地 OR 智能体 应用 OR 多智能体 OR AI agent news",
  "开源 AI 爆火 OR GitHub trending AI OR 开源大模型 热门",
].join("\n");

const TYPE_LABELS = CHANNEL_TYPE_LABELS;
const TYPE_ICONS = {
  [ChannelType.RSS]: Rss,
  [ChannelType.WEB]: Globe2,
  [ChannelType.GITHUB]: Activity,
  [ChannelType.SEARXNG]: Radar,
  [ChannelType.TAVILY]: KeyRound,
} as const;

type FormState = {
  name: string;
  type: ChannelTypeValue;
  baseUrl: string;
  /** 网页多地址，换行分隔。 */
  urlsText: string;
  query: string;
  queriesText: string;
  maxResults: string;
  apiKey: string;
  sort: GithubSortValue;
};

/** 热点聚合搜索默认查询词：偏「资讯/事件/热议」，避免只搜厂商名落到官网首页。
 * 每行一次搜索；行内可用 OR。 */
function emptyForm(defaultQueriesText = FALLBACK_SEARCH_QUERIES, defaultGithubQuery = "AI OR LLM OR agent"): FormState {
  return {
    name: "",
    type: ChannelType.RSS,
    baseUrl: "",
    urlsText: "",
    query: defaultGithubQuery,
    queriesText: defaultQueriesText,
    maxResults: String(SEARXNG_DEFAULT_MAX_RESULTS),
    apiKey: "",
    sort: GithubSort.STARS,
  };
}

/** 切换到搜索引擎类型时套用固定名称与默认 URL。 */
function applySearchEngineDefaults(type: ChannelTypeValue, current: FormState): FormState {
  if (!isSearchEngineType(type)) {
    return { ...current, type };
  }
  const meta = SEARCH_ENGINE_FORM_META[type];
  return {
    ...current,
    type,
    name: meta.fixedName,
    baseUrl: meta.defaultBaseUrl,
    maxResults: String(meta.defaultMaxResults),
  };
}

/** 解析网页渠道地址列表：config.urls 优先，否则回退 baseUrl。 */
function resolveWebUrls(source: HotNewsSource): string[] {
  const fromConfig = Array.isArray(source.config?.urls)
    ? source.config.urls.map(String).map((line) => line.trim()).filter(Boolean)
    : [];
  if (fromConfig.length > 0) {
    return fromConfig;
  }
  return source.baseUrl?.trim() ? [source.baseUrl.trim()] : [];
}

function formFromSource(source: HotNewsSource): FormState {
  const queries = Array.isArray(source.config?.queries)
    ? source.config.queries.map(String).join("\n")
    : String(source.config?.query || "");
  const engineMeta = isSearchEngineType(source.type) ? SEARCH_ENGINE_FORM_META[source.type] : null;
  const defaultMax = engineMeta
    ? engineMeta.defaultMaxResults
    : source.type === ChannelType.TAVILY
      ? TAVILY_DEFAULT_MAX_RESULTS
      : SEARXNG_DEFAULT_MAX_RESULTS;
  return {
    name: engineMeta ? engineMeta.fixedName : source.name,
    type: source.type,
    baseUrl:
      source.type === ChannelType.TAVILY
        ? TAVILY_SEARCH_API_URL
        : source.baseUrl || (engineMeta?.defaultBaseUrl ?? ""),
    urlsText: resolveWebUrls(source).join("\n"),
    query: String(source.config?.query || "AI OR LLM OR agent"),
    queriesText: queries || FALLBACK_SEARCH_QUERIES,
    maxResults: String(source.config?.maxResults || defaultMax),
    apiKey: "",
    sort:
      source.config?.sort === GithubSort.UPDATED || source.config?.sort === GithubSort.FORKS
        ? source.config.sort
        : GithubSort.STARS,
  };
}

function parseQueries(text: string): string[] {
  return text
    .split(/\r?\n|,/)
    .map((line) => line.trim())
    .filter(Boolean);
}

/** 解析网页多地址（仅换行分隔，避免 URL 内逗号被拆开）。 */
function parseUrls(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

function buildConfig(form: FormState): ChannelFetchConfig {
  if (form.type === ChannelType.GITHUB) {
    return { query: form.query.trim(), dateField: "updated", sort: form.sort };
  }
  if (form.type === ChannelType.WEB) {
    return { urls: parseUrls(form.urlsText) };
  }
  if (form.type === ChannelType.SEARXNG || form.type === ChannelType.TAVILY) {
    const config: ChannelFetchConfig = {
      queries: parseQueries(form.queriesText),
      maxResults:
        Number(form.maxResults) ||
        (form.type === ChannelType.TAVILY ? TAVILY_DEFAULT_MAX_RESULTS : SEARXNG_DEFAULT_MAX_RESULTS),
    };
    if (form.type === ChannelType.TAVILY) {
      config.topic = TAVILY_DEFAULT_TOPIC;
      config.searchDepth = TAVILY_DEFAULT_SEARCH_DEPTH;
      if (form.apiKey.trim()) {
        config.apiKey = form.apiKey.trim();
      }
    }
    return config;
  }
  return {};
}

function addressHint(source: HotNewsSource): string {
  if (source.type === ChannelType.GITHUB) {
    return `关键词：${String(source.config?.query || "未配置")}`;
  }
  if (source.type === ChannelType.WEB) {
    const urls = resolveWebUrls(source);
    if (urls.length === 0) {
      return "未配置地址";
    }
    if (urls.length === 1) {
      return urls[0];
    }
    return `${urls[0]} 等 ${urls.length} 个地址`;
  }
  if (source.type === ChannelType.SEARXNG || source.type === ChannelType.TAVILY) {
    const queries = Array.isArray(source.config?.queries) ? source.config.queries.map(String) : [];
    const first = queries[0] || String(source.config?.query || "");
    return first ? `查询：${first}${queries.length > 1 ? ` 等 ${queries.length} 条` : ""}` : source.baseUrl || "未配置查询词";
  }
  return source.baseUrl;
}

export function SourceManagementPage({
  themeId,
  themeName,
  defaultSearchQueries,
  defaultGithubQuery,
  showNotice,
  confirmAction,
}: {
  themeId: string;
  themeName: string;
  defaultSearchQueries?: string;
  defaultGithubQuery?: string;
  showNotice: (title: string, message: string) => void;
  confirmAction: (title: string, message: string, tone?: "default" | "danger") => Promise<boolean>;
}) {
  const queriesDefault = defaultSearchQueries?.trim() || FALLBACK_SEARCH_QUERIES;
  const githubDefault = defaultGithubQuery?.trim() || "AI OR LLM OR agent";
  const [sources, setSources] = useState<HotNewsSource[]>([]);
  const [total, setTotal] = useState(0);
  const [enabledCount, setEnabledCount] = useState(0);
  const [disabledCount, setDisabledCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState<"all" | HotNewsSource["type"]>("all");
  const [statusFilter, setStatusFilter] = useState<"all" | "enabled" | "disabled">("all");
  const [listPage, setListPage] = useState(1);
  const [pageSize, setPageSize] = useState<ListPageSize>(5);
  const [pageSizeOpen, setPageSizeOpen] = useState(false);
  const pageSizeRef = useRef<HTMLDivElement | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingSource, setEditingSource] = useState<HotNewsSource | null>(null);
  const [form, setForm] = useState<FormState>(() => emptyForm(queriesDefault, githubDefault));
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [generatingAi, setGeneratingAi] = useState(false);

  const engineMeta = isSearchEngineType(form.type) ? SEARCH_ENGINE_FORM_META[form.type] : null;

  // 点击外部关闭每页条数菜单
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
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  // 切换主题时回到第一页并关闭表单，避免沿用上一主题的页码/编辑态
  useEffect(() => {
    setListPage(1);
    setFormOpen(false);
    setEditingSource(null);
    setFormError("");
  }, [themeId]);

  async function loadPage(page = listPage) {
    setLoading(true);
    try {
      const result = await pageHotNewsSources(themeId, {
        page,
        pageSize,
        keyword: debouncedQuery,
        type: typeFilter,
        enabled:
          statusFilter === "all"
            ? null
            : statusFilter === "enabled",
      });
      setSources(result.items);
      setTotal(result.total);
      setEnabledCount(result.enabledCount);
      setDisabledCount(result.disabledCount);
      // 后端可能夹页码（删除后末页变空）
      if (result.page !== page) {
        setListPage(result.page);
      }
    } catch (error) {
      showNotice("来源加载失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const result = await pageHotNewsSources(themeId, {
          page: listPage,
          pageSize,
          keyword: debouncedQuery,
          type: typeFilter,
          enabled:
            statusFilter === "all"
              ? null
              : statusFilter === "enabled",
        });
        if (cancelled) return;
        setSources(result.items);
        setTotal(result.total);
        setEnabledCount(result.enabledCount);
        setDisabledCount(result.disabledCount);
        if (result.page !== listPage) {
          setListPage(result.page);
        }
      } catch (error) {
        if (!cancelled) {
          showNotice("来源加载失败", error instanceof Error ? error.message : "请稍后重试");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [themeId, listPage, pageSize, debouncedQuery, typeFilter, statusFilter, showNotice]);

  const listPageCount = Math.max(1, Math.ceil(total / pageSize));
  const themeTotal = enabledCount + disabledCount;

  function openCreate() {
    if (enabledCount >= MAX_CHANNELS_PER_THEME) {
      showNotice(
        "来源数量已达上限",
        `每个主题最多 ${MAX_CHANNELS_PER_THEME} 个已启用来源，请先停用或归档不用的来源。`,
      );
      return;
    }
    setEditingSource(null);
    setForm(emptyForm(queriesDefault, githubDefault));
    setFormError("");
    setFormOpen(true);
  }

  function openEdit(source: HotNewsSource) {
    setEditingSource(source);
    setForm(formFromSource(source));
    setFormError("");
    setFormOpen(true);
  }

  function closeForm() {
    if (saving) return;
    setEditingSource(null);
    setFormError("");
    setFormOpen(false);
  }

  function onTypeChange(nextType: ChannelTypeValue) {
    setForm((current) => {
      if (isSearchEngineType(nextType)) {
        return applySearchEngineDefaults(nextType, current);
      }
      return {
        ...current,
        type: nextType,
        name: isSearchEngineType(current.type) ? "" : current.name,
        baseUrl: isSearchEngineType(current.type) ? "" : current.baseUrl,
      };
    });
  }

  async function saveSource() {
    const resolvedName = engineMeta ? engineMeta.fixedName : form.name.trim();
    if (!resolvedName) {
      setFormError("请填写来源名称。");
      return;
    }
    if (form.type === ChannelType.RSS && !form.baseUrl.trim()) {
      setFormError("请填写来源地址。");
      return;
    }
    const webUrls = parseUrls(form.urlsText);
    if (form.type === ChannelType.WEB) {
      if (webUrls.length === 0) {
        setFormError("请至少填写一个网页地址。");
        return;
      }
      if (webUrls.length > MAX_WEB_URLS_PER_CHANNEL) {
        setFormError(`网页来源最多 ${MAX_WEB_URLS_PER_CHANNEL} 个地址。`);
        return;
      }
      const invalidUrl = webUrls.find((url) => !/^https?:\/\//i.test(url));
      if (invalidUrl) {
        setFormError(`网页地址须以 http:// 或 https:// 开头：${invalidUrl}`);
        return;
      }
    }
    if (!editingSource && enabledCount >= MAX_CHANNELS_PER_THEME) {
      setFormError(`每个主题最多 ${MAX_CHANNELS_PER_THEME} 个已启用来源。`);
      return;
    }
    if (form.type === ChannelType.SEARXNG && !form.baseUrl.trim()) {
      setFormError("请填写 SearXNG 实例地址。");
      return;
    }
    if (form.type === ChannelType.GITHUB && !form.query.trim()) {
      setFormError("请填写 GitHub 搜索关键词。");
      return;
    }
    if (
      (form.type === ChannelType.SEARXNG || form.type === ChannelType.TAVILY) &&
      parseQueries(form.queriesText).length === 0
    ) {
      setFormError("请至少填写一条查询词。");
      return;
    }
    if (form.type === ChannelType.TAVILY && !editingSource && !form.apiKey.trim()) {
      setFormError("请填写 Tavily API Key。");
      return;
    }
    if (
      form.type === ChannelType.TAVILY &&
      editingSource &&
      !editingSource.credentialConfigured &&
      !form.apiKey.trim()
    ) {
      setFormError("请填写 Tavily API Key。");
      return;
    }
    setSaving(true);
    setFormError("");
    try {
      const config = buildConfig(form);
      let baseUrl = form.baseUrl.trim();
      if (form.type === ChannelType.WEB) {
        baseUrl = webUrls[0];
      } else if (form.type === ChannelType.GITHUB) {
        baseUrl = GITHUB_SEARCH_API_URL;
      } else if (form.type === ChannelType.TAVILY) {
        baseUrl = TAVILY_SEARCH_API_URL;
      }
      if (editingSource) {
        const updated = await patchHotNewsSource(editingSource.id, {
          name: resolvedName,
          type: form.type,
          baseUrl,
          config,
        });
        showNotice("来源已更新", `${updated.name} 的配置已保存。`);
      } else {
        const created = await createHotNewsSource(themeId, {
          name: resolvedName,
          type: form.type,
          baseUrl,
          config,
        });
        showNotice("来源已添加", `${created.name} 已加入当前主题。`);
        setListPage(1);
      }
      closeForm();
      await loadPage(editingSource ? listPage : 1);
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "保存失败，请检查后重试。");
    } finally {
      setSaving(false);
    }
  }

  async function toggleSource(source: HotNewsSource) {
    try {
      const updated = await patchHotNewsSource(source.id, { enabled: !source.enabled });
      // 只改本行与顶部计数，避免整表 loading 闪烁
      setSources((current) =>
        current.map((item) => (item.id === updated.id ? { ...item, ...updated } : item)),
      );
      if (updated.enabled !== source.enabled) {
        setEnabledCount((count) => Math.max(0, count + (updated.enabled ? 1 : -1)));
        setDisabledCount((count) => Math.max(0, count + (updated.enabled ? -1 : 1)));
      }
    } catch (error) {
      showNotice("状态更新失败", error instanceof Error ? error.message : "请稍后重试");
    }
  }

  async function removeSource(source: HotNewsSource) {
    const confirmed = await confirmAction(
      "归档这个来源？",
      `${source.name} 将停止参与采集，但历史采集数据会保留。之后可以用同名来源重新添加。`,
      "danger",
    );
    if (!confirmed) return;
    setDeletingId(source.id);
    try {
      await deleteHotNewsSource(source.id);
      showNotice("来源已归档", "历史采集记录仍然保留。");
      await loadPage(listPage);
    } catch (error) {
      showNotice("归档失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setDeletingId(null);
    }
  }

  async function testSource(source: HotNewsSource) {
    setTestingId(source.id);
    try {
      const result = await testHotNewsSource(source.id);
      showNotice(result.success ? "来源测试成功" : "来源测试失败", result.message);
    } catch (error) {
      showNotice("来源测试失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setTestingId(null);
    }
  }

  async function generateSourcesByAi() {
    setGeneratingAi(true);
    try {
      const result = await generateHotNewsSourcesByAi(themeId);
      if (!result.fromAi) {
        showNotice("AI 生成失败", "模型未返回可用渠道，请稍后重试或手动添加。");
        return;
      }
      const createdCount = result.created?.length ?? 0;
      const skippedCount = result.skipped?.length ?? 0;
      const skippedHint =
        skippedCount > 0
          ? `跳过 ${skippedCount} 条（连通性未通过或重复等）。`
          : "";
      if (createdCount === 0) {
        showNotice(
          "未写入新来源",
          skippedHint || "候选均未通过校验，请手动添加或稍后重试。",
        );
      } else {
        showNotice(
          "AI 来源已生成",
          `新建 ${createdCount} 条。${skippedHint}`.trim(),
        );
      }
      // 回到第一页并刷新；已在第 1 页则直接拉数，否则改页码交给列表 effect
      if (listPage === 1) {
        await loadPage(1);
      } else {
        setListPage(1);
      }
    } catch (error) {
      showNotice("AI 生成失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setGeneratingAi(false);
    }
  }

  return (
    <section className="source-management-page">
      <div className="context-line">热点资讯模式 / {themeName}</div>
      <header className="source-management-hero">
        <div>
          <span className="section-kicker">SOURCE MANAGEMENT</span>
          <h2>来源管理</h2>
          <p>管理当前主题用于热点采集的 RSS、网页、GitHub、SearXNG 与 Tavily 来源。</p>
        </div>
        <div className="source-management-hero-actions">
          <button
            className="secondary-button"
            type="button"
            onClick={() => void generateSourcesByAi()}
            disabled={loading || generatingAi || enabledCount >= MAX_CHANNELS_PER_THEME}
            title={
              enabledCount >= MAX_CHANNELS_PER_THEME
                ? `每个主题最多 ${MAX_CHANNELS_PER_THEME} 个已启用来源`
                : undefined
            }
          >
            <Sparkles size={15} />
            {generatingAi ? "生成中…" : "AI生成来源渠道"}
          </button>
          <button className="secondary-button" type="button" onClick={() => void loadPage(listPage)} disabled={loading || generatingAi}>
            <RefreshCcw size={15} />刷新
          </button>
          <button
            className="primary-button"
            type="button"
            onClick={openCreate}
            disabled={generatingAi || enabledCount >= MAX_CHANNELS_PER_THEME}
            title={
              enabledCount >= MAX_CHANNELS_PER_THEME
                ? `每个主题最多 ${MAX_CHANNELS_PER_THEME} 个已启用来源`
                : undefined
            }
          >
            <Plus size={15} />添加来源
          </button>
        </div>
      </header>

      <div className="source-management-metrics">
        <div><span>来源总数</span><strong>{themeTotal}</strong><small>含已停用</small></div>
        <div><span>已启用</span><strong>{enabledCount}</strong><small>上限 {MAX_CHANNELS_PER_THEME}</small></div>
        <div><span>已停用</span><strong>{disabledCount}</strong><small>保留配置和历史数据</small></div>
      </div>

      <section className="source-management-panel">
        <div className="source-management-toolbar">
          <label className="source-search-box">
            <Search size={16} />
            <input aria-label="搜索来源" placeholder="搜索来源名称或地址" value={query} onChange={(event) => setQuery(event.target.value)} />
          </label>
          <select
            aria-label="来源类型筛选"
            value={typeFilter}
            onChange={(event) => {
              setTypeFilter(event.target.value as typeof typeFilter);
              setListPage(1);
            }}
          >
            <option value="all">全部类型</option>
            <option value={ChannelType.RSS}>{TYPE_LABELS[ChannelType.RSS]}</option>
            <option value={ChannelType.WEB}>{TYPE_LABELS[ChannelType.WEB]}</option>
            <option value={ChannelType.GITHUB}>{TYPE_LABELS[ChannelType.GITHUB]}</option>
            <option value={ChannelType.SEARXNG}>SearXNG</option>
            <option value={ChannelType.TAVILY}>Tavily</option>
          </select>
          <select
            aria-label="来源状态筛选"
            value={statusFilter}
            onChange={(event) => {
              setStatusFilter(event.target.value as typeof statusFilter);
              setListPage(1);
            }}
          >
            <option value="all">全部状态</option>
            <option value="enabled">已启用</option>
            <option value="disabled">已停用</option>
          </select>
        </div>

        {loading ? (
          <div className="source-management-empty"><RefreshCcw size={20} /><strong>正在读取来源配置</strong></div>
        ) : sources.length === 0 ? (
          <div className="source-management-empty"><Search size={20} /><strong>{themeTotal ? "没有符合条件的来源" : "还没有来源配置"}</strong><span>{themeTotal ? "可以调整筛选条件。" : "点击右上角添加第一个来源。"}</span></div>
        ) : (
          <div className="source-management-list">
            <div className="source-management-list-head"><span>来源</span><span>类型与地址</span><span>最近状态</span><span>启用</span><span>操作</span></div>
            {sources.map((source) => {
              const Icon = TYPE_ICONS[source.type] || Globe2;
              return (
                <article className="source-management-row" key={source.id}>
                  <div className="source-management-name"><span className="source-management-icon"><Icon size={16} /></span><div><strong>{source.name}</strong><small>{source.type === ChannelType.TAVILY ? (source.credentialConfigured ? "Key 已配置" : "Key 未配置") : TYPE_LABELS[source.type] || source.type}</small></div></div>
                  <div className="source-management-address"><b>{TYPE_LABELS[source.type] || source.type}</b><span title={addressHint(source)}>{addressHint(source)}</span></div>
                  <div className="source-management-status">{source.lastSuccessAt ? <><CheckCircle2 size={14} /><span>最近成功<br />{formatSourceSuccessTime(source.lastSuccessAt)}</span></> : <><span className="source-status-dot" />尚未执行</>}</div>
                  <button className={`source-management-switch ${source.enabled ? "active" : ""}`} type="button" role="switch" aria-checked={source.enabled} aria-label={`${source.enabled ? "停用" : "启用"}${source.name}`} onClick={() => void toggleSource(source)}><span /></button>
                  <div className="source-management-actions"><button className="icon-button" type="button" title="测试来源" aria-label={`测试${source.name}`} disabled={testingId === source.id} onClick={() => void testSource(source)}><Wifi size={15} /></button><button className="icon-button" type="button" title="编辑来源" aria-label={`编辑${source.name}`} onClick={() => openEdit(source)}><Pencil size={15} /></button><button className="icon-button danger-icon-button" type="button" title="归档来源" aria-label={`归档${source.name}`} disabled={deletingId === source.id} onClick={() => void removeSource(source)}><Trash2 size={15} /></button></div>
                </article>
              );
            })}
            {total > 0 ? (
              <nav className="source-management-pager" aria-label="来源分页">
                <span className="source-management-pager-total">共 {total} 条</span>
                <div className="source-management-pager-controls">
                  <button
                    type="button"
                    className="source-management-pager-nav"
                    disabled={listPage <= 1 || loading}
                    aria-label="上一页"
                    onClick={() => setListPage((page) => Math.max(1, page - 1))}
                  >
                    <ChevronLeft size={16} />
                    上一页
                  </button>
                  {listPageNumbers(listPage, listPageCount).map((token, index) =>
                    token === "ellipsis" ? (
                      <span className="source-management-pager-ellipsis" key={`ellipsis-${index}`}>…</span>
                    ) : (
                      <button
                        type="button"
                        key={token}
                        className={`source-management-pager-page${token === listPage ? " active" : ""}`}
                        disabled={loading}
                        aria-current={token === listPage ? "page" : undefined}
                        aria-label={`第 ${token} 页`}
                        onClick={() => setListPage(token)}
                      >
                        {token}
                      </button>
                    ),
                  )}
                  <button
                    type="button"
                    className="source-management-pager-nav"
                    disabled={listPage >= listPageCount || loading}
                    aria-label="下一页"
                    onClick={() =>
                      setListPage((page) => Math.min(listPageCount, page + 1))
                    }
                  >
                    下一页
                    <ChevronRight size={16} />
                  </button>
                </div>
                <div className="source-management-pager-size" ref={pageSizeRef}>
                  <span>每页</span>
                  <button
                    type="button"
                    className={`source-management-pager-size-trigger${pageSizeOpen ? " open" : ""}`}
                    disabled={loading}
                    aria-label="每页条数"
                    aria-haspopup="listbox"
                    aria-expanded={pageSizeOpen}
                    onClick={() => setPageSizeOpen((open) => !open)}
                  >
                    <em>{pageSize} 条</em>
                    <ChevronDown size={14} />
                  </button>
                  {pageSizeOpen ? (
                    <ul className="source-management-pager-size-menu" role="listbox" aria-label="可选每页条数">
                      {LIST_PAGE_SIZE_OPTIONS.map((size) => {
                        const active = size === pageSize;
                        return (
                          <li key={size} role="option" aria-selected={active}>
                            <button
                              type="button"
                              className={active ? "active" : undefined}
                              onClick={() => {
                                setPageSize(size);
                                setListPage(1);
                                setPageSizeOpen(false);
                              }}
                            >
                              <span>{size} 条</span>
                              {active ? <Check size={14} /> : null}
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  ) : null}
                </div>
              </nav>
            ) : null}
          </div>
        )}
      </section>

      <div className="source-management-footer-note"><ExternalLink size={14} /><span>RSS 按 feed 抓取；网页可配多个页面地址依次抓取；GitHub 按关键词搜索；SearXNG/Tavily 按查询词搜索，结果统一进入热点采集流水线。</span></div>

      {formOpen && (
        <div className="modal-backdrop" role="presentation" onClick={closeForm}>
          <div className="modal-card source-management-modal" role="dialog" aria-modal="true" aria-label={editingSource ? "编辑热点来源" : "添加热点来源"} onClick={(event) => event.stopPropagation()}>
            <div className="modal-header"><div><span className="section-kicker">SOURCE</span><h3>{editingSource ? "编辑热点来源" : "添加热点来源"}</h3></div><button className="icon-button" type="button" aria-label="关闭" disabled={saving} onClick={closeForm}><X size={17} /></button></div>
            <div className="source-management-form">
              <label>来源类型
                <select value={form.type} onChange={(event) => onTypeChange(event.target.value as FormState["type"])}>
                  <option value={ChannelType.RSS}>RSS / Atom</option>
                  <option value={ChannelType.WEB}>网页</option>
                  <option value={ChannelType.GITHUB}>GitHub 热门仓库</option>
                  <option value={ChannelType.SEARXNG}>SearXNG 搜索</option>
                  <option value={ChannelType.TAVILY}>Tavily 搜索</option>
                </select>
              </label>

              {engineMeta ? (
                <>
                  <div className="source-engine-intro">
                    <strong>{engineMeta.fixedName}</strong>
                    <p>{engineMeta.description}</p>
                    <a href={engineMeta.docsUrl} target="_blank" rel="noreferrer">
                      <ExternalLink size={14} />
                      {engineMeta.docsLabel}
                    </a>
                  </div>
                  <label>来源名称
                    <input value={engineMeta.fixedName} readOnly aria-readonly="true" />
                    <small>由引擎类型固定，无需手输。</small>
                  </label>
                  <label>{form.type === ChannelType.SEARXNG ? "实例地址" : "API 地址"}
                    <input
                      value={form.type === ChannelType.TAVILY ? TAVILY_SEARCH_API_URL : form.baseUrl}
                      readOnly={!engineMeta.baseUrlEditable}
                      aria-readonly={!engineMeta.baseUrlEditable}
                      onChange={
                        engineMeta.baseUrlEditable
                          ? (event) => setForm((current) => ({ ...current, baseUrl: event.target.value }))
                          : undefined
                      }
                    />
                    <small>{engineMeta.baseUrlHint}</small>
                  </label>
                  <label>查询词列表
                    <textarea
                      rows={5}
                      value={form.queriesText}
                      placeholder={"OpenAI OR Anthropic news\nGoogle Gemini release\nAI agent open source"}
                      onChange={(event) => setForm((current) => ({ ...current, queriesText: event.target.value }))}
                    />
                    <small>{engineMeta.queriesHint}</small>
                  </label>
                  <label>单次最多条数
                    <input value={form.maxResults} onChange={(event) => setForm((current) => ({ ...current, maxResults: event.target.value }))} />
                    <small>{engineMeta.maxResultsHint}</small>
                  </label>
                  {form.type === ChannelType.TAVILY && (
                    <label>API Key{editingSource?.credentialConfigured ? "（已配置，留空则不修改）" : ""}
                      <input
                        type="password"
                        autoComplete="off"
                        value={form.apiKey}
                        placeholder={editingSource?.credentialConfigured ? "留空保留原 Key" : "tvly-xxxxxxxx"}
                        onChange={(event) => setForm((current) => ({ ...current, apiKey: event.target.value }))}
                      />
                      <small>{engineMeta.apiKeyHint}</small>
                    </label>
                  )}
                </>
              ) : (
                <>
                  <label>来源名称<input value={form.name} placeholder="例如：OpenAI 官方资讯" onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} /></label>
                  {form.type === ChannelType.RSS && (
                    <label>来源地址
                      <input
                        value={form.baseUrl}
                        placeholder="https://example.com/feed.xml"
                        onChange={(event) => setForm((current) => ({ ...current, baseUrl: event.target.value }))}
                      />
                      <small>填写站点 RSS / Atom feed 地址。</small>
                    </label>
                  )}
                  {form.type === ChannelType.WEB && (
                    <label>来源地址
                      <textarea
                        rows={4}
                        value={form.urlsText}
                        placeholder={"https://top.baidu.com/board\nhttps://quark.sm.cn/s"}
                        onChange={(event) => setForm((current) => ({ ...current, urlsText: event.target.value }))}
                      />
                      <small>每行一个页面 URL，最多 {MAX_WEB_URLS_PER_CHANNEL} 个；采集时依次打开并合并结果。</small>
                    </label>
                  )}
                  {form.type === ChannelType.GITHUB && (
                    <>
                      <label>搜索关键词<input value={form.query} placeholder="AI OR LLM OR agent" onChange={(event) => setForm((current) => ({ ...current, query: event.target.value }))} /></label>
                      <label>排序方式<select value={form.sort} onChange={(event) => setForm((current) => ({ ...current, sort: event.target.value as FormState["sort"] }))}><option value={GithubSort.STARS}>Star 数</option><option value={GithubSort.UPDATED}>最近更新</option><option value={GithubSort.FORKS}>Fork 数</option></select></label>
                      <small>按仓库更新时间过滤时效；搜索词默认来自主题 AI 生成。</small>
                    </>
                  )}
                </>
              )}
              {formError && <div className="source-management-form-error">{formError}</div>}
            </div>
            <div className="modal-actions"><button className="secondary-button" type="button" disabled={saving} onClick={closeForm}>取消</button>{editingSource && <button className="secondary-button" type="button" disabled={saving || testingId === editingSource.id} onClick={() => void testSource(editingSource)}><Wifi size={15} />{testingId === editingSource.id ? "测试中..." : "测试来源"}</button>}<button className="primary-button" type="button" disabled={saving} onClick={() => void saveSource()}>{saving ? "保存中..." : "保存来源"}</button></div>
          </div>
        </div>
      )}
    </section>
  );
}
