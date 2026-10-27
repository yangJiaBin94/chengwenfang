import type { ContentThemeData, ScoringRubric } from "../../course/api";

/** 热点主题创建向导入参。 */
export type HotNewsThemeWizardProps = {
  resumeThemeIdOrCode?: string | null;
  onCancel: () => void;
  onCommitted: (theme: ContentThemeData) => void | Promise<void>;
  showNotice: (title: string, detail: string) => void;
  navigate: (path: string) => void;
};

/** 热点主题问卷状态；保留完整结构以便旧草稿 resume 与成文上下文兼容。 */
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
