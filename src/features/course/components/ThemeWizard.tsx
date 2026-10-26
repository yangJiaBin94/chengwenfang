import { useEffect, useMemo, useRef, useState } from "react";
import {
  commitThemeCurriculum,
  createThemeDraft,
  generateThemeCurriculum,
  getContentTheme,
  getCurriculumDraft,
  patchThemeBriefing,
  saveCurriculumDraft,
  themePathSlug,
  waitForBackgroundJob,
} from "../api";
import type {
  ContentThemeData,
  CurriculumDraftData,
  CurriculumSeriesDraft,
  JobEventData,
} from "../api";

type ThemeWizardProps = {
  modeId: string;
  themeId?: string | null;
  onCancel: () => void;
  onCompleted: (theme: ContentThemeData) => void;
  showNotice: (title: string, detail: string) => void;
};

const DOMAIN_OPTIONS = [
  { value: "ai_intro", label: "AI 入门" },
  { value: "programming", label: "编程" },
  { value: "career_writing", label: "职场写作" },
  { value: "custom", label: "自定义" },
];

const READER_OPTIONS = [
  { value: "zero_basis", label: "零基础" },
  { value: "experienced", label: "有相关经验" },
  { value: "career_switch", label: "转行" },
  { value: "on_job", label: "在职提升" },
];

const PREREQ_OPTIONS = [
  { value: "none", label: "无" },
  { value: "computer", label: "会用电脑" },
  { value: "touched_domain", label: "接触过相关领域" },
];

const TIME_OPTIONS = [
  { value: "light_2w", label: "2 周轻量" },
  { value: "system_1_2m", label: "1–2 月系统" },
  { value: "long_term", label: "长期体系" },
];

const SUCCESS_OPTIONS = [
  { value: "project", label: "能独立完成小项目" },
  { value: "understand", label: "能看懂原理" },
  { value: "publish", label: "能对外输出内容" },
];

const CURVE_OPTIONS = [
  { value: "gentle", label: "平缓入门" },
  { value: "easy_to_hard", label: "先易后难" },
  { value: "project_spiral", label: "项目驱动螺旋" },
];

const SERIES_COUNT_OPTIONS = [
  { value: "3_5", label: "3–5 个系列" },
  { value: "5_8", label: "5–8 个系列" },
  { value: "custom", label: "自定义" },
];

const LESSON_COUNT_OPTIONS = [
  { value: "3_5", label: "每系列 3–5 课" },
  { value: "5_8", label: "每系列 5–8 课" },
  { value: "custom", label: "自定义" },
];

const FORM_OPTIONS = [
  { value: "concept", label: "概念讲解" },
  { value: "practice", label: "动手练习" },
  { value: "case", label: "案例拆解" },
  { value: "tool", label: "工具实操" },
];

const TONE_OPTIONS = [
  { value: "textbook", label: "严谨教材" },
  { value: "companion", label: "口语陪跑" },
  { value: "story", label: "故事化" },
];

const LENGTH_OPTIONS = [
  { value: "short", label: "短平快" },
  { value: "standard", label: "标准长文" },
  { value: "deep", label: "深度长文" },
];

type BriefingState = {
  naming: {
    name: string;
    description: string;
    code: string;
    domain: string;
    domain_custom: string;
  };
  audience: {
    readers: string[];
    readers_note: string;
    prerequisites: string[];
    time_budget: string;
  };
  goals: {
    outcomes: string[];
    success_criteria: string;
  };
  scope: {
    must_cover: string[];
    out_of_scope: string[];
    difficulty_curve: string;
  };
  structure: {
    series_count_pref: string;
    series_count_custom: number | null;
    lessons_per_series_pref: string;
    lessons_per_series_custom: number | null;
    need_overview_lessons: boolean;
    lesson_forms: string[];
  };
  expression: {
    tone: string;
    length: string;
    boundaries: string;
  };
};

function emptyBriefing(): BriefingState {
  return {
    naming: {
      name: "",
      description: "",
      code: "",
      domain: "ai_intro",
      domain_custom: "",
    },
    audience: {
      readers: ["zero_basis"],
      readers_note: "",
      prerequisites: ["none"],
      time_budget: "system_1_2m",
    },
    goals: {
      outcomes: ["", "", ""],
      success_criteria: "understand",
    },
    scope: {
      must_cover: [],
      out_of_scope: [],
      difficulty_curve: "easy_to_hard",
    },
    structure: {
      series_count_pref: "3_5",
      series_count_custom: null,
      lessons_per_series_pref: "3_5",
      lessons_per_series_custom: null,
      need_overview_lessons: true,
      lesson_forms: ["concept", "practice"],
    },
    expression: {
      tone: "companion",
      length: "standard",
      boundaries: "",
    },
  };
}

function toggleInList(list: string[], value: string): string[] {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
}

/**
 * 提问式主题创建向导：问卷 → 生成课表 → 确认落库。
 */
export function ThemeWizard({
  modeId,
  themeId: initialThemeId,
  onCancel,
  onCompleted,
  showNotice,
}: ThemeWizardProps) {
  const [step, setStep] = useState(1);
  const [themeId, setThemeId] = useState<string | null>(initialThemeId || null);
  const [theme, setTheme] = useState<ContentThemeData | null>(null);
  const [briefing, setBriefing] = useState<BriefingState>(emptyBriefing());
  const [draft, setDraft] = useState<CurriculumDraftData | null>(null);
  const [busy, setBusy] = useState(false);
  const [coverInput, setCoverInput] = useState("");
  const [outScopeInput, setOutScopeInput] = useState("");
  const [statusText, setStatusText] = useState("");
  /** 稳定引用，避免父组件重渲染导致草稿加载 effect 误触发。 */
  const showNoticeRef = useRef(showNotice);
  showNoticeRef.current = showNotice;

  const stepTitle = useMemo(() => {
    const titles = [
      "",
      "定位与命名",
      "受众与基础",
      "学习目标",
      "范围与深度",
      "结构偏好",
      "表达与约束",
      "确认课表",
    ];
    return titles[step] || "";
  }, [step]);

  useEffect(() => {
    if (!initialThemeId) {
      return;
    }
    void (async () => {
      try {
        const loaded = await getContentTheme(initialThemeId);
        setTheme(loaded);
        setThemeId(loaded.id);
        if (loaded.briefingJson) {
          try {
            const parsed = JSON.parse(loaded.briefingJson) as Partial<BriefingState> & {
              step?: number;
            };
            setBriefing((prev) => ({
              ...prev,
              ...parsed,
              naming: { ...prev.naming, ...(parsed.naming || {}) },
              audience: { ...prev.audience, ...(parsed.audience || {}) },
              goals: { ...prev.goals, ...(parsed.goals || {}) },
              scope: { ...prev.scope, ...(parsed.scope || {}) },
              structure: { ...prev.structure, ...(parsed.structure || {}) },
              expression: { ...prev.expression, ...(parsed.expression || {}) },
            }));
            if (typeof parsed.step === "number" && parsed.step >= 1 && parsed.step <= 7) {
              setStep(parsed.step);
            }
          } catch {
            /* ignore */
          }
        }
        if (
          loaded.status === "ready_for_review" ||
          (loaded.curriculumDraftJson && loaded.curriculumDraftJson.length > 10)
        ) {
          const curriculum = await getCurriculumDraft(loaded.id);
          setDraft(curriculum);
          setStep(7);
        }
      } catch (error) {
        showNoticeRef.current(
          "加载失败",
          error instanceof Error ? error.message : "无法加载主题草稿",
        );
      }
    })();
  }, [initialThemeId]);

  async function ensureDraftTheme() {
    if (themeId) {
      return themeId;
    }
    setBusy(true);
    try {
      // 编码不传，由后端按主题名自动生成并避重
      const created = await createThemeDraft(modeId, {
        name: briefing.naming.name || "未命名主题",
        description: briefing.naming.description,
      });
      setTheme(created);
      setThemeId(created.id);
      setBriefing((current) => ({
        ...current,
        naming: { ...current.naming, code: created.code },
      }));
      return created.id;
    } finally {
      setBusy(false);
    }
  }

  async function saveCurrentStep(nextStep: number) {
    const id = await ensureDraftTheme();
    setBusy(true);
    try {
      const payload = {
        ...briefing,
        schema_version: 1,
        step: nextStep,
        goals: {
          ...briefing.goals,
          outcomes: briefing.goals.outcomes.filter((item) => item.trim()),
        },
      };
      const updated = await patchThemeBriefing(id, {
        briefing: payload as unknown as Record<string, unknown>,
        name: briefing.naming.name,
        description: briefing.naming.description,
        code: theme?.code || briefing.naming.code || undefined,
      });
      setTheme(updated);
      setThemeId(updated.id);
      setStep(nextStep);
    } catch (error) {
      showNotice("保存失败", error instanceof Error ? error.message : "问卷保存失败");
    } finally {
      setBusy(false);
    }
  }

  async function runGenerate() {
    const id = await ensureDraftTheme();
    setBusy(true);
    setStatusText("正在提交生成任务…");
    try {
      await patchThemeBriefing(id, {
        briefing: {
          ...briefing,
          schema_version: 1,
          step: 7,
          goals: {
            ...briefing.goals,
            outcomes: briefing.goals.outcomes.filter((item) => item.trim()),
          },
        } as unknown as Record<string, unknown>,
        name: briefing.naming.name,
        description: briefing.naming.description,
        code: theme?.code || briefing.naming.code || undefined,
      });
      const enqueued = await generateThemeCurriculum(id);
      setStatusText("生成中，请稍候（通常 1–2 分钟）…");
      await waitForBackgroundJob(enqueued.jobId, (event: JobEventData) => {
        const stepLabel = event.message || event.step || event.eventType;
        setStatusText(`生成中… ${stepLabel}`);
      });
      setStatusText("正在加载课表草案…");
      const curriculum = await getCurriculumDraft(id);
      const series = curriculum?.series ?? [];
      if (!series.length) {
        throw new Error("课表草案为空，请重试生成");
      }
      setDraft({
        schemaVersion: curriculum.schemaVersion || 1,
        routeNarrative: curriculum.routeNarrative || "",
        series,
      });
      setStep(7);
      setStatusText("");
      showNotice("生成成功", `已生成 ${series.length} 个系列，请确认后创建主题`);
    } catch (error) {
      showNotice("生成失败", error instanceof Error ? error.message : "课表生成失败");
      setStatusText("");
    } finally {
      setBusy(false);
    }
  }

  async function handleCommit() {
    if (!themeId || !draft) {
      return;
    }
    setBusy(true);
    try {
      const saved = await saveCurriculumDraft(themeId, draft);
      setDraft(saved);
      await commitThemeCurriculum(themeId);
      const latest = await getContentTheme(themeId);
      showNotice("主题已创建", `已落库 ${latest.seriesCount} 个系列、${latest.courseCount} 门课`);
      onCompleted(latest);
    } catch (error) {
      showNotice("确认失败", error instanceof Error ? error.message : "课表确认失败");
    } finally {
      setBusy(false);
    }
  }

  function updateSeries(index: number, patch: Partial<CurriculumSeriesDraft>) {
    if (!draft) return;
    const series = draft.series.map((item, i) => (i === index ? { ...item, ...patch } : item));
    setDraft({ ...draft, series });
  }

  function updateLesson(
    seriesIndex: number,
    lessonIndex: number,
    patch: Partial<CurriculumSeriesDraft["lessons"][number]>,
  ) {
    if (!draft) return;
    const series = draft.series.map((item, i) => {
      if (i !== seriesIndex) return item;
      const lessons = item.lessons.map((lesson, j) =>
        j === lessonIndex ? { ...lesson, ...patch } : lesson,
      );
      return { ...item, lessons };
    });
    setDraft({ ...draft, series });
  }

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
        <div className="wizard-panel">
          <label>
            主题名称
            <input
              value={briefing.naming.name}
              onChange={(e) =>
                setBriefing((b) => ({
                  ...b,
                  naming: { ...b.naming, name: e.target.value },
                }))
              }
              placeholder="例如：产品经理的 AI 实战"
            />
          </label>
          <label>
            一句话卖点
            <input
              value={briefing.naming.description}
              onChange={(e) =>
                setBriefing((b) => ({
                  ...b,
                  naming: { ...b.naming, description: e.target.value },
                }))
              }
              placeholder="面向谁、解决什么"
            />
          </label>
          <fieldset>
            <legend>领域</legend>
            <div className="chip-row">
              {DOMAIN_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={briefing.naming.domain === opt.value ? "chip selected" : "chip"}
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      naming: { ...b.naming, domain: opt.value },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
            {briefing.naming.domain === "custom" && (
              <input
                value={briefing.naming.domain_custom}
                onChange={(e) =>
                  setBriefing((b) => ({
                    ...b,
                    naming: { ...b.naming, domain_custom: e.target.value },
                  }))
                }
                placeholder="自定义领域"
              />
            )}
          </fieldset>
        </div>
      )}

      {step === 2 && (
        <div className="wizard-panel">
          <fieldset>
            <legend>目标读者</legend>
            <div className="chip-row">
              {READER_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.audience.readers.includes(opt.value) ? "chip selected" : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      audience: {
                        ...b.audience,
                        readers: toggleInList(b.audience.readers, opt.value),
                      },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
          <label>
            补充说明
            <input
              value={briefing.audience.readers_note}
              onChange={(e) =>
                setBriefing((b) => ({
                  ...b,
                  audience: { ...b.audience, readers_note: e.target.value },
                }))
              }
            />
          </label>
          <fieldset>
            <legend>先备知识</legend>
            <div className="chip-row">
              {PREREQ_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.audience.prerequisites.includes(opt.value)
                      ? "chip selected"
                      : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      audience: {
                        ...b.audience,
                        prerequisites: toggleInList(b.audience.prerequisites, opt.value),
                      },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend>预计投入</legend>
            <div className="chip-row">
              {TIME_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.audience.time_budget === opt.value ? "chip selected" : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      audience: { ...b.audience, time_budget: opt.value },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
        </div>
      )}

      {step === 3 && (
        <div className="wizard-panel">
          {[0, 1, 2].map((index) => (
            <label key={index}>
              学完后能做到 {index + 1}
              <input
                value={briefing.goals.outcomes[index] || ""}
                onChange={(e) => {
                  const outcomes = [...briefing.goals.outcomes];
                  outcomes[index] = e.target.value;
                  setBriefing((b) => ({
                    ...b,
                    goals: { ...b.goals, outcomes },
                  }));
                }}
              />
            </label>
          ))}
          <fieldset>
            <legend>成功标准</legend>
            <div className="chip-row">
              {SUCCESS_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.goals.success_criteria === opt.value ? "chip selected" : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      goals: { ...b.goals, success_criteria: opt.value },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
        </div>
      )}

      {step === 4 && (
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
                    setBriefing((b) => ({
                      ...b,
                      scope: {
                        ...b.scope,
                        must_cover: b.scope.must_cover.filter((x) => x !== item),
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
              onChange={(e) => setCoverInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && coverInput.trim()) {
                  e.preventDefault();
                  setBriefing((b) => ({
                    ...b,
                    scope: {
                      ...b.scope,
                      must_cover: [...b.scope.must_cover, coverInput.trim()],
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
                    setBriefing((b) => ({
                      ...b,
                      scope: {
                        ...b.scope,
                        out_of_scope: b.scope.out_of_scope.filter((x) => x !== item),
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
              onChange={(e) => setOutScopeInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && outScopeInput.trim()) {
                  e.preventDefault();
                  setBriefing((b) => ({
                    ...b,
                    scope: {
                      ...b.scope,
                      out_of_scope: [...b.scope.out_of_scope, outScopeInput.trim()],
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
              {CURVE_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.scope.difficulty_curve === opt.value ? "chip selected" : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      scope: { ...b.scope, difficulty_curve: opt.value },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
        </div>
      )}

      {step === 5 && (
        <div className="wizard-panel">
          <fieldset>
            <legend>系列数量</legend>
            <div className="chip-row">
              {SERIES_COUNT_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.structure.series_count_pref === opt.value
                      ? "chip selected"
                      : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      structure: { ...b.structure, series_count_pref: opt.value },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend>每系列课次数</legend>
            <div className="chip-row">
              {LESSON_COUNT_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.structure.lessons_per_series_pref === opt.value
                      ? "chip selected"
                      : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      structure: { ...b.structure, lessons_per_series_pref: opt.value },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={briefing.structure.need_overview_lessons}
              onChange={(e) =>
                setBriefing((b) => ({
                  ...b,
                  structure: { ...b.structure, need_overview_lessons: e.target.checked },
                }))
              }
            />
            <span>需要总览课 / 阶段小结课</span>
          </label>
          <fieldset>
            <legend>单课形态</legend>
            <div className="chip-row">
              {FORM_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.structure.lesson_forms.includes(opt.value)
                      ? "chip selected"
                      : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      structure: {
                        ...b.structure,
                        lesson_forms: toggleInList(b.structure.lesson_forms, opt.value),
                      },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
        </div>
      )}

      {step === 6 && (
        <div className="wizard-panel">
          <fieldset>
            <legend>语气风格</legend>
            <div className="chip-row">
              {TONE_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.expression.tone === opt.value ? "chip selected" : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      expression: { ...b.expression, tone: opt.value },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
          <fieldset>
            <legend>篇幅倾向</legend>
            <div className="chip-row">
              {LENGTH_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  className={
                    briefing.expression.length === opt.value ? "chip selected" : "chip"
                  }
                  onClick={() =>
                    setBriefing((b) => ({
                      ...b,
                      expression: { ...b.expression, length: opt.value },
                    }))
                  }
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </fieldset>
          <label>
            敏感边界或必须遵守的表述
            <textarea
              rows={3}
              value={briefing.expression.boundaries}
              onChange={(e) =>
                setBriefing((b) => ({
                  ...b,
                  expression: { ...b.expression, boundaries: e.target.value },
                }))
              }
            />
          </label>
        </div>
      )}

      {step === 7 && draft && (
        <div className="wizard-panel curriculum-review">
          <label>
            路线说明
            <textarea
              rows={3}
              value={draft.routeNarrative || ""}
              onChange={(e) => setDraft({ ...draft, routeNarrative: e.target.value })}
            />
          </label>
          {draft.series.map((series, sIndex) => (
            <div className="curriculum-series-block" key={series.tempId || sIndex}>
              <input
                className="series-name-input"
                value={series.name}
                onChange={(e) => updateSeries(sIndex, { name: e.target.value })}
              />
              <input
                value={series.positioning || ""}
                onChange={(e) => updateSeries(sIndex, { positioning: e.target.value })}
                placeholder="系列定位"
              />
              {series.lessons.map((lesson, lIndex) => (
                <div className="curriculum-lesson-row" key={lIndex}>
                  <span>第 {lesson.lessonNo} 课</span>
                  <input
                    value={lesson.title}
                    onChange={(e) => updateLesson(sIndex, lIndex, { title: e.target.value })}
                  />
                  <input
                    value={lesson.summary || ""}
                    onChange={(e) =>
                      updateLesson(sIndex, lIndex, { summary: e.target.value })
                    }
                    placeholder="摘要"
                  />
                </div>
              ))}
            </div>
          ))}
        </div>
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
