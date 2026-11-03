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
        我想用这个主题干什么（建议填写，供 AI 分析意图）
        <textarea
          rows={3}
          value={briefing.naming.description}
          placeholder="例如：想看 A 股新股涨势、火热板块走势、大公司融资/政策对股价影响，以及炒股大牛最新言论"
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
