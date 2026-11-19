import { MAX_SEARCH_QUERIES, MAX_SEARCH_QUERY_CHARS } from "../constants";
import type { HotNewsThemeWizardModel } from "../useHotNewsThemeWizard";

type StepRadarProps = Pick<
  HotNewsThemeWizardModel,
  | "briefing"
  | "searchQueries"
  | "setSearchQueries"
  | "queryInput"
  | "setQueryInput"
  | "busy"
  | "suggesting"
  | "addQueryChip"
  | "generateQueriesByAi"
>;

/**
 * 向导采集范围步：搜索关键词（渠道由启用时自动配置）。
 *
 * @param props 采集范围相关状态与操作
 */
export function StepRadar({
  briefing,
  searchQueries,
  setSearchQueries,
  queryInput,
  setQueryInput,
  busy,
  suggesting,
  addQueryChip,
  generateQueriesByAi,
}: StepRadarProps) {
  const unstructured = briefing.collect_strategy?.mode === "unstructured";
  return (
    <div className="theme-wizard-panel">
      <p className="theme-wizard-hint">
        {unstructured
          ? "非结构化策略：采集时按意图多轮联网搜索。搜索词可选（作兜底）。启用后将自动配置 SearXNG、沉淀池匹配渠道，并可由 AI 补源。"
          : `点击「AI 生成」根据主要意图与主题说明生成搜索词（最多 ${MAX_SEARCH_QUERIES} 条，每条不超过 ${MAX_SEARCH_QUERY_CHARS} 字），也可手动追加短词。启用后将自动配置渠道，无需在此勾选。`}
      </p>
      <div className="theme-wizard-query-toolbar">
        <strong>
          搜索关键词{unstructured ? "" : "*"}（{searchQueries.length}/{MAX_SEARCH_QUERIES}）
        </strong>
        <div className="theme-wizard-query-actions">
          <button
            type="button"
            className="primary-button"
            disabled={busy || suggesting}
            onClick={() => void generateQueriesByAi()}
          >
            {suggesting ? "生成中…" : searchQueries.length ? "重新生成" : "AI 生成"}
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
    </div>
  );
}
