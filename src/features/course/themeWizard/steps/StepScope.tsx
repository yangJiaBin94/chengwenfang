import { CURVE_OPTIONS } from "../constants";
import type { ThemeWizardModel } from "../useThemeWizard";

type StepScopeProps = Pick<
  ThemeWizardModel,
  | "briefing"
  | "setBriefing"
  | "coverInput"
  | "setCoverInput"
  | "outScopeInput"
  | "setOutScopeInput"
>;

/**
 * 向导第 4 步：范围与深度。
 *
 * @param props 范围相关状态
 */
export function StepScope({
  briefing,
  setBriefing,
  coverInput,
  setCoverInput,
  outScopeInput,
  setOutScopeInput,
}: StepScopeProps) {
  return (
    <div className="wizard-panel">
      <label>
        必须覆盖的模块（回车添加）
        <div className="chip-row">
          {briefing.scope.must_cover.map((item) => (
            <button
              key={item}
              type="button"
              className="chip selected"
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  scope: {
                    ...current.scope,
                    must_cover: current.scope.must_cover.filter((entry) => entry !== item),
                  },
                }))
              }
            >
              {item} ×
            </button>
          ))}
        </div>
        <input
          value={coverInput}
          onChange={(event) => setCoverInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && coverInput.trim()) {
              event.preventDefault();
              setBriefing((current) => ({
                ...current,
                scope: {
                  ...current.scope,
                  must_cover: [...current.scope.must_cover, coverInput.trim()],
                },
              }));
              setCoverInput("");
            }
          }}
          placeholder="例如：提示词工程"
        />
      </label>
      <label>
        明确不做（可选）
        <div className="chip-row">
          {briefing.scope.out_of_scope.map((item) => (
            <button
              key={item}
              type="button"
              className="chip"
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  scope: {
                    ...current.scope,
                    out_of_scope: current.scope.out_of_scope.filter(
                      (entry) => entry !== item,
                    ),
                  },
                }))
              }
            >
              {item} ×
            </button>
          ))}
        </div>
        <input
          value={outScopeInput}
          onChange={(event) => setOutScopeInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && outScopeInput.trim()) {
              event.preventDefault();
              setBriefing((current) => ({
                ...current,
                scope: {
                  ...current.scope,
                  out_of_scope: [...current.scope.out_of_scope, outScopeInput.trim()],
                },
              }));
              setOutScopeInput("");
            }
          }}
        />
      </label>
      <fieldset>
        <legend>难度曲线</legend>
        <div className="chip-row">
          {CURVE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={
                briefing.scope.difficulty_curve === option.value ? "chip selected" : "chip"
              }
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  scope: { ...current.scope, difficulty_curve: option.value },
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
