/** 列表分页可选每页条数。 */
export const LIST_PAGE_SIZE_OPTIONS = [5, 10, 20, 50, 100] as const;

export type ListPageSize = (typeof LIST_PAGE_SIZE_OPTIONS)[number];

/** 生成分页页码：首尾保留，中间用省略号。 */
export function listPageNumbers(
  current: number,
  pageCount: number,
): Array<number | "ellipsis"> {
  if (pageCount <= 7) {
    return Array.from({ length: pageCount }, (_, index) => index + 1);
  }
  const pages = new Set<number>([1, pageCount, current, current - 1, current + 1]);
  if (current <= 3) {
    pages.add(2);
    pages.add(3);
    pages.add(4);
  }
  if (current >= pageCount - 2) {
    pages.add(pageCount - 3);
    pages.add(pageCount - 2);
    pages.add(pageCount - 1);
  }
  const sorted = [...pages]
    .filter((page) => page >= 1 && page <= pageCount)
    .sort((left, right) => left - right);
  const tokens: Array<number | "ellipsis"> = [];
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1] > 1) {
      tokens.push("ellipsis");
    }
    tokens.push(page);
  });
  return tokens;
}
