import { COLLECT_STRATEGY_OPTIONS, SEED_CHANNEL_OPTIONS } from "../constants";
import type { HotNewsThemeWizardModel } from "../useHotNewsThemeWizard";

type StepConfirmProps = Pick<
  HotNewsThemeWizardModel,
  "briefing" | "searchQueries"
>;

/**
 * 向导最后一步：启用前确认摘要。
 *
 * @param props 确认页展示数据
 */
export function StepConfirm({ briefing, searchQueries }: StepConfirmProps) {
  const seedChannelLabels = briefing.radar.seed_channels
    .map(
      (value) =>
        SEED_CHANNEL_OPTIONS.find((item) => item.value === value)?.label || value,
    )
    .join("、");
  const keywords =
    searchQueries.length || briefing.radar.search_queries.length
      ? (searchQueries.length ? searchQueries : briefing.radar.search_queries).join(
          " · ",
        )
      : briefing.collect_strategy.mode === "unstructured"
        ? "（非结构化：采集时由意图自适应搜索生成）"
        : "无";
  const strategyLabel =
    COLLECT_STRATEGY_OPTIONS.find(
      (item) => item.value === briefing.collect_strategy.mode,
    )?.label || briefing.collect_strategy.mode || "未选";

  return (
    <div className="theme-wizard-panel confirm-panel">
      <h3>确认启用</h3>
      <ul>
        <li>
          <strong>主题</strong>：{briefing.naming.name}
        </li>
        <li>
          <strong>说明</strong>：{briefing.naming.description || "无"}
        </li>
        <li>
          <strong>主要意图</strong>：{briefing.core_intent.summary || "无"}
          {briefing.core_intent.confirmed ? "（已确认）" : "（未确认）"}
        </li>
        <li>
          <strong>要的素材</strong>：
          {(briefing.core_intent.material_wants || []).join(" · ") || "无"}
        </li>
        <li>
          <strong>不要的素材</strong>：
          {(briefing.core_intent.material_avoids || []).join(" · ") || "无"}
        </li>
        <li>
          <strong>采集策略</strong>：{strategyLabel}
          {briefing.collect_strategy.rationale
            ? ` — ${briefing.collect_strategy.rationale}`
            : ""}
        </li>
        <li>
          <strong>关键词</strong>：{keywords}
        </li>
        <li>
          <strong>初始渠道</strong>：{seedChannelLabels || "未选"}
        </li>
        <li>
          <strong>评分维度</strong>：
          {(briefing.scoring_rubric?.dimensions || [])
            .map((dim) => `${dim.label}(${dim.weight}%)`)
            .join(" · ") || "未配置"}
        </li>
      </ul>
      <p>确认后主题将启用，并按清单创建初始搜索渠道；之后可在来源管理中继续调整。</p>
    </div>
  );
}
