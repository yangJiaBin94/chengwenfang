import { WIZARD_STEP_COUNT } from "./constants";
import type { HotNewsThemeWizardProps } from "./types";
import { useHotNewsThemeWizard } from "./useHotNewsThemeWizard";
import { StepConfirm } from "./steps/StepConfirm";
import { StepIntent } from "./steps/StepIntent";
import { StepNaming } from "./steps/StepNaming";
import { StepRadar } from "./steps/StepRadar";
import { StepRubric } from "./steps/StepRubric";

/**
 * 热点主题分区创建向导：基本信息 → 意图与策略 → 采集范围 → 评分量表 → 确认启用。
 *
 * @param props 向导入参
 */
export function HotNewsThemeWizard(props: HotNewsThemeWizardProps) {
  const model = useHotNewsThemeWizard(props);
  const {
    step,
    setStep,
    stepTitle,
    theme,
    editingEnabledTheme,
    busy,
    resumeLoading,
    rubricGenerating,
    onCancel,
    goNext,
    commit,
  } = model;

  return (
    <section className="theme-wizard hot-news-theme-wizard">
      <div className="context-line">
        {editingEnabledTheme
          ? "热点资讯模式 / 主题配置"
          : "热点资讯模式 / 新建主题分区"}
      </div>
      <header className="theme-wizard-header">
        <div>
          <span className="section-kicker">Hot News Theme</span>
          <h2>
            {theme?.name || (editingEnabledTheme ? "主题配置" : "新建主题分区")}
          </h2>
          <p>
            步骤 {step} / {WIZARD_STEP_COUNT}：{stepTitle}
            {editingEnabledTheme
              ? "。保存后将刷新 SearXNG 并按意图同步沉淀池渠道；手工与 AI 补源渠道会保留。"
              : "。先确认主要意图与采集策略，再配置搜索词与评分量表；渠道将在启用时自动配置。"}
          </p>
        </div>
        <button type="button" className="ghost-button" onClick={onCancel} disabled={busy}>
          取消
        </button>
      </header>

      {step === 1 && (
        <StepNaming briefing={model.briefing} setBriefing={model.setBriefing} />
      )}
      {step === 2 && (
        <StepIntent
          briefing={model.briefing}
          setBriefing={model.setBriefing}
          busy={model.busy}
          intentGenerating={model.intentGenerating}
          strategyGenerating={model.strategyGenerating}
          generateIntentByAi={model.generateIntentByAi}
          generateStrategyByAi={model.generateStrategyByAi}
          confirmIntent={model.confirmIntent}
        />
      )}
      {step === 3 && (
        <StepRadar
          briefing={model.briefing}
          searchQueries={model.searchQueries}
          setSearchQueries={model.setSearchQueries}
          queryInput={model.queryInput}
          setQueryInput={model.setQueryInput}
          busy={model.busy}
          suggesting={model.suggesting}
          addQueryChip={model.addQueryChip}
          generateQueriesByAi={model.generateQueriesByAi}
        />
      )}
      {step === 4 && (
        <StepRubric
          briefing={model.briefing}
          setBriefing={model.setBriefing}
          busy={model.busy}
          rubricGenerating={model.rubricGenerating}
          generateRubricByAi={model.generateRubricByAi}
          patchRubricDimension={model.patchRubricDimension}
          removeRubricDimension={model.removeRubricDimension}
          addRubricDimension={model.addRubricDimension}
          minRubricDims={model.minRubricDims}
          maxRubricDims={model.maxRubricDims}
        />
      )}
      {step === 5 && (
        <StepConfirm briefing={model.briefing} searchQueries={model.searchQueries} />
      )}

      <footer className="theme-wizard-footer">
        {step > 1 && (
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={() => setStep((current) => Math.max(1, current - 1))}
          >
            上一步
          </button>
        )}
        {step < WIZARD_STEP_COUNT ? (
          <button
            type="button"
            className="primary-button"
            disabled={busy || resumeLoading || rubricGenerating}
            onClick={() => void goNext()}
          >
            {busy ? "保存中…" : "下一步"}
          </button>
        ) : (
          <button
            type="button"
            className="primary-button"
            disabled={busy || resumeLoading}
            onClick={() => void commit()}
          >
            {busy
              ? editingEnabledTheme
                ? "保存中…"
                : "启用中…"
              : editingEnabledTheme
                ? "保存配置"
                : "启用主题"}
          </button>
        )}
      </footer>
    </section>
  );
}
