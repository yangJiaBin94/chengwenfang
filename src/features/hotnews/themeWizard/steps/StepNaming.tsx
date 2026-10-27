import { DOMAIN_OPTIONS } from "../constants";
import type { HotNewsThemeWizardModel } from "../useHotNewsThemeWizard";

type StepNamingProps = Pick<HotNewsThemeWizardModel, "briefing" | "setBriefing">;

/**
 * 向导第 1 步：主题名称、说明与领域标签。
 *
 * @param props 命名相关状态
 */
export function StepNaming({ briefing, setBriefing }: StepNamingProps) {
  return (
    <div className="theme-wizard-panel">
      <label>
        主题名称*
        <input
          value={briefing.naming.name}
          placeholder="如：AI 前沿热点"
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              naming: { ...current.naming, name: event.target.value },
            }))
          }
        />
      </label>
      <label>
        主题说明（可选）
        <input
          value={briefing.naming.description}
          placeholder="这个主题关注什么"
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              naming: { ...current.naming, description: event.target.value },
            }))
          }
        />
      </label>
      <p>领域标签（可选）</p>
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
        <label>
          自定义领域
          <input
            value={briefing.naming.domain_custom}
            onChange={(event) =>
              setBriefing((current) => ({
                ...current,
                naming: { ...current.naming, domain_custom: event.target.value },
              }))
            }
          />
        </label>
      )}
    </div>
  );
}
