// @ts-nocheck

export type CourseRouteRailProps = {
  course: any;
  groupIndex: number;
  publishedCourses: any;
};

export function CourseRouteRail({
  course,
  groupIndex,
  publishedCourses,
}: CourseRouteRailProps) {
  return (
    <div className="course-main">
      <div className="course-title-line">
        <strong>
          {course.title?.trim()
            ? `第 ${course.lessonNo ?? 0} 课：${course.title.trim()}`
            : `第 ${groupIndex + 1} 系列第 ${course.lessonNo ?? 0} 课`}
        </strong>
        <div className="stage-dots">
          <span
            className={
              course.draftWords
                ? "done"
                : ""
            }
          >
            正文
          </span>
          <span
            className={
              course.articleWords
                ? "done"
                : ""
            }
          >
            公众号
          </span>
          <span
            className={
              course.status ===
              "人工已审核"
                ? "done"
                : ""
            }
          >
            审核
          </span>
          <span
            className={
              publishedCourses[course.id]
                ? "done published"
                : ""
            }
          >
            发布
          </span>
        </div>
      </div>
      <small>
        {course.summary ||
          (course.draftWords
            ? "课程正文已生成"
            : "等待生成课程正文")}
      </small>
    </div>
  );
}
