import { themePathSlug } from "../api";
import type { ThemeWizardProps } from "./types";
import { useThemeWizard } from "./useThemeWizard";
import { StepAudience } from "./steps/StepAudience";
import { StepCurriculum } from "./steps/StepCurriculum";
import { StepExpression } from "./steps/StepExpression";
import { StepGoals } from "./steps/StepGoals";
import { StepNaming } from "./steps/StepNaming";
import { StepScope } from "./steps/StepScope";
import { StepStructure } from "./steps/StepStructure";

/**
 * 提问式主题创建向导：问卷 → 生成课表 → 确认落库。
 *
 * @param props 向导入参
 */
export function ThemeWizard(props: ThemeWizardProps) {
  const model = useThemeWizard(props);
  const {
    step,
    setStep,
    stepTitle,
    theme,
    briefing,
    busy,
    statusText,
    onCancel,
    saveCurrentStep,
    runGenerate,
    handleCommit,
  } = model;

  return (
    <section className="theme-wizard">
      <div className="context-line">课程体系教学模式 / 新建主题</div>
      <header className="theme-wizard-header">
        <div>
          <span className="section-kicker">Guided Theme</span>
          <h2>提问式创建主题</h2>
          <p>
            步骤 {Math.min(step, 6)} / 6{step === 7 ? " → 确认课表" : ""}：{stepTitle}
          </p>
        </div>
        <button type="button" className="ghost-button" onClick={onCancel} disabled={busy}>
          退出
        </button>
      </header>

      {step === 1 && (
        <StepNaming briefing={model.briefing} setBriefing={model.setBriefing} />
      )}
      {step === 2 && (
        <StepAudience briefing={model.briefing} setBriefing={model.setBriefing} />
      )}
      {step === 3 && (
        <StepGoals briefing={model.briefing} setBriefing={model.setBriefing} />
      )}
      {step === 4 && (
        <StepScope
          briefing={model.briefing}
          setBriefing={model.setBriefing}
          coverInput={model.coverInput}
          setCoverInput={model.setCoverInput}
          outScopeInput={model.outScopeInput}
          setOutScopeInput={model.setOutScopeInput}
        />
      )}
      {step === 5 && (
        <StepStructure briefing={model.briefing} setBriefing={model.setBriefing} />
      )}
      {step === 6 && (
        <StepExpression briefing={model.briefing} setBriefing={model.setBriefing} />
      )}
      {step === 7 && (
        <StepCurriculum
          draft={model.draft}
          setDraft={model.setDraft}
          updateSeries={model.updateSeries}
          updateLesson={model.updateLesson}
        />
      )}

      <footer className="wizard-actions">
        {statusText && (
          <span className={`wizard-status${busy ? " is-running" : ""}`}>{statusText}</span>
        )}
        {step > 1 && step < 7 && (
          <button
            type="button"
            className="secondary-button"
            disabled={busy}
            onClick={() => setStep(step - 1)}
          >
            上一步
          </button>
        )}
        {step >= 1 && step <= 5 && (
          <button
            type="button"
            className="primary-button"
            disabled={busy || (step === 1 && !briefing.naming.name.trim())}
            onClick={() => void saveCurrentStep(step + 1)}
          >
            下一步
          </button>
        )}
        {step === 6 && (
          <button
            type="button"
            className="primary-button"
            disabled={busy}
            onClick={() => void runGenerate()}
          >
            {busy ? "生成中…" : "生成课表草案"}
          </button>
        )}
        {step === 7 && (
          <>
            <button
              type="button"
              className="secondary-button"
              disabled={busy}
              onClick={() => void runGenerate()}
            >
              重新生成
            </button>
            <button
              type="button"
              className="primary-button"
              disabled={busy}
              onClick={() => void handleCommit()}
            >
              确认创建主题
            </button>
          </>
        )}
      </footer>
      {theme && (
        <p className="wizard-meta">
          草稿 ID：{theme.id} · 编码：{theme.code} · 路径将为 /themes/
          {themePathSlug(theme.code)}
        </p>
      )}
    </section>
  );
}
