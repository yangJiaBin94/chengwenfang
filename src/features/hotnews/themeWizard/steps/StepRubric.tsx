import type { HotNewsThemeWizardModel } from "../useHotNewsThemeWizard";

type StepRubricProps = Pick<
  HotNewsThemeWizardModel,
  | "briefing"
  | "setBriefing"
  | "busy"
  | "rubricGenerating"
  | "generateRubricByAi"
  | "patchRubricDimension"
  | "removeRubricDimension"
  | "addRubricDimension"
  | "minRubricDims"
  | "maxRubricDims"
>;

/**
 * 向导第 3 步：写作价值评分量表编辑。
 *
 * @param props 量表相关状态与操作
 */
export function StepRubric({
  briefing,
  setBriefing,
  busy,
  rubricGenerating,
  generateRubricByAi,
  patchRubricDimension,
  removeRubricDimension,
  addRubricDimension,
  minRubricDims,
  maxRubricDims,
}: StepRubricProps) {
  const dimensions = briefing.scoring_rubric?.dimensions || [];
  return (
    <div className="theme-wizard-panel">
      <p className="theme-wizard-hint">
        点击「AI 生成」为本主题生成写作价值评分维度（{minRubricDims}～
        {maxRubricDims} 个，权重之和 100），也可手动添加维度。
      </p>
      <div className="theme-wizard-query-toolbar">
        <strong>
          评分维度（{dimensions.length}/{maxRubricDims}）
        </strong>
        <div className="theme-wizard-query-actions">
          <button
            type="button"
            className="secondary-button"
            disabled={busy || rubricGenerating || dimensions.length >= maxRubricDims}
            onClick={() => addRubricDimension()}
          >
            添加维度
          </button>
          <button
            type="button"
            className="primary-button"
            disabled={busy || rubricGenerating}
            onClick={() => void generateRubricByAi()}
          >
            {rubricGenerating
              ? "生成中…"
              : dimensions.length
                ? "重新生成"
                : "AI 生成"}
          </button>
        </div>
      </div>
      {rubricGenerating && dimensions.length === 0 && (
        <p className="theme-wizard-empty-hint">正在根据主题生成评分量表…</p>
      )}
      {!rubricGenerating && dimensions.length === 0 && (
        <p className="theme-wizard-empty-hint">
          尚未生成量表，可点「AI 生成」或「添加维度」
        </p>
      )}
      {dimensions.map((dim, index) => (
        <div key={`${dim.key}-${index}`} className="theme-wizard-rubric-row">
          <label>
            键
            <input
              value={dim.key}
              onChange={(event) =>
                patchRubricDimension(index, { key: event.target.value })
              }
            />
          </label>
          <label>
            名称
            <input
              value={dim.label}
              onChange={(event) =>
                patchRubricDimension(index, { label: event.target.value })
              }
            />
          </label>
          <label>
            权重
            <input
              type="number"
              min={1}
              max={100}
              value={dim.weight}
              onChange={(event) =>
                patchRubricDimension(index, {
                  weight: Number(event.target.value) || 0,
                })
              }
            />
          </label>
          <label className="theme-wizard-rubric-desc">
            释义
            <input
              value={dim.description}
              onChange={(event) =>
                patchRubricDimension(index, { description: event.target.value })
              }
            />
          </label>
          <button
            type="button"
            className="ghost-button"
            disabled={dimensions.length <= minRubricDims}
            onClick={() => removeRubricDimension(index)}
          >
            删除
          </button>
        </div>
      ))}
      <label>
        整体打分指引（可选）
        <input
          value={briefing.scoring_rubric?.scoreGuidance || ""}
          placeholder="例如：触碰不做范围整体压低"
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              scoring_rubric: {
                version: current.scoring_rubric?.version || 1,
                generatedAt: current.scoring_rubric?.generatedAt,
                dimensions: current.scoring_rubric?.dimensions || [],
                scoreGuidance: event.target.value,
              },
            }))
          }
        />
      </label>
      <p className="theme-wizard-hint">
        当前权重之和：
        {dimensions.reduce((sum, dim) => sum + (Number(dim.weight) || 0), 0)}
        {" / 100"}
      </p>
    </div>
  );
}
