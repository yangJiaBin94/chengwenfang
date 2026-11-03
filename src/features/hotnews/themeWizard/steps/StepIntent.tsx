import { COLLECT_STRATEGY_OPTIONS } from "../constants";
import { ensureSearxngChannel } from "../briefingUtils";
import type { HotNewsThemeWizardModel } from "../useHotNewsThemeWizard";

type StepIntentProps = Pick<
  HotNewsThemeWizardModel,
  | "briefing"
  | "setBriefing"
  | "busy"
  | "intentGenerating"
  | "strategyGenerating"
  | "generateIntentByAi"
  | "generateStrategyByAi"
  | "confirmIntent"
>;

/**
 * 向导第 2 步：AI 推导主要意图并确认，再选定采集策略。
 *
 * @param props 意图相关状态
 */
export function StepIntent({
  briefing,
  setBriefing,
  busy,
  intentGenerating,
  strategyGenerating,
  generateIntentByAi,
  generateStrategyByAi,
  confirmIntent,
}: StepIntentProps) {
  const intent = briefing.core_intent;
  const strategy = briefing.collect_strategy;

  return (
    <div className="theme-wizard-panel">
      <h3>主要意图</h3>
      <p>
        AI 会根据主题名称与说明提炼「这个主题要带来什么素材」。请核对并确认后，再选采集策略。
      </p>
      <div className="chip-row" style={{ marginBottom: "0.75rem" }}>
        <button
          type="button"
          className="secondary-button"
          disabled={busy || intentGenerating}
          onClick={() => void generateIntentByAi()}
        >
          {intentGenerating ? "分析中…" : "AI 分析意图"}
        </button>
        <button
          type="button"
          className="primary-button"
          disabled={busy || !intent.summary.trim() || intent.confirmed}
          onClick={() => void confirmIntent()}
        >
          {intent.confirmed ? "已确认意图" : "确认此意图"}
        </button>
      </div>
      <label>
        一句话意图*
        <textarea
          rows={2}
          value={intent.summary}
          placeholder="例如：追踪 A 股近期可能影响涨跌的可写事件"
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              core_intent: {
                ...current.core_intent,
                summary: event.target.value,
                confirmed: false,
              },
            }))
          }
        />
      </label>
      <label>
        要的素材（每行一条）
        <textarea
          rows={3}
          value={(intent.material_wants || []).join("\n")}
          placeholder={"新股涨势\n板块走势分析\n大公司融资/政策\n炒股人物最新观点"}
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              core_intent: {
                ...current.core_intent,
                material_wants: event.target.value
                  .split(/\r?\n/)
                  .map((line) => line.trim())
                  .filter(Boolean),
                confirmed: false,
              },
            }))
          }
        />
      </label>
      <label>
        不要的素材（每行一条）
        <textarea
          rows={3}
          value={(intent.material_avoids || []).join("\n")}
          placeholder={"监管机构职能介绍\n百科/官网 About\n纯宏观通稿"}
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              core_intent: {
                ...current.core_intent,
                material_avoids: event.target.value
                  .split(/\r?\n/)
                  .map((line) => line.trim())
                  .filter(Boolean),
                confirmed: false,
              },
            }))
          }
        />
      </label>
      <label>
        好素材长什么样
        <textarea
          rows={2}
          value={intent.success_looks_like}
          placeholder="近几天可核实事件，能看出对板块或股价的含义"
          onChange={(event) =>
            setBriefing((current) => ({
              ...current,
              core_intent: {
                ...current.core_intent,
                success_looks_like: event.target.value,
                confirmed: false,
              },
            }))
          }
        />
      </label>

      <h3 style={{ marginTop: "1.5rem" }}>采集策略</h3>
      <p>AI 可按意图推荐；你也可手动切换。</p>
      <div className="chip-row" style={{ marginBottom: "0.75rem" }}>
        <button
          type="button"
          className="secondary-button"
          disabled={busy || strategyGenerating || !intent.summary.trim()}
          onClick={() => void generateStrategyByAi()}
        >
          {strategyGenerating ? "推荐中…" : "AI 推荐策略"}
        </button>
      </div>
      <div className="chip-row">
        {COLLECT_STRATEGY_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            className={
              strategy.mode === option.value ? "chip selected" : "chip"
            }
            onClick={() =>
              setBriefing((current) => {
                const seedChannels =
                  option.value === "unstructured"
                    ? ensureSearxngChannel(current.radar.seed_channels)
                    : current.radar.seed_channels;
                return {
                  ...current,
                  collect_strategy: {
                    mode: option.value,
                    rationale:
                      current.collect_strategy.mode === option.value
                        ? current.collect_strategy.rationale
                        : option.hint,
                    user_override:
                      Boolean(current.collect_strategy.mode) &&
                      current.collect_strategy.mode !== option.value,
                  },
                  radar: { ...current.radar, seed_channels: seedChannels },
                };
              })
            }
          >
            {option.label}
          </button>
        ))}
      </div>
      {strategy.mode ? (
        <p className="muted">
          当前：{COLLECT_STRATEGY_OPTIONS.find((item) => item.value === strategy.mode)?.label}
          {strategy.rationale ? ` — ${strategy.rationale}` : ""}
        </p>
      ) : (
        <p className="muted">请选择或让 AI 推荐一种策略。</p>
      )}
    </div>
  );
}
