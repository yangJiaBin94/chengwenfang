package com.chengwenfang.mode.course.application.course;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.mode.course.application.generation.course.CourseGraphState;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 课程成文图：提交课程版本并同步预告。
 */
@Service
@RequiredArgsConstructor
public class CourseVersionCommitter {
  /** 内容门面。 */
  private final ContentFacade content;
  /** 上下文与进度。 */
  private final CourseGraphContextLoader contextLoader;

  /**
   * 提交课程版本并同步预告；回写 draft/wechat 供 Job 输出。
   *
   * @param state 图状态
   */
  public void commitVersion(CourseGraphState state) {
    contextLoader.markProgress(state, CourseWorkflowNodeType.COMMIT_VERSION);
    String courseId = state.getCourseId();
    var committed = content.commitCourseGeneration(courseId, state.getDraft(), state.getWechat());
    content.syncPreviousLessonPreview(courseId);
    // 状态保留 Markdown 正文；文章视图仅用于确认提交成功
    if (committed.getDraft() != null && StrUtil.isNotBlank(committed.getDraft().getContent())) {
      state.setDraft(committed.getDraft().getContent());
    }
    if (committed.getWechat() != null && StrUtil.isNotBlank(committed.getWechat().getContent())) {
      state.setWechat(committed.getWechat().getContent());
    }
    state.setCommitted(true);
  }
}
