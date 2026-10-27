import type { ContentThemeData } from "../api";

/** 课程主题提问式创建向导入参。 */
export type ThemeWizardProps = {
  modeId: string;
  themeId?: string | null;
  onCancel: () => void;
  onCompleted: (theme: ContentThemeData) => void;
  showNotice: (title: string, detail: string) => void;
};

/** 课程主题问卷状态。 */
export type BriefingState = {
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
    prerequisites: string[];
    time_budget: string;
  };
  goals: {
    outcomes: string[];
    success_criteria: string;
  };
  scope: {
    must_cover: string[];
    out_of_scope: string[];
    difficulty_curve: string;
  };
  structure: {
    series_count_pref: string;
    series_count_custom: number | null;
    lessons_per_series_pref: string;
    lessons_per_series_custom: number | null;
    need_overview_lessons: boolean;
    lesson_forms: string[];
  };
  expression: {
    tone: string;
    length: string;
    boundaries: string;
  };
};
