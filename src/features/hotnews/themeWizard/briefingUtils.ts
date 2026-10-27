import type { ScoringRubric } from "../../course/api";
import {
  DOMAIN_OPTIONS,
  MAX_RUBRIC_DIMS,
  MAX_SEARCH_QUERIES,
  MAX_SEARCH_QUERY_CHARS,
  MIN_RUBRIC_DIMS,
} from "./constants";
import type { BriefingState } from "./types";

/** 空白问卷初始值。 */
export function emptyBriefing(): BriefingState {
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
export function validateRubric(rubric: ScoringRubric | null | undefined): string | null {
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

/** 多选 chip 切换。 */
export function toggleInList(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

/**
 * 把粘贴文本拆成可检索短词（按顿号、逗号等切开）。
 *
 * @param items 原始条目
 */
export function atomizeSearchTerms(items: string[]): string[] {
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
export function parseSearchQueries(text: string): string[] {
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
  return atomizeSearchTerms(lines).slice(0, MAX_SEARCH_QUERIES);
}

/** 领域展示文案（供 AI 生成）。 */
export function domainLabelOf(briefing: BriefingState): string {
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
export function mergeSearchQueries(current: string[], incoming: string[]): string[] {
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
export function mapResumeStep(savedStep: number, schemaVersion?: number): number {
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
