/** 领域选项。 */
export const DOMAIN_OPTIONS = [
  { value: "ai_intro", label: "AI 入门" },
  { value: "programming", label: "编程" },
  { value: "career_writing", label: "职场写作" },
  { value: "custom", label: "自定义" },
] as const;

/** 目标读者选项。 */
export const READER_OPTIONS = [
  { value: "zero_basis", label: "零基础" },
  { value: "experienced", label: "有相关经验" },
  { value: "career_switch", label: "转行" },
  { value: "on_job", label: "在职提升" },
] as const;

/** 先备知识选项。 */
export const PREREQ_OPTIONS = [
  { value: "none", label: "无" },
  { value: "computer", label: "会用电脑" },
  { value: "touched_domain", label: "接触过相关领域" },
] as const;

/** 预计投入选项。 */
export const TIME_OPTIONS = [
  { value: "light_2w", label: "2 周轻量" },
  { value: "system_1_2m", label: "1–2 月系统" },
  { value: "long_term", label: "长期体系" },
] as const;

/** 成功标准选项。 */
export const SUCCESS_OPTIONS = [
  { value: "project", label: "能独立完成小项目" },
  { value: "understand", label: "能看懂原理" },
  { value: "publish", label: "能对外输出内容" },
] as const;

/** 难度曲线选项。 */
export const CURVE_OPTIONS = [
  { value: "gentle", label: "平缓入门" },
  { value: "easy_to_hard", label: "先易后难" },
  { value: "project_spiral", label: "项目驱动螺旋" },
] as const;

/** 系列数量偏好。 */
export const SERIES_COUNT_OPTIONS = [
  { value: "3_5", label: "3–5 个系列" },
  { value: "5_8", label: "5–8 个系列" },
  { value: "custom", label: "自定义" },
] as const;

/** 每系列课次偏好。 */
export const LESSON_COUNT_OPTIONS = [
  { value: "3_5", label: "每系列 3–5 课" },
  { value: "5_8", label: "每系列 5–8 课" },
  { value: "custom", label: "自定义" },
] as const;

/** 单课形态选项。 */
export const FORM_OPTIONS = [
  { value: "concept", label: "概念讲解" },
  { value: "practice", label: "动手练习" },
  { value: "case", label: "案例拆解" },
  { value: "tool", label: "工具实操" },
] as const;

/** 语气风格选项。 */
export const TONE_OPTIONS = [
  { value: "textbook", label: "严谨教材" },
  { value: "companion", label: "口语陪跑" },
  { value: "story", label: "故事化" },
] as const;

/** 篇幅倾向选项。 */
export const LENGTH_OPTIONS = [
  { value: "short", label: "短平快" },
  { value: "standard", label: "标准长文" },
  { value: "deep", label: "深度长文" },
] as const;
