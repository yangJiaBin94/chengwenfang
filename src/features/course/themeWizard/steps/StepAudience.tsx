import { PREREQ_OPTIONS, READER_OPTIONS, TIME_OPTIONS } from "../constants";
import { toggleInList } from "../briefingUtils";
import type { ThemeWizardModel } from "../useThemeWizard";

type StepAudienceProps = Pick<ThemeWizardModel, "briefing" | "setBriefing">;

/**
 * 向导第 2 步：受众与基础。
 *
 * @param props 受众相关状态
 */
export function StepAudience({ briefing, setBriefing }: StepAudienceProps) {
  return (
    <div className="wizard-panel">
      <fieldset>
        <legend>目标读者</legend>
        <div className="chip-row">
          {READER_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.audience.readers.includes(option.value) ? "chip selected" : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  audience: {
                    ...current.audience,
                    readers: toggleInList(current.audience.readers, option.value),
                  },
                }))
              }
            >
              {option.label}
            </button>
          ))}
        </div>
      </fieldset>
      <label>
        补充说明
        <input
          value={briefing.audience.readers_note}
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              audience: { ...current.audience, readers_note: event.target.value },
            }))
          }
        />
      </label>
      <fieldset>
        <legend>先备知识</legend>
        <div className="chip-row">
          {PREREQ_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.audience.prerequisites.includes(option.value)
                  ? "chip selected"
                  : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  audience: {
                    ...current.audience,
                    prerequisites: toggleInList(
                      current.audience.prerequisites,
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
      <fieldset>
        <legend>预计投入</legend>
        <div className="chip-row">
          {TIME_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.audience.time_budget === option.value ? "chip selected" : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  audience: { ...current.audience, time_budget: option.value },
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
