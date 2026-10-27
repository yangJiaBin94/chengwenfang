import { LENGTH_OPTIONS, TONE_OPTIONS } from "../constants";
import type { ThemeWizardModel } from "../useThemeWizard";

type StepExpressionProps = Pick<ThemeWizardModel, "briefing" | "setBriefing">;

/**
 * 向导第 6 步：表达与约束。
 *
 * @param props 表达相关状态
 */
export function StepExpression({ briefing, setBriefing }: StepExpressionProps) {
  return (
    <div className="wizard-panel">
      <fieldset>
        <legend>语气风格</legend>
        <div className="chip-row">
          {TONE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.expression.tone === option.value ? "chip selected" : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  expression: { ...current.expression, tone: option.value },
                }))
              }
            >
              {option.label}
            </button>
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend>篇幅倾向</legend>
        <div className="chip-row">
          {LENGTH_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.expression.length === option.value ? "chip selected" : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  expression: { ...current.expression, length: option.value },
                }))
              }
            >
              {option.label}
            </button>
          ))}
        </div>
      </fieldset>
      <label>
        敏感边界或必须遵守的表述
        <textarea
          rows={3}
          value={briefing.expression.boundaries}
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              expression: { ...current.expression, boundaries: event.target.value },
            }))
          }
        />
      </label>
    </div>
  );
}
