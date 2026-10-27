import { DOMAIN_OPTIONS } from "../constants";
import type { ThemeWizardModel } from "../useThemeWizard";

type StepNamingProps = Pick<ThemeWizardModel, "briefing" | "setBriefing">;

/**
 * 向导第 1 步：定位与命名。
 *
 * @param props 命名相关状态
 */
export function StepNaming({ briefing, setBriefing }: StepNamingProps) {
  return (
    <div className="wizard-panel">
      <label>
        主题名称
        <input
          value={briefing.naming.name}
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              naming: { ...current.naming, name: event.target.value },
            }))
          }
          placeholder="例如：产品经理的 AI 实战"
        />
      </label>
      <label>
        一句话卖点
        <input
          value={briefing.naming.description}
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              naming: { ...current.naming, description: event.target.value },
            }))
          }
          placeholder="面向谁、解决什么"
        />
      </label>
      <fieldset>
        <legend>领域</legend>
        <div className="chip-row">
          {DOMAIN_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              className={briefing.naming.domain === option.value ? "chip selected" : "chip"}
              onClick={() =>
                setBriefing((current) => ({
                  ...current,
                  naming: { ...current.naming, domain: option.value },
                }))
              }
            >
              {option.label}
            </button>
          ))}
        </div>
        {briefing.naming.domain === "custom" && (
          <input
            value={briefing.naming.domain_custom}
            onChange={(event) =>
              setBriefing((current) => ({
                ...current,
                naming: { ...current.naming, domain_custom: event.target.value },
              }))
            }
            placeholder="自定义领域"
          />
        )}
      </fieldset>
    </div>
  );
}
