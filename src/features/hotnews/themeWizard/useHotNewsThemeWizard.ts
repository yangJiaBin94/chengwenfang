import { useEffect, useMemo, useRef, useState } from "react";
import {
  commitHotNewsTheme,
  createThemeDraft,
  getContentTheme,
  hotNewsThemePath,
  patchThemeBriefing,
  saveHotNewsThemeConfig,
  suggestHotNewsCollectStrategy,
  suggestHotNewsCoreIntent,
  suggestHotNewsScoringRubric,
  suggestHotNewsSearchQueries,
  type ContentThemeData,
  type ScoringRubric,
  type ScoringRubricDimension,
} from "../../course/api";
import {
  generateHotNewsSourcesByAi,
  listHotNewsRadarCollectRuns,
  listHotNewsRuns,
} from "../api";
import {
  AUDIENCE_HOOK_KEY,
  AUDIENCE_HOOK_MIN_WEIGHT,
  MAX_RUBRIC_DIMS,
  MAX_SEARCH_QUERIES,
  MAX_SEARCH_QUERY_CHARS,
  MIN_RUBRIC_DIMS,
  WIZARD_STEP_COUNT,
} from "./constants";
import {
  atomizeSearchTerms,
  domainLabelOf,
  emptyBriefing,
  mapResumeStep,
  mergeSearchQueries,
  parseSearchQueries,
  validateRubric,
} from "./briefingUtils";
import type { BriefingState, HotNewsThemeWizardProps } from "./types";

/**
 * 热点主题向导状态与操作：草稿加载、保存、AI 生成、步骤前进与启用。
 *
 * @param props 向导入参
 */
export function useHotNewsThemeWizard({
  resumeThemeIdOrCode,
  onCancel,
  onCommitted,
  showNotice,
  navigate,
  confirmAction,
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
  const [intentGenerating, setIntentGenerating] = useState(false);
  const [strategyGenerating, setStrategyGenerating] = useState(false);
  /** resume 主题详情是否仍在加载（避免启用主题未加载完就误打 patchBriefing）。 */
  const [resumeLoading, setResumeLoading] = useState(Boolean(resumeThemeIdOrCode));
  /** 首次建稿后 navigate 会改 URL；跳过紧随其后的 resume 重载，避免冲掉表单。 */
  const skipNextResumeLoadRef = useRef(false);
  /** resume 加载代数，用于丢弃过期响应。 */
  const resumeLoadGenerationRef = useRef(0);
  /** 稳定引用，避免父组件重渲染导致 resume effect 误触发。 */
  const showNoticeRef = useRef(showNotice);
  showNoticeRef.current = showNotice;

  const editingEnabledTheme =
    theme?.status === "enabled" || theme?.status === "disabled";

  const stepTitle = useMemo(() => {
    const titles = editingEnabledTheme
      ? ["", "基本信息", "意图与策略", "采集范围", "评分量表", "确认保存"]
      : ["", "基本信息", "意图与策略", "采集范围", "评分量表", "确认启用"];
    return titles[step] || "";
  }, [step, editingEnabledTheme]);

  useEffect(() => {
    if (!resumeThemeIdOrCode) {
      setResumeLoading(false);
      return;
    }
    if (skipNextResumeLoadRef.current) {
      skipNextResumeLoadRef.current = false;
      setResumeLoading(false);
      return;
    }
    const generation = ++resumeLoadGenerationRef.current;
    setResumeLoading(true);
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
            core_intent: {
              ...emptyBriefing().core_intent,
              ...(parsed as BriefingState).core_intent,
            },
            collect_strategy: {
              ...emptyBriefing().collect_strategy,
              ...(parsed as BriefingState).collect_strategy,
            },
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
      } finally {
        if (generation === resumeLoadGenerationRef.current) {
          setResumeLoading(false);
        }
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
    const normalized = atomizeSearchTerms(nextQueries).slice(0, MAX_SEARCH_QUERIES);
    const mergedBriefing: BriefingState = {
      ...nextBriefing,
      radar: {
        ...nextBriefing.radar,
        search_queries: normalized.length
          ? normalized
          : nextBriefing.radar.search_queries,
      },
    };
    // resume 未完成时禁止打 patch；已启用/停用主题逐步编辑只改本地，最终 save-config 落库
    if (resumeLoading && !theme) {
      throw new Error("主题配置仍在加载，请稍后再试");
    }
    if (editingEnabledTheme) {
      setBriefing(mergedBriefing);
      setSearchQueries(mergedBriefing.radar.search_queries);
      return theme as ContentThemeData;
    }
    const id = await ensureDraft();
    const payload = {
      ...mergedBriefing,
      schema_version: 3,
      step: nextStep,
      scoring_rubric: mergedBriefing.scoring_rubric || null,
      radar: {
        ...mergedBriefing.radar,
        search_queries: mergedBriefing.radar.search_queries,
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
    setBriefing(mergedBriefing);
    setSearchQueries(mergedBriefing.radar.search_queries);
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
        intentSummary: briefing.core_intent.summary,
        materialWants: briefing.core_intent.material_wants,
        materialAvoids: briefing.core_intent.material_avoids,
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
      const currentDim = dims[index];
      const nextPatch = { ...patch };
      // 固定打开欲维：禁止改 key，权重不得低于下限
      if ((currentDim.key || "").trim().toLowerCase() === AUDIENCE_HOOK_KEY) {
        delete nextPatch.key;
        if (nextPatch.weight != null) {
          const weight = Number(nextPatch.weight);
          nextPatch.weight = Number.isFinite(weight)
            ? Math.max(AUDIENCE_HOOK_MIN_WEIGHT, Math.round(weight))
            : AUDIENCE_HOOK_MIN_WEIGHT;
        }
      }
      dims[index] = { ...currentDim, ...nextPatch };
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

  /** 删除一维（固定打开欲维不可删）。 */
  function removeRubricDimension(index: number) {
    setBriefing((current) => {
      const target = current.scoring_rubric?.dimensions?.[index];
      if ((target?.key || "").trim().toLowerCase() === AUDIENCE_HOOK_KEY) {
        return current;
      }
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

  /** AI 推导主要意图。 */
  async function generateIntentByAi() {
    if (!briefing.naming.name.trim()) {
      showNotice("请先填写主题名称", "第一步的主题名称会用于分析意图。");
      return;
    }
    setIntentGenerating(true);
    try {
      const result = await suggestHotNewsCoreIntent({
        name: briefing.naming.name,
        description: briefing.naming.description,
        domainLabel: domainLabelOf(briefing),
      });
      const intent = result.intent;
      if (!result.fromAi || !intent?.summary?.trim()) {
        showNotice("AI 未能分析意图", "请检查 AI 设置后重试，或手动填写意图。");
        return;
      }
      resumeLoadGenerationRef.current += 1;
      const nextBriefing: BriefingState = {
        ...briefing,
        core_intent: {
          summary: intent.summary,
          material_wants: intent.materialWants || [],
          material_avoids: intent.materialAvoids || [],
          success_looks_like: intent.successLooksLike || "",
          confirmed: false,
        },
      };
      setBriefing(nextBriefing);
      if (themeId) {
        await saveBriefing(step, nextBriefing, searchQueries);
      }
      showNotice("已生成主要意图", "请核对后点「确认此意图」。");
    } catch (error) {
      showNotice(
        "AI 分析失败",
        error instanceof Error ? error.message : "请检查 AI 设置后重试",
      );
    } finally {
      setIntentGenerating(false);
    }
  }

  /** 确认意图并落盘（有草稿时立即写入，避免刷新丢失）。 */
  async function confirmIntent() {
    if (!briefing.core_intent.summary.trim()) {
      showNotice("请完善意图", "一句话意图不能为空。");
      return;
    }
    const nextBriefing: BriefingState = {
      ...briefing,
      core_intent: { ...briefing.core_intent, confirmed: true },
    };
    setBriefing(nextBriefing);
    if (themeId) {
      try {
        await saveBriefing(step, nextBriefing, searchQueries);
      } catch (error) {
        showNotice(
          "保存失败",
          error instanceof Error ? error.message : "意图已确认但未写入服务器，请稍后重试下一步。",
        );
        return;
      }
    }
    showNotice("意图已确认", "可继续让 AI 推荐采集策略，或手动选择。");
  }

  /** AI 推荐采集策略。 */
  async function generateStrategyByAi() {
    if (!briefing.core_intent.summary.trim()) {
      showNotice("请先填写意图", "需要主要意图才能推荐策略。");
      return;
    }
    setStrategyGenerating(true);
    try {
      const result = await suggestHotNewsCollectStrategy({
        name: briefing.naming.name,
        description: briefing.naming.description,
        domainLabel: domainLabelOf(briefing),
        intentSummary: briefing.core_intent.summary,
        materialWants: briefing.core_intent.material_wants,
        materialAvoids: briefing.core_intent.material_avoids,
      });
      const strategy = result.strategy;
      if (!strategy?.mode) {
        showNotice("未能推荐策略", "请手动选择结构化或非结构化。");
        return;
      }
      resumeLoadGenerationRef.current += 1;
      const nextBriefing: BriefingState = {
        ...briefing,
        collect_strategy: {
          mode: strategy.mode,
          rationale: strategy.rationale || "",
          user_override: false,
          require_topic_freshness:
            briefing.collect_strategy.require_topic_freshness !== false,
        },
      };
      setBriefing(nextBriefing);
      if (themeId) {
        await saveBriefing(step, nextBriefing, searchQueries);
      }
      showNotice(
        "已推荐采集策略",
        `${strategy.mode === "unstructured" ? "非结构化" : "结构化"}：${strategy.rationale || ""}`,
      );
    } catch (error) {
      showNotice(
        "推荐失败",
        error instanceof Error ? error.message : "请检查 AI 设置后重试",
      );
    } finally {
      setStrategyGenerating(false);
    }
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
      nextBriefing = briefing;
    }

    if (step === 2) {
      if (!briefing.core_intent.summary.trim() || !briefing.core_intent.confirmed) {
        showNotice("请确认主要意图", "请先 AI 分析或手写意图，并点「确认此意图」。");
        return;
      }
      if (!briefing.collect_strategy.mode) {
        showNotice("请选择采集策略", "请点「AI 推荐策略」或手动选择。");
        return;
      }
      nextBriefing = briefing;
    }

    if (step === 3) {
      const unstructured = briefing.collect_strategy.mode === "unstructured";
      const normalized = atomizeSearchTerms(searchQueries).slice(0, MAX_SEARCH_QUERIES);
      if (!unstructured && normalized.length === 0) {
        showNotice(
          "请完善必填项",
          `结构化策略须点「AI 生成」或手动追加短关键词（最多 ${MAX_SEARCH_QUERIES} 条）。`,
        );
        return;
      }
      nextQueries = normalized;
      nextBriefing = {
        ...briefing,
        radar: {
          ...briefing.radar,
          search_queries: normalized,
          seed_channels: [],
        },
      };
      setBriefing(nextBriefing);
      setSearchQueries(normalized);
    }

    if (step === 4) {
      const error = validateRubric(briefing.scoring_rubric);
      if (error) {
        showNotice("请完善评分量表", error);
        return;
      }
    }

    try {
      setBusy(true);
      await saveBriefing(
        Math.min(leavingStep + 1, WIZARD_STEP_COUNT),
        nextBriefing,
        nextQueries,
      );
      setStep((current) => Math.min(current + 1, WIZARD_STEP_COUNT));
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
      const unstructured = briefing.collect_strategy.mode === "unstructured";
      const normalized = atomizeSearchTerms(searchQueries).slice(0, MAX_SEARCH_QUERIES);
      const actionLabel = editingEnabledTheme ? "保存" : "启用";
      if (!briefing.core_intent.confirmed || !briefing.core_intent.summary.trim()) {
        showNotice("请确认主要意图", `${actionLabel}前须确认主要意图。`);
        return;
      }
      if (!briefing.collect_strategy.mode) {
        showNotice("请选择采集策略", `${actionLabel}前须选定策略模式。`);
        return;
      }
      if (!unstructured && normalized.length === 0) {
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
        radar: {
          ...briefing.radar,
          search_queries: normalized,
          seed_channels: [],
        },
        scoring_rubric: briefing.scoring_rubric,
      };

      if (themeId && editingEnabledTheme) {
        const [collectRuns, fetchRuns] = await Promise.all([
          listHotNewsRadarCollectRuns(themeId),
          listHotNewsRuns(themeId),
        ]);
        const radarBusy =
          (collectRuns || []).some(
            (job) => job.status === "pending" || job.status === "running",
          ) ||
          (fetchRuns || []).some(
            (run) => run.status === "pending" || run.status === "running",
          );
        if (radarBusy) {
          showNotice("无法保存", "雷达采集进行中，请结束后再修改主题配置。");
          return;
        }
        if (confirmAction) {
          const ok = await confirmAction(
            "保存主题配置？",
            "将刷新 SearXNG，并按新意图同步沉淀池渠道；手工与 AI 补源渠道会保留。已采选题不会自动重算。",
            "danger",
          );
          if (!ok) {
            return;
          }
        }
        const result = await saveHotNewsThemeConfig(themeId, {
          briefing: nextBriefing as unknown as Record<string, unknown>,
          name: nextBriefing.naming.name.trim() || undefined,
          description: nextBriefing.naming.description,
        });
        const refreshed = await getContentTheme(result.themeId);
        setTheme(refreshed);
        showNotice(
          "配置已保存",
          `已同步配源，当前约 ${result.channelCount} 个渠道。`,
        );
        await onCommitted(refreshed);
        return;
      }

      const updated = await saveBriefing(WIZARD_STEP_COUNT, nextBriefing, normalized);
      const result = await commitHotNewsTheme(updated.id);
      const enabled = await getContentTheme(result.themeId);
      let aiSupplementNote = "";
      try {
        const generated = await generateHotNewsSourcesByAi(result.themeId);
        const createdCount = generated.created?.length || 0;
        if (createdCount > 0) {
          aiSupplementNote = `，AI 另补 ${createdCount} 个渠道`;
        }
      } catch {
        aiSupplementNote = "；AI 补源未完成，可在来源管理重试";
      }
      showNotice(
        "主题已启用",
        `已自动配置 ${result.channelCount} 个渠道${aiSupplementNote}，可开始采集。`,
      );
      await onCommitted(enabled);
    } catch (error) {
      showNotice(
        editingEnabledTheme ? "保存失败" : "启用失败",
        error instanceof Error ? error.message : "请稍后重试",
      );
    } finally {
      setBusy(false);
    }
  }

  return {
    step,
    setStep,
    stepTitle,
    theme,
    editingEnabledTheme,
    resumeLoading,
    briefing,
    setBriefing,
    searchQueries,
    setSearchQueries,
    queryInput,
    setQueryInput,
    busy,
    suggesting,
    rubricGenerating,
    intentGenerating,
    strategyGenerating,
    onCancel,
    addQueryChip,
    generateQueriesByAi,
    generateRubricByAi,
    generateIntentByAi,
    generateStrategyByAi,
    confirmIntent,
    patchRubricDimension,
    removeRubricDimension,
    addRubricDimension,
    goNext,
    commit,
    minRubricDims: MIN_RUBRIC_DIMS,
    maxRubricDims: MAX_RUBRIC_DIMS,
    maxSearchQueries: MAX_SEARCH_QUERIES,
    maxSearchQueryChars: MAX_SEARCH_QUERY_CHARS,
  };
}

/** 向导 hook 返回值类型。 */
export type HotNewsThemeWizardModel = ReturnType<typeof useHotNewsThemeWizard>;
