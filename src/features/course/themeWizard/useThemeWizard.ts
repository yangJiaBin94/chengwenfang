import { useEffect, useMemo, useRef, useState } from "react";
import {
  commitThemeCurriculum,
  createThemeDraft,
  generateThemeCurriculum,
  getContentTheme,
  getCurriculumDraft,
  patchThemeBriefing,
  saveCurriculumDraft,
  waitForBackgroundJob,
} from "../api";
import type {
  ContentThemeData,
  CurriculumDraftData,
  CurriculumSeriesDraft,
  JobEventData,
} from "../api";
import { emptyBriefing } from "./briefingUtils";
import type { BriefingState, ThemeWizardProps } from "./types";

/**
 * 课程主题向导状态与操作：草稿加载、问卷保存、课表生成与确认落库。
 *
 * @param props 向导入参
 */
export function useThemeWizard({
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

  return {
    step,
    setStep,
    stepTitle,
    theme,
    briefing,
    setBriefing,
    draft,
    setDraft,
    busy,
    coverInput,
    setCoverInput,
    outScopeInput,
    setOutScopeInput,
    statusText,
    onCancel,
    saveCurrentStep,
    runGenerate,
    handleCommit,
    updateSeries,
    updateLesson,
  };
}

/** 课程向导 hook 返回值类型。 */
export type ThemeWizardModel = ReturnType<typeof useThemeWizard>;
