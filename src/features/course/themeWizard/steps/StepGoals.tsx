import { SUCCESS_OPTIONS } from "../constants";
import type { ThemeWizardModel } from "../useThemeWizard";

type StepGoalsProps = Pick<ThemeWizardModel, "briefing" | "setBriefing">;

/**
 * 向导第 3 步：学习目标。
 *
 * @param props 目标相关状态
 */
export function StepGoals({ briefing, setBriefing }: StepGoalsProps) {
  return (
    <div className="wizard-panel">
      {[0, 1, 2].map((index) => (
        <label key={index}>
          学完后能做到 {index + 1}
          <input
            value={briefing.goals.outcomes[index] || ""}
            onChange={(event) => {
              const outcomes = [...briefing.goals.outcomes];
              outcomes[index] = event.target.value;
              setBriefing((current) => ({
                ...current,
                goals: { ...current.goals, outcomes },
              }));
            }}
          />
        </label>
      ))}
      <fieldset>
        <legend>成功标准</legend>
        <div className="chip-row">
          {SUCCESS_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.goals.success_criteria === option.value ? "chip selected" : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  goals: { ...current.goals, success_criteria: option.value },
                }))
              }
            >
              {option.label}
            </button>
          ))}
        </div>
      </fieldset>
    </div>
  );
}
