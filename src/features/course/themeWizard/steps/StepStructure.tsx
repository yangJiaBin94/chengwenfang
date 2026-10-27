import { FORM_OPTIONS, LESSON_COUNT_OPTIONS, SERIES_COUNT_OPTIONS } from "../constants";
import { toggleInList } from "../briefingUtils";
import type { ThemeWizardModel } from "../useThemeWizard";

type StepStructureProps = Pick<ThemeWizardModel, "briefing" | "setBriefing">;

/**
 * 向导第 5 步：结构偏好。
 *
 * @param props 结构相关状态
 */
export function StepStructure({ briefing, setBriefing }: StepStructureProps) {
  return (
    <div className="wizard-panel">
      <fieldset>
        <legend>系列数量</legend>
        <div className="chip-row">
          {SERIES_COUNT_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.structure.series_count_pref === option.value
                  ? "chip selected"
                  : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  structure: { ...current.structure, series_count_pref: option.value },
                }))
              }
            >
              {option.label}
            </button>
          ))}
        </div>
      </fieldset>
      <fieldset>
        <legend>每系列课次数</legend>
        <div className="chip-row">
          {LESSON_COUNT_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.structure.lessons_per_series_pref === option.value
                  ? "chip selected"
                  : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  structure: {
                    ...current.structure,
                    lessons_per_series_pref: option.value,
                  },
                }))
              }
            >
              {option.label}
            </button>
          ))}
        </div>
      </fieldset>
      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={briefing.structure.need_overview_lessons}
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              structure: {
                ...current.structure,
                need_overview_lessons: event.target.checked,
              },
            }))
          }
        />
        <span>需要总览课 / 阶段小结课</span>
      </label>
      <fieldset>
        <legend>单课形态</legend>
        <div className="chip-row">
          {FORM_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.structure.lesson_forms.includes(option.value)
                  ? "chip selected"
                  : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  structure: {
                    ...current.structure,
                    lesson_forms: toggleInList(
                      current.structure.lesson_forms,
                      option.value,
                    ),
                  },
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
