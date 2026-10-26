package com.chengwenfang.app.adapter.web.content;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.FlexiblePayload;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.CourseGenerationSettingsView;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import com.chengwenfang.skill.facade.WorkflowFacade;
import com.chengwenfang.skill.facade.enums.WorkflowFeatureBinding;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 正式课程内容生成入参补全：偏好档位、文档身份与已发布工作流。
 * <p>放在 adapter.web，避免 web → application.course 依赖（ArchUnit 禁止）。
 */
@Component
@RequiredArgsConstructor
public class CourseGenerationInputEnricher {
  private final ContentFacade content;
  private final WorkflowFacade workflows;

  /**
   * 为正式课程内容生成补全字数/配图偏好，并解析已发布工作流 id。
   *
   * @param courseId 课程 id，用于读取生成偏好
   * @param payload  原始入参；可为 null
   * @return 补全后的新载荷（不修改入参实例）
   */
  public FlexiblePayload enrichFormalCourseGenerationInput(String courseId, FlexiblePayload payload) {
    Map<String, Object> input = FlexiblePayload.ofNullable(payload).mutableMap();
    // 确保壳+文档 baseline，并把双轨 documentId 写入 Job（供 JobView 投影与深链）
    ensureCourseDocumentIds(courseId, input);
    CourseGenerationSettingsView prefs = content.getGenerationSettings(courseId);
    if (!input.containsKey("wordCountOption") || input.get("wordCountOption") == null
        || String.valueOf(input.get("wordCountOption")).isBlank()) {
      input.put("wordCountOption", prefs.getWordCountOption() == null ? "2000-4000" : prefs.getWordCountOption());
    }
    if (!input.containsKey("imageCountOption") || input.get("imageCountOption") == null
        || String.valueOf(input.get("imageCountOption")).isBlank()) {
      input.put("imageCountOption", prefs.getImageCountOption() == null ? "3-5" : prefs.getImageCountOption());
    }
    Object existingWorkflowId = input.get("workflowId");
    if (existingWorkflowId != null && !String.valueOf(existingWorkflowId).isBlank()) {
      // 已带 workflowId 时仍反查启用态，避免停用后继续入队
      workflows.requireActivePublished(WorkflowFeatureBinding.COURSE_CONTENT_GENERATION);
      ensureSkillSnapshot(input, String.valueOf(existingWorkflowId));
      return FlexiblePayload.of(input);
    }
    var published = workflows.requireActivePublished(WorkflowFeatureBinding.COURSE_CONTENT_GENERATION);
    input.put("workflowId", published.getId());
    if (published.getPublishedVersionId() != null) {
      input.put("workflowVersionId", published.getPublishedVersionId());
    }
    ensureSkillSnapshot(input, published.getId());
    return FlexiblePayload.of(input);
  }

  /**
   * 补全课程双轨文章/文档 id（缺省不覆盖调用方显式值）。
   *
   * @param courseId 课程 id
   * @param input    可变入参
   */
  private void ensureCourseDocumentIds(String courseId, Map<String, Object> input) {
    if (StrUtil.isBlank(courseId) || input == null) {
      return;
    }
    CourseShellView shell = content.ensureCourseShell(courseId);
    putIfAbsentBlank(input, "draftArticleId", shell.getDraftArticleId());
    putIfAbsentBlank(input, "wechatArticleId", shell.getWechatArticleId());
    // 课程轨 documentId 与 articles.id 相同
    putIfAbsentBlank(input, "draftDocumentId", shell.getDraftDocumentId());
    putIfAbsentBlank(input, "documentId", shell.getWechatDocumentId());
  }

  /**
   * 仅当键缺失或值为空白时写入。
   *
   * @param input 可变 map
   * @param key   键
   * @param value 值；空白则跳过
   */
  private static void putIfAbsentBlank(Map<String, Object> input, String key, String value) {
    if (StrUtil.isBlank(value)) {
      return;
    }
    Object existing = input.get(key);
    if (existing == null || StrUtil.isBlank(String.valueOf(existing))) {
      input.put(key, value);
    }
  }

  private void ensureSkillSnapshot(Map<String, Object> input, String workflowId) {
    if (input.containsKey("skillSnapshot") && input.get("skillSnapshot") instanceof List<?> list && !list.isEmpty()) {
      return;
    }
    List<Map<String, Object>> skillSnapshot = workflows.skillSnapshotForWorkflow(workflowId);
    if (!skillSnapshot.isEmpty()) {
      input.put("skillSnapshot", skillSnapshot);
    }
  }
}
