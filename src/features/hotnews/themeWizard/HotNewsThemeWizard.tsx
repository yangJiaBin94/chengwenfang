import type { HotNewsThemeWizardProps } from "./types";
import { useHotNewsThemeWizard } from "./useHotNewsThemeWizard";
import { StepConfirm } from "./steps/StepConfirm";
import { StepNaming } from "./steps/StepNaming";
import { StepRadar } from "./steps/StepRadar";
import { StepRubric } from "./steps/StepRubric";

/**
 * 热点主题分区创建向导：基本信息 → 采集范围 → 评分量表 → 确认启用。
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
    busy,
    rubricGenerating,
    onCancel,
    goNext,
    commit,
  } = model;

  return (
    <section className="theme-wizard hot-news-theme-wizard">
      <div className="context-line">热点资讯模式 / 新建主题分区</div>
      <header className="theme-wizard-header">
        <div>
          <span className="section-kicker">Hot News Theme</span>
          <h2>{theme?.name || "新建主题分区"}</h2>
          <p>
            步骤 {step} / 4：{stepTitle}
            。雷达流程通用，主题用于隔离统计、选题素材、文章与来源配置；写作价值按本主题量表打分。
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
        <StepRadar
          briefing={model.briefing}
          setBriefing={model.setBriefing}
          searchQueries={model.searchQueries}
          setSearchQueries={model.setSearchQueries}
          queryInput={model.queryInput}
          setQueryInput={model.setQueryInput}
          busy={model.busy}
          suggesting={model.suggesting}
          githubQueryGenerating={model.githubQueryGenerating}
          addQueryChip={model.addQueryChip}
          generateQueriesByAi={model.generateQueriesByAi}
          generateGithubQueryByAi={model.generateGithubQueryByAi}
        />
      )}
      {step === 3 && (
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
      {step === 4 && (
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
        {step < 4 ? (
          <button
            type="button"
            className="primary-button"
            disabled={busy || rubricGenerating}
            onClick={() => void goNext()}
          >
            {busy ? "保存中…" : "下一步"}
          </button>
        ) : (
          <button
            type="button"
            className="primary-button"
            disabled={busy}
            onClick={() => void commit()}
          >
            {busy ? "启用中…" : "启用主题"}
          </button>
        )}
      </footer>
    </section>
  );
}
