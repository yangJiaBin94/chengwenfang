import type { ThemeWizardModel } from "../useThemeWizard";

type StepCurriculumProps = Pick<
  ThemeWizardModel,
  "draft" | "setDraft" | "updateSeries" | "updateLesson"
>;

/**
 * 向导第 7 步：确认并微调课表草案。
 *
 * @param props 课表相关状态与操作
 */
export function StepCurriculum({
  draft,
  setDraft,
  updateSeries,
  updateLesson,
}: StepCurriculumProps) {
  if (!draft) {
    return null;
  }
  return (
    <div className="wizard-panel curriculum-review">
      <label>
        路线说明
        <textarea
          rows={3}
          value={draft.routeNarrative || ""}
          onChange={(event) =>
            setDraft({ ...draft, routeNarrative: event.target.value })
          }
        />
      </label>
      {draft.series.map((series, seriesIndex) => (
        <div className="curriculum-series-block" key={series.tempId || seriesIndex}>
          <input
            className="series-name-input"
            value={series.name}
            onChange={(event) => updateSeries(seriesIndex, { name: event.target.value })}
          />
          <input
            value={series.positioning || ""}
            onChange={(event) =>
              updateSeries(seriesIndex, { positioning: event.target.value })
            }
            placeholder="系列定位"
          />
          {series.lessons.map((lesson, lessonIndex) => (
            <div className="curriculum-lesson-row" key={lessonIndex}>
              <span>第 {lesson.lessonNo} 课</span>
              <input
                value={lesson.title}
                onChange={(event) =>
                  updateLesson(seriesIndex, lessonIndex, { title: event.target.value })
                }
              />
              <input
                value={lesson.summary || ""}
                onChange={(event) =>
                  updateLesson(seriesIndex, lessonIndex, {
                    summary: event.target.value,
                  })
                }
                placeholder="摘要"
              />
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
