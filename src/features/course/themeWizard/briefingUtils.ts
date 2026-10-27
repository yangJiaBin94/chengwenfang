import type { BriefingState } from "./types";

/** 空白问卷初始值。 */
export function emptyBriefing(): BriefingState {
  return {
    naming: {
      name: "",
      description: "",
      code: "",
      domain: "ai_intro",
      domain_custom: "",
    },
    audience: {
      readers: ["zero_basis"],
      readers_note: "",
      prerequisites: ["none"],
      time_budget: "system_1_2m",
    },
    goals: {
      outcomes: ["", "", ""],
      success_criteria: "understand",
    },
    scope: {
      must_cover: [],
      out_of_scope: [],
      difficulty_curve: "easy_to_hard",
    },
    structure: {
      series_count_pref: "3_5",
      series_count_custom: null,
      lessons_per_series_pref: "3_5",
      lessons_per_series_custom: null,
      need_overview_lessons: true,
      lesson_forms: ["concept", "practice"],
    },
    expression: {
      tone: "companion",
      length: "standard",
      boundaries: "",
    },
  };
}

/** 多选 chip 切换。 */
export function toggleInList(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}
