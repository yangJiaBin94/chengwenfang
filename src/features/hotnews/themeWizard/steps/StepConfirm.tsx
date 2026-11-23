import { HOT_NEWS_TOPIC_FRESHNESS_WINDOW_DAYS } from "../../freshness";
import { COLLECT_STRATEGY_OPTIONS } from "../constants";
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
          <strong>话题素材时效性</strong>：
          {briefing.collect_strategy.require_topic_freshness !== false
            ? `开启（发布日须在近 ${HOT_NEWS_TOPIC_FRESHNESS_WINDOW_DAYS} 天内）`
            : "关闭（不强制时效硬闸）"}
        </li>
        <li>
          <strong>关键词</strong>：{keywords}
        </li>
        <li>
          <strong>渠道配置</strong>
          ：启用后自动配置 SearXNG、沉淀池匹配渠道，并尝试 AI 推荐补源；之后可在来源管理调整。
        </li>
        <li>
          <strong>评分维度</strong>：
          {(briefing.scoring_rubric?.dimensions || [])
            .map((dim) => `${dim.label}(${dim.weight}%)`)
            .join(" · ") || "未配置"}
        </li>
      </ul>
      <p>确认后主题将启用并自动配源；AI 补源失败时可在来源管理中重试「AI 生成」。</p>
    </div>
  );
}
