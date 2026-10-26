import { useEffect, useMemo, useRef, useState } from "react";
import {
  commitHotNewsTheme,
  createThemeDraft,
  getContentTheme,
  hotNewsThemePath,
  patchThemeBriefing,
  suggestHotNewsGithubQuery,
  suggestHotNewsScoringRubric,
  suggestHotNewsSearchQueries,
  type ContentThemeData,
  type ScoringRubric,
  type ScoringRubricDimension,
} from "../../course/api";

type HotNewsThemeWizardProps = {
  resumeThemeIdOrCode?: string | null;
  onCancel: () => void;
  onCommitted: (theme: ContentThemeData) => void | Promise<void>;
  showNotice: (title: string, detail: string) => void;
  navigate: (path: string) => void;
};

const DOMAIN_OPTIONS = [
  { value: "tech_product", label: "科技产品" },
  { value: "policy", label: "行业政策" },
  { value: "funding", label: "创投融资" },
  { value: "opensource", label: "开源生态" },
  { value: "consumer_brand", label: "消费品牌" },
  { value: "custom", label: "自定义" },
];

const SEED_CHANNEL_OPTIONS = [
  { value: "searxng", label: "SearXNG（推荐）" },
  { value: "tavily", label: "Tavily" },
  { value: "github", label: "GitHub" },
];

/** 热点主题问卷状态；保留完整结构以便旧草稿 resume 与成文上下文兼容。 */
type BriefingState = {
  naming: {
    name: string;
    description: string;
    code: string;
    domain: string;
    domain_custom: string;
  };
  audience: {
    readers: string[];
    readers_note: string;
    content_stance: string;
  };
  value_filters: {
    worth_writing: string[];
  };
  scope: {
    must_watch: string[];
    out_of_scope: string;
    locale: string;
  };
  radar: {
    search_queries: string[];
    github_query: string;
    seed_channels: string[];
  };
  expression: {
    tone: string;
    boundaries: string;
  };
  /** 主题级写作价值评分量表；启用前必须合法。 */
  scoring_rubric?: ScoringRubric | null;
};

const MIN_RUBRIC_DIMS = 3;
const MAX_RUBRIC_DIMS = 6;

function emptyBriefing(): BriefingState {
  return {
    naming: {
      name: "",
      description: "",
      code: "",
      domain: "tech_product",
      domain_custom: "",
    },
    // 成文上下文默认值；创建向导不再采集，仍写入 briefing 保持兼容
    audience: {
      readers: ["developer"],
      readers_note: "",
      content_stance: "deep_analysis",
    },
    value_filters: {
      worth_writing: ["multi_source", "clear_impact"],
    },
    scope: {
      must_watch: [],
      out_of_scope: "",
      locale: "zh_en",
    },
    radar: {
      search_queries: [],
      github_query: "",
      seed_channels: ["searxng"],
    },
    expression: {
      tone: "chatty_tech",
      boundaries: "",
    },
    scoring_rubric: null,
  };
}

/**
 * 校验评分量表是否可提交。
 *
 * @param rubric 量表
 * @return 错误文案；合法时 null
 */
function validateRubric(rubric: ScoringRubric | null | undefined): string | null {
  const dims = rubric?.dimensions || [];
  if (dims.length < MIN_RUBRIC_DIMS || dims.length > MAX_RUBRIC_DIMS) {
    return `评分维度须为 ${MIN_RUBRIC_DIMS}～${MAX_RUBRIC_DIMS} 个，当前 ${dims.length} 个。`;
  }
  const keys = new Set<string>();
  let weightSum = 0;
  for (const dim of dims) {
    const key = (dim.key || "").trim().toLowerCase();
    if (!/^[a-z][a-z0-9_]{1,47}$/.test(key)) {
      return "维度键须为小写英文开头的 snake_case。";
    }
    if (keys.has(key)) {
      return `维度键重复：${key}`;
    }
    keys.add(key);
    if (!(dim.label || "").trim()) {
      return "维度名称不能为空。";
    }
    if (!(dim.description || "").trim()) {
      return `维度「${dim.label}」缺少释义。`;
    }
    const weight = Number(dim.weight);
    if (!Number.isFinite(weight) || weight <= 0) {
      return `维度「${dim.label}」权重须为正整数。`;
    }
    weightSum += Math.round(weight);
  }
  if (weightSum !== 100) {
    return `维度权重之和须为 100，当前为 ${weightSum}。`;
  }
  return null;
}

function toggleInList(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

/** 单条搜索词最大字符数；与后端 SearchQueryNormalizer 对齐。 */
const MAX_SEARCH_QUERY_CHARS = 64;
/** 单次配置最多保留的搜索词条数。 */
const MAX_SEARCH_QUERIES = 12;

/**
 * 把粘贴文本拆成可检索短词（按顿号、逗号等切开）。
 *
 * @param items 原始条目
 */
function atomizeSearchTerms(items: string[]): string[] {
  const out: string[] = [];
  for (const item of items) {
    for (const part of item.split(/[、，,;；/|＆&]+/)) {
      let text = part.trim().replace(/[。.]+$/g, "").trim();
      if (!text || text.length > MAX_SEARCH_QUERY_CHARS) {
        continue;
      }
      out.push(text);
    }
  }
  return Array.from(new Set(out));
}

/**
 * 解析搜索关键词文本：按行拆分，过长行再拆成短词。
 *
 * @param text textarea 内容
 */
function parseSearchQueries(text: string): string[] {
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  return atomizeSearchTerms(lines).slice(0, MAX_SEARCH_QUERIES);
}

/** 领域展示文案（供 AI 生成）。 */
function domainLabelOf(briefing: BriefingState): string {
  if (briefing.naming.domain === "custom") {
    return briefing.naming.domain_custom.trim() || briefing.naming.name.trim() || "热点";
  }
  return (
    DOMAIN_OPTIONS.find((item) => item.value === briefing.naming.domain)?.label ||
    briefing.naming.name.trim() ||
    "热点"
  );
}

/**
 * 合并关键词 chips，遵守条数与字数上限。
 *
 * @param current 已有词
 * @param incoming 新词
 */
function mergeSearchQueries(current: string[], incoming: string[]): string[] {
  return Array.from(new Set([...current, ...atomizeSearchTerms(incoming)])).slice(
    0,
    MAX_SEARCH_QUERIES,
  );
}

/**
 * 恢复向导步骤。schema_version≥2 为四步（含评分量表）；≥1 为旧三步；更早为五步草稿。
 *
 * @param savedStep 已保存的 step
 * @param schemaVersion briefing.schema_version；缺省视为旧稿
 * @return 1–4
 */
function mapResumeStep(savedStep: number, schemaVersion?: number): number {
  if (typeof schemaVersion === "number" && schemaVersion >= 2) {
    return Math.min(4, Math.max(1, savedStep));
  }
  if (typeof schemaVersion === "number" && schemaVersion >= 1) {
    if (savedStep <= 1) {
      return 1;
    }
    if (savedStep === 2) {
      return 2;
    }
    // 旧「确认启用」映射到新第 4 步；中间补评分量表步
    return savedStep >= 3 ? 4 : 3;
  }
  // 旧 5 步：2/3/4 → 采集范围，≥5 → 确认启用
  if (savedStep <= 1) {
    return 1;
  }
  if (savedStep === 2 || savedStep === 3 || savedStep === 4) {
    return 2;
  }
  return 4;
}

/**
 * 热点主题分区创建向导：基本信息 → 采集范围 → 评分量表 → 确认启用。
 */
export function HotNewsThemeWizard({
  resumeThemeIdOrCode,
  onCancel,
  onCommitted,
  showNotice,
  navigate,
}: HotNewsThemeWizardProps) {
  const [step, setStep] = useState(1);
  const [themeId, setThemeId] = useState<string | null>(resumeThemeIdOrCode || null);
  const [theme, setTheme] = useState<ContentThemeData | null>(null);
  const [briefing, setBriefing] = useState<BriefingState>(emptyBriefing());
  /** 受控短搜索词（chip）；禁止大段自由 textarea。 */
  const [searchQueries, setSearchQueries] = useState<string[]>([]);
  const [queryInput, setQueryInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [suggesting, setSuggesting] = useState(false);
  const [rubricGenerating, setRubricGenerating] = useState(false);
  const [githubQueryGenerating, setGithubQueryGenerating] = useState(false);
  /** 首次建稿后 navigate 会改 URL；跳过紧随其后的 resume 重载，避免冲掉表单。 */
  const skipNextResumeLoadRef = useRef(false);
  /** resume 加载代数，用于丢弃过期响应。 */
  const resumeLoadGenerationRef = useRef(0);
  /** 稳定引用，避免父组件重渲染导致 resume effect 误触发。 */
  const showNoticeRef = useRef(showNotice);
  showNoticeRef.current = showNotice;

  const stepTitle = useMemo(() => {
    const titles = ["", "基本信息", "采集范围", "评分量表", "确认启用"];
    return titles[step] || "";
  }, [step]);

  useEffect(() => {
    if (!resumeThemeIdOrCode) {
      return;
    }
    if (skipNextResumeLoadRef.current) {
      skipNextResumeLoadRef.current = false;
      return;
    }
    const generation = ++resumeLoadGenerationRef.current;
    void (async () => {
      try {
        const loaded = await getContentTheme(resumeThemeIdOrCode);
        if (generation !== resumeLoadGenerationRef.current) {
          return;
        }
        setTheme(loaded);
        setThemeId(loaded.id);
        if (loaded.briefingJson) {
          const parsed = JSON.parse(loaded.briefingJson) as Partial<BriefingState> & {
            step?: number;
            schema_version?: number;
          };
          const parsedRubric = (parsed as { scoring_rubric?: ScoringRubric | null })
            .scoring_rubric;
          setBriefing({
            ...emptyBriefing(),
            ...parsed,
            naming: {
              ...emptyBriefing().naming,
              ...parsed.naming,
              // 编码只读：以主题实体为准
              code: loaded.code || parsed.naming?.code || "",
            },
            audience: { ...emptyBriefing().audience, ...parsed.audience },
            value_filters: {
              ...emptyBriefing().value_filters,
              ...parsed.value_filters,
            },
            scope: { ...emptyBriefing().scope, ...parsed.scope },
            radar: { ...emptyBriefing().radar, ...parsed.radar },
            expression: { ...emptyBriefing().expression, ...parsed.expression },
            scoring_rubric: parsedRubric?.dimensions?.length ? parsedRubric : null,
          });
          const queries = parseSearchQueries(
            (parsed.radar?.search_queries || []).join("\n"),
          );
          setSearchQueries(queries);
          if (typeof parsed.step === "number" && parsed.step >= 1) {
            setStep(mapResumeStep(parsed.step, parsed.schema_version));
          }
        }
      } catch (error) {
        if (generation !== resumeLoadGenerationRef.current) {
          return;
        }
        showNoticeRef.current(
          "加载主题失败",
          error instanceof Error ? error.message : "请稍后重试",
        );
      }
    })();
  }, [resumeThemeIdOrCode]);

  async function ensureDraft(): Promise<string> {
    if (themeId) {
      return themeId;
    }
    // 编码不传，由后端按主题名自动生成并避重
    const created = await createThemeDraft("hot_news", {
      name: briefing.naming.name || "未命名热点主题",
      description: briefing.naming.description,
    });
    setThemeId(created.id);
    setTheme(created);
    setBriefing((current) => ({
      ...current,
      naming: { ...current.naming, code: created.code },
    }));
    // 先标记再改 URL，避免 App 同步 resumeId 时把刚写入的本地状态冲掉
    skipNextResumeLoadRef.current = true;
    void navigate(hotNewsThemePath(created.code, "wizard"));
    return created.id;
  }

  async function saveBriefing(
    nextStep: number,
    nextBriefing: BriefingState,
    nextQueries: string[],
  ) {
    const id = await ensureDraft();
    const normalized = atomizeSearchTerms(nextQueries).slice(0, MAX_SEARCH_QUERIES);
    const payload = {
      ...nextBriefing,
      schema_version: 2,
      step: nextStep,
      scoring_rubric: nextBriefing.scoring_rubric || null,
      radar: {
        ...nextBriefing.radar,
        search_queries: normalized.length
          ? normalized
          : nextBriefing.radar.search_queries,
      },
    };
    const updated = await patchThemeBriefing(id, {
      briefing: payload as unknown as Record<string, unknown>,
      name: nextBriefing.naming.name,
      description: nextBriefing.naming.description,
      // 编码创建后固定，补丁中带回已生成值即可
      code: nextBriefing.naming.code || theme?.code || undefined,
    });
    setTheme(updated);
    setThemeId(updated.id);
    setBriefing({
      ...nextBriefing,
      radar: {
        ...nextBriefing.radar,
        search_queries: payload.radar.search_queries,
      },
    });
    setSearchQueries(payload.radar.search_queries);
    return updated;
  }

  /** 追加一条用户输入的短词（禁止超长粘贴）。 */
  function addQueryChip() {
    const atoms = atomizeSearchTerms([queryInput]);
    if (atoms.length === 0) {
      showNotice(
        "关键词不合规",
        `请输入不超过 ${MAX_SEARCH_QUERY_CHARS} 字的短词；长文会按顿号拆分，无法拆分则拒绝。`,
      );
      return;
    }
    if (searchQueries.length >= MAX_SEARCH_QUERIES) {
      showNotice("已达上限", `搜索词最多 ${MAX_SEARCH_QUERIES} 条。`);
      return;
    }
    setSearchQueries((current) => mergeSearchQueries(current, atoms));
    setQueryInput("");
  }

  /**
   * 把生成的搜索词写入本地状态；已有草稿时落盘，避免后续 resume 盖回旧词。
   *
   * @param next 规范化后的短词
   * @param persistStep 落盘用的向导步骤（自动生成时可能尚未完成 setStep）
   */
  async function applyGeneratedQueries(next: string[], persistStep?: number) {
    // 作废进行中的 resume 响应，防止异步回写冲掉刚生成的词
    resumeLoadGenerationRef.current += 1;
    const nextBriefing: BriefingState = {
      ...briefing,
      radar: {
        ...briefing.radar,
        search_queries: next,
      },
    };
    setSearchQueries(next);
    setBriefing(nextBriefing);
    if (themeId) {
      await saveBriefing(persistStep ?? step, nextBriefing, next);
    }
  }

  /**
   * 调用 AI 生成搜索词；仅采纳 fromAi=true 的结果，失败不填充分词假数据。
   *
   * @param options.persistStep 落盘步骤号
   */
  async function generateQueriesByAi(options?: { persistStep?: number }) {
    if (!briefing.naming.name.trim()) {
      showNotice("请先填写主题名称", "第一步的主题名称会用于生成搜索词。");
      return;
    }
    setSuggesting(true);
    try {
      const result = await suggestHotNewsSearchQueries({
        name: briefing.naming.name,
        description: briefing.naming.description,
        domainLabel: domainLabelOf(briefing),
        mustWatch: [],
      });
      const next = atomizeSearchTerms(result.queries || []).slice(0, MAX_SEARCH_QUERIES);
      if (!result.fromAi || next.length === 0) {
        showNotice(
          "AI 未能生成搜索词",
          "请检查 AI 设置与模型连通性后重试，或手动追加短词。",
        );
        return;
      }
      try {
        await applyGeneratedQueries(next, options?.persistStep);
      } catch (persistError) {
        showNotice(
          "已用 AI 生成搜索词",
          `共 ${next.length} 条已展示；草稿保存失败：${
            persistError instanceof Error ? persistError.message : "请稍后重试"
          }`,
        );
        return;
      }
      showNotice(
        "已用 AI 生成搜索词",
        `共 ${next.length} 条（最多 ${MAX_SEARCH_QUERIES} 条，每条 ≤${MAX_SEARCH_QUERY_CHARS} 字）。可继续手动追加。`,
      );
    } catch (error) {
      showNotice(
        "AI 生成失败",
        error instanceof Error ? error.message : "请检查 AI 设置后重试，或手动追加短词",
      );
    } finally {
      setSuggesting(false);
    }
  }

  /**
   * 调用 AI 生成 GitHub 查询串，写入 radar.github_query。
   */
  async function generateGithubQueryByAi() {
    if (!briefing.naming.name.trim()) {
      showNotice("请先填写主题名称", "主题名称会用于生成 GitHub 查询串。");
      return;
    }
    setGithubQueryGenerating(true);
    try {
      const result = await suggestHotNewsGithubQuery({
        name: briefing.naming.name,
        description: briefing.naming.description,
        domainLabel: domainLabelOf(briefing),
        searchQueries: searchQueries.length
          ? searchQueries
          : briefing.radar.search_queries,
      });
      const query = String(result.query || "").trim();
      if (!result.fromAi || !query) {
        showNotice(
          "AI 未能生成 GitHub 查询串",
          "请检查 AI 设置后重试，或手动填写（如 AI OR LLM OR agent）。",
        );
        return;
      }
      resumeLoadGenerationRef.current += 1;
      const nextBriefing: BriefingState = {
        ...briefing,
        radar: { ...briefing.radar, github_query: query },
      };
      setBriefing(nextBriefing);
      if (themeId) {
        await saveBriefing(step, nextBriefing, searchQueries);
      }
      showNotice("已用 AI 生成 GitHub 查询串", "可按需微调后再进入下一步。");
    } catch (error) {
      showNotice(
        "AI 生成失败",
        error instanceof Error ? error.message : "请检查 AI 设置后重试",
      );
    } finally {
      setGithubQueryGenerating(false);
    }
  }

  /**
   * AI 生成评分量表并写入本地状态（有草稿则落盘）。
   *
   * @param options.persistStep 落盘步骤
   */
  async function generateRubricByAi(options?: { persistStep?: number }) {
    if (!briefing.naming.name.trim()) {
      showNotice("请先填写主题名称", "主题名称会用于生成评分维度。");
      return;
    }
    setRubricGenerating(true);
    try {
      const result = await suggestHotNewsScoringRubric({
        name: briefing.naming.name,
        description: briefing.naming.description,
        domainLabel: domainLabelOf(briefing),
        searchQueries: searchQueries.length
          ? searchQueries
          : briefing.radar.search_queries,
        worthWriting: briefing.value_filters.worth_writing,
        readers: briefing.audience.readers,
        outOfScope: briefing.scope.out_of_scope,
        contentStance: briefing.audience.content_stance,
      });
      const rubric = result.rubric;
      const error = validateRubric(rubric);
      if (error) {
        showNotice("量表不合规", error);
        return;
      }
      resumeLoadGenerationRef.current += 1;
      const nextBriefing: BriefingState = {
        ...briefing,
        scoring_rubric: rubric,
      };
      setBriefing(nextBriefing);
      if (themeId) {
        await saveBriefing(options?.persistStep ?? step, nextBriefing, searchQueries);
      }
      showNotice(
        "已生成评分量表",
        `${rubric.dimensions.length} 个维度，权重之和 100。可改名称、释义与权重。`,
      );
    } catch (error) {
      showNotice(
        "生成失败",
        error instanceof Error ? error.message : "请检查 AI 设置后重试",
      );
    } finally {
      setRubricGenerating(false);
    }
  }

  /** 更新量表中某一维字段。 */
  function patchRubricDimension(
    index: number,
    patch: Partial<ScoringRubricDimension>,
  ) {
    setBriefing((current) => {
      const dims = [...(current.scoring_rubric?.dimensions || [])];
      if (!dims[index]) {
        return current;
      }
      dims[index] = { ...dims[index], ...patch };
      return {
        ...current,
        scoring_rubric: {
          version: current.scoring_rubric?.version || 1,
          generatedAt: current.scoring_rubric?.generatedAt,
          scoreGuidance: current.scoring_rubric?.scoreGuidance,
          dimensions: dims,
        },
      };
    });
  }

  /** 删除一维。 */
  function removeRubricDimension(index: number) {
    setBriefing((current) => {
      const dims = (current.scoring_rubric?.dimensions || []).filter(
        (_item, dimIndex) => dimIndex !== index,
      );
      return {
        ...current,
        scoring_rubric: current.scoring_rubric
          ? { ...current.scoring_rubric, dimensions: dims }
          : null,
      };
    });
  }

  /** 追加一维（需用户补齐）。 */
  function addRubricDimension() {
    setBriefing((current) => {
      const dims = [...(current.scoring_rubric?.dimensions || [])];
      if (dims.length >= MAX_RUBRIC_DIMS) {
        return current;
      }
      dims.push({
        key: `custom_dim_${dims.length + 1}`,
        label: "新维度",
        description: "请填写判定释义",
        weight: 1,
      });
      return {
        ...current,
        scoring_rubric: {
          version: current.scoring_rubric?.version || 1,
          generatedAt: current.scoring_rubric?.generatedAt,
          scoreGuidance: current.scoring_rubric?.scoreGuidance || "",
          dimensions: dims,
        },
      };
    });
  }

  async function goNext() {
    let nextBriefing = briefing;
    let nextQueries = searchQueries;
    const leavingStep = step;

    if (step === 1) {
      if (!briefing.naming.name.trim()) {
        showNotice("请完善必填项", "主题名称不能为空。");
        return;
      }
      // 编码由创建草稿时后端自动生成，此处不写入手填值
      nextBriefing = briefing;
    }

    if (step === 2) {
      const normalized = atomizeSearchTerms(searchQueries).slice(0, MAX_SEARCH_QUERIES);
      if (normalized.length === 0) {
        showNotice(
          "请完善必填项",
          `请点「AI 生成」或手动追加短关键词（最多 ${MAX_SEARCH_QUERIES} 条，每条 ≤${MAX_SEARCH_QUERY_CHARS} 字）。`,
        );
        return;
      }
      if (briefing.radar.seed_channels.length === 0) {
        showNotice("请完善必填项", "请至少选择一个初始搜索渠道。");
        return;
      }
      if (
        briefing.radar.seed_channels.includes("github") &&
        !briefing.radar.github_query.trim()
      ) {
        showNotice(
          "请完善必填项",
          "勾选 GitHub 渠道时请点「AI 生成」或手动填写查询串。",
        );
        return;
      }
      nextQueries = normalized;
      nextBriefing = {
        ...briefing,
        radar: { ...briefing.radar, search_queries: normalized },
      };
      setBriefing(nextBriefing);
      setSearchQueries(normalized);
    }

    if (step === 3) {
      const error = validateRubric(briefing.scoring_rubric);
      if (error) {
        showNotice("请完善评分量表", error);
        return;
      }
    }

    try {
      setBusy(true);
      await saveBriefing(Math.min(leavingStep + 1, 4), nextBriefing, nextQueries);
      setStep((current) => Math.min(current + 1, 4));
    } catch (error) {
      showNotice("保存失败", error instanceof Error ? error.message : "请稍后重试");
      return;
    } finally {
      setBusy(false);
    }

  }

  async function commit() {
    setBusy(true);
    try {
      const normalized = atomizeSearchTerms(searchQueries).slice(0, MAX_SEARCH_QUERIES);
      if (normalized.length === 0) {
        showNotice(
          "请完善必填项",
          `请至少确认一条可检索短关键词（每条不超过 ${MAX_SEARCH_QUERY_CHARS} 字）。`,
        );
        return;
      }
      const rubricError = validateRubric(briefing.scoring_rubric);
      if (rubricError) {
        showNotice("请完善评分量表", rubricError);
        return;
      }
      const nextBriefing = {
        ...briefing,
        radar: { ...briefing.radar, search_queries: normalized },
        scoring_rubric: briefing.scoring_rubric,
      };
      // 若上次已启用成功但未离开向导，直接回总览，避免再次 patch 草稿报错
      if (themeId) {
        const existing = await getContentTheme(themeId);
        if (existing.status === "enabled") {
          showNotice("主题已启用", "该主题此前已启用，正在返回主题列表。");
          await onCommitted(existing);
          return;
        }
      }
      const updated = await saveBriefing(4, nextBriefing, normalized);
      const result = await commitHotNewsTheme(updated.id);
      const enabled = await getContentTheme(result.themeId);
      showNotice(
        "主题已启用",
        `已创建 ${result.channelCount} 个初始渠道，可开始采集。`,
      );
      await onCommitted(enabled);
    } catch (error) {
      showNotice("启用失败", error instanceof Error ? error.message : "请稍后重试");
    } finally {
      setBusy(false);
    }
  }

  const seedChannelLabels = briefing.radar.seed_channels
    .map((value) => SEED_CHANNEL_OPTIONS.find((item) => item.value === value)?.label || value)
    .join("、");

  return (
    <section className="theme-wizard hot-news-theme-wizard">
      <div className="context-line">热点资讯模式 / 新建主题分区</div>
      <header className="theme-wizard-header">
        <div>
          <span className="section-kicker">Hot News Theme</span>
          <h2>{theme?.name || "新建主题分区"}</h2>
          <p>
            步骤 {step} / 4：{stepTitle}
            。雷达流程通用，主题用于隔离统计、选题素材、文章与来源配置；写作价值按本主题量表打分。
          </p>
        </div>
        <button type="button" className="ghost-button" onClick={onCancel} disabled={busy}>
          取消
        </button>
      </header>

      {step === 1 && (
        <div className="theme-wizard-panel">
          <label>
            主题名称*
            <input
              value={briefing.naming.name}
              placeholder="如：AI 前沿热点"
              onChange={(event) =>
                setBriefing((current) => ({
                  ...current,
                  naming: { ...current.naming, name: event.target.value },
                }))
              }
            />
          </label>
          <label>
            主题说明（可选）
            <input
              value={briefing.naming.description}
              placeholder="这个主题关注什么"
              onChange={(event) =>
                setBriefing((current) => ({
                  ...current,
                  naming: { ...current.naming, description: event.target.value },
                }))
              }
            />
          </label>
          <p>领域标签（可选）</p>
          <div className="chip-row">
            {DOMAIN_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={briefing.naming.domain === option.value ? "chip selected" : "chip"}
                onClick={() =>
                  setBriefing((current) => ({
                    ...current,
                    naming: { ...current.naming, domain: option.value },
                  }))
                }
              >
                {option.label}
              </button>
            ))}
          </div>
          {briefing.naming.domain === "custom" && (
            <label>
              自定义领域
              <input
                value={briefing.naming.domain_custom}
                onChange={(event) =>
                  setBriefing((current) => ({
                    ...current,
                    naming: { ...current.naming, domain_custom: event.target.value },
                  }))
                }
              />
            </label>
          )}
        </div>
      )}

      {step === 2 && (
        <div className="theme-wizard-panel">
          <p className="theme-wizard-hint">
            点击「AI 生成」根据主题名称与说明生成搜索词（最多 {MAX_SEARCH_QUERIES}{" "}
            条，每条不超过 {MAX_SEARCH_QUERY_CHARS} 字），也可手动追加短词。
          </p>
          <div className="theme-wizard-query-toolbar">
            <strong>
              搜索关键词*（{searchQueries.length}/{MAX_SEARCH_QUERIES}）
            </strong>
            <div className="theme-wizard-query-actions">
              <button
                type="button"
                className="primary-button"
                disabled={busy || suggesting}
                onClick={() => void generateQueriesByAi()}
              >
                {suggesting
                  ? "生成中…"
                  : searchQueries.length
                    ? "重新生成"
                    : "AI 生成"}
              </button>
            </div>
          </div>
          <div className="chip-row">
            {searchQueries.map((item) => (
              <button
                key={item}
                type="button"
                className="chip selected"
                onClick={() =>
                  setSearchQueries((current) => current.filter((entry) => entry !== item))
                }
              >
                {item} ×
              </button>
            ))}
            {suggesting && searchQueries.length === 0 && (
              <span className="theme-wizard-empty-hint">正在生成搜索词…</span>
            )}
            {!suggesting && searchQueries.length === 0 && (
              <span className="theme-wizard-empty-hint">
                尚未添加关键词，可点「AI 生成」或手动追加
              </span>
            )}
          </div>
          <label>
            手动追加短词（可选）
            <div className="tag-input-row">
              <input
                value={queryInput}
                maxLength={MAX_SEARCH_QUERY_CHARS}
                placeholder={`不超过 ${MAX_SEARCH_QUERY_CHARS} 字，回车添加`}
                disabled={searchQueries.length >= MAX_SEARCH_QUERIES}
                onChange={(event) => setQueryInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    addQueryChip();
                  }
                }}
              />
              <button
                type="button"
                className="secondary-button"
                disabled={searchQueries.length >= MAX_SEARCH_QUERIES}
                onClick={() => addQueryChip()}
              >
                添加
              </button>
            </div>
          </label>
          <p>创建时启用的初始搜索渠道*</p>
          <div className="chip-row">
            {SEED_CHANNEL_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={
                  briefing.radar.seed_channels.includes(option.value) ? "chip selected" : "chip"
                }
                onClick={() =>
                  setBriefing((current) => ({
                    ...current,
                    radar: {
                      ...current.radar,
                      seed_channels: toggleInList(current.radar.seed_channels, option.value),
                    },
                  }))
                }
              >
                {option.label}
              </button>
            ))}
          </div>
          {briefing.radar.seed_channels.includes("github") && (
            <div className="theme-wizard-github-query">
              <div className="theme-wizard-query-toolbar">
                <strong>GitHub 查询串*</strong>
                <div className="theme-wizard-query-actions">
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={busy || githubQueryGenerating || suggesting}
                    onClick={() => void generateGithubQueryByAi()}
                  >
                    {githubQueryGenerating
                      ? "生成中…"
                      : briefing.radar.github_query.trim()
                        ? "重新生成"
                        : "AI 生成"}
                  </button>
                </div>
              </div>
              <input
                value={briefing.radar.github_query}
                placeholder="如 AI OR LLM OR agent；可点 AI 生成"
                disabled={githubQueryGenerating}
                onChange={(event) =>
                  setBriefing((current) => ({
                    ...current,
                    radar: { ...current.radar, github_query: event.target.value },
                  }))
                }
              />
            </div>
          )}
        </div>
      )}

      {step === 3 && (
        <div className="theme-wizard-panel">
          <p className="theme-wizard-hint">
            点击「AI 生成」为本主题生成写作价值评分维度（{MIN_RUBRIC_DIMS}～
            {MAX_RUBRIC_DIMS} 个，权重之和 100），也可手动添加维度。
          </p>
          <div className="theme-wizard-query-toolbar">
            <strong>
              评分维度（{briefing.scoring_rubric?.dimensions?.length || 0}/{MAX_RUBRIC_DIMS}）
            </strong>
            <div className="theme-wizard-query-actions">
              <button
                type="button"
                className="secondary-button"
                disabled={
                  busy ||
                  rubricGenerating ||
                  (briefing.scoring_rubric?.dimensions?.length || 0) >= MAX_RUBRIC_DIMS
                }
                onClick={() => addRubricDimension()}
              >
                添加维度
              </button>
              <button
                type="button"
                className="primary-button"
                disabled={busy || rubricGenerating}
                onClick={() => void generateRubricByAi()}
              >
                {rubricGenerating
                  ? "生成中…"
                  : briefing.scoring_rubric?.dimensions?.length
                    ? "重新生成"
                    : "AI 生成"}
              </button>
            </div>
          </div>
          {rubricGenerating && !(briefing.scoring_rubric?.dimensions?.length) && (
            <p className="theme-wizard-empty-hint">正在根据主题生成评分量表…</p>
          )}
          {!rubricGenerating && !(briefing.scoring_rubric?.dimensions?.length) && (
            <p className="theme-wizard-empty-hint">
              尚未生成量表，可点「AI 生成」或「添加维度」
            </p>
          )}
          {(briefing.scoring_rubric?.dimensions || []).map((dim, index) => (
            <div key={`${dim.key}-${index}`} className="theme-wizard-rubric-row">
              <label>
                键
                <input
                  value={dim.key}
                  onChange={(event) =>
                    patchRubricDimension(index, { key: event.target.value })
                  }
                />
              </label>
              <label>
                名称
                <input
                  value={dim.label}
                  onChange={(event) =>
                    patchRubricDimension(index, { label: event.target.value })
                  }
                />
              </label>
              <label>
                权重
                <input
                  type="number"
                  min={1}
                  max={100}
                  value={dim.weight}
                  onChange={(event) =>
                    patchRubricDimension(index, {
                      weight: Number(event.target.value) || 0,
                    })
                  }
                />
              </label>
              <label className="theme-wizard-rubric-desc">
                释义
                <input
                  value={dim.description}
                  onChange={(event) =>
                    patchRubricDimension(index, { description: event.target.value })
                  }
                />
              </label>
              <button
                type="button"
                className="ghost-button"
                disabled={(briefing.scoring_rubric?.dimensions?.length || 0) <= MIN_RUBRIC_DIMS}
                onClick={() => removeRubricDimension(index)}
              >
                删除
              </button>
            </div>
          ))}
          <label>
            整体打分指引（可选）
            <input
              value={briefing.scoring_rubric?.scoreGuidance || ""}
              placeholder="例如：触碰不做范围整体压低"
              onChange={(event) =>
                setBriefing((current) => ({
                  ...current,
                  scoring_rubric: {
                    version: current.scoring_rubric?.version || 1,
                    generatedAt: current.scoring_rubric?.generatedAt,
                    dimensions: current.scoring_rubric?.dimensions || [],
                    scoreGuidance: event.target.value,
                  },
                }))
              }
            />
          </label>
          <p className="theme-wizard-hint">
            当前权重之和：
            {(briefing.scoring_rubric?.dimensions || []).reduce(
              (sum, dim) => sum + (Number(dim.weight) || 0),
              0,
            )}
            {" / 100"}
          </p>
        </div>
      )}

      {step === 4 && (
        <div className="theme-wizard-panel confirm-panel">
          <h3>确认启用</h3>
          <ul>
            <li>
              <strong>主题</strong>：{briefing.naming.name}
            </li>
            <li>
              <strong>说明</strong>：{briefing.naming.description || "无"}
            </li>
            <li>
              <strong>关键词</strong>：
              {searchQueries.length || briefing.radar.search_queries.length
                ? (searchQueries.length ? searchQueries : briefing.radar.search_queries).join(
                    " · ",
                  )
                : "无"}
            </li>
            <li>
              <strong>初始渠道</strong>：{seedChannelLabels || "未选"}
            </li>
            <li>
              <strong>评分维度</strong>：
              {(briefing.scoring_rubric?.dimensions || [])
                .map((dim) => `${dim.label}(${dim.weight}%)`)
                .join(" · ") || "未配置"}
            </li>
          </ul>
          <p>确认后主题将启用，并按清单创建初始搜索渠道；之后可在来源管理中继续调整。</p>
        </div>
      )}

      <footer className="theme-wizard-footer">
        {step > 1 && (
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={() => setStep((current) => Math.max(1, current - 1))}
          >
            上一步
          </button>
        )}
        {step < 4 ? (
          <button
            type="button"
            className="primary-button"
            disabled={busy || rubricGenerating}
            onClick={() => void goNext()}
          >
            {busy ? "保存中…" : "下一步"}
          </button>
        ) : (
          <button
            type="button"
            className="primary-button"
            disabled={busy}
            onClick={() => void commit()}
          >
            {busy ? "启用中…" : "启用主题"}
          </button>
        )}
      </footer>
    </section>
  );
}
