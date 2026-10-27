import { MAX_SEARCH_QUERIES, MAX_SEARCH_QUERY_CHARS, SEED_CHANNEL_OPTIONS } from "../constants";
import { toggleInList } from "../briefingUtils";
import type { HotNewsThemeWizardModel } from "../useHotNewsThemeWizard";

type StepRadarProps = Pick<
  HotNewsThemeWizardModel,
  | "briefing"
  | "setBriefing"
  | "searchQueries"
  | "setSearchQueries"
  | "queryInput"
  | "setQueryInput"
  | "busy"
  | "suggesting"
  | "githubQueryGenerating"
  | "addQueryChip"
  | "generateQueriesByAi"
  | "generateGithubQueryByAi"
>;

/**
 * 向导第 2 步：搜索关键词、初始渠道与 GitHub 查询串。
 *
 * @param props 采集范围相关状态与操作
 */
export function StepRadar({
  briefing,
  setBriefing,
  searchQueries,
  setSearchQueries,
  queryInput,
  setQueryInput,
  busy,
  suggesting,
  githubQueryGenerating,
  addQueryChip,
  generateQueriesByAi,
  generateGithubQueryByAi,
}: StepRadarProps) {
  return (
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
  );
}
