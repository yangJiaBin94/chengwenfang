/**
 * 课程 / 热点字数与配图档展示文案。
 * 两套选项值不同，分函数/映射保留，统一落在 shared/format。
 */

/** 课程正文字数档 → 展示文案。 */
export const wordCountLabels: Record<
  "under-2000" | "2000-4000" | "4000-6000" | "6000-8000",
  string
> = {
  "under-2000": "2000 字内",
  "2000-4000": "2000 到 4000 字",
  "4000-6000": "4000 到 6000 字",
  "6000-8000": "6000 到 8000 字",
};

/** 课程正文插图档 → 展示文案。 */
export const imageCountLabels: Record<"up-to-3" | "3-5" | "5-7", string> = {
  "up-to-3": "3 张以内",
  "3-5": "3 到 5 张",
  "5-7": "5 到 7 张",
};

/** 热点骨架/生成推荐字数档 → 展示文案。 */
export function wordCountLabel(option?: string | null) {
  switch (option) {
    case "1500-2500":
      return "1500-2500 字";
    case "4000-6000":
      return "4000-6000 字";
    case "2500-4000":
    default:
      return "2500-4000 字";
  }
}

/** 热点骨架/生成推荐插图档 → 展示文案。 */
export function imageCountLabel(option?: string | null) {
  switch (option) {
    case "2-3":
      return "2-3 张";
    case "6-8":
      return "6-8 张";
    case "3-5":
    default:
      return "3-5 张";
  }
}
