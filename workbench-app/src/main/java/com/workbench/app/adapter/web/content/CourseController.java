package com.workbench.app.adapter.web.content;

import com.workbench.common.ApiResult;
import com.workbench.common.FlexiblePayload;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.dto.view.ContentModeView;
import com.workbench.content.facade.dto.view.CourseContentView;
import com.workbench.content.facade.dto.request.CourseGenerationSettingsRequest;
import com.workbench.content.facade.dto.view.CourseGenerationSettingsView;
import com.workbench.content.facade.dto.request.CourseLifecycleRequest;
import com.workbench.content.facade.dto.view.CourseLifecycleView;
import com.workbench.content.facade.dto.view.CourseMediaView;
import com.workbench.content.facade.dto.view.CourseView;
import com.workbench.content.facade.dto.view.GenerationJobCreateView;
import com.workbench.content.facade.dto.view.WorkbenchDataView;
import com.workbench.job.facade.JobFacade;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.job.facade.dto.view.JobView;
import com.workbench.skill.facade.WorkflowFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课程与教学模式相关 HTTP 接口：模式列表、工作台聚合、课程详情/正文、生成任务与偏好。
 */
@Tag(name = "课程", description = "教学模式、课程详情与内容生成")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CourseController {
  /** 内容领域门面：课程/偏好/生成任务创建。 */
  private final ContentFacade content;
  /** 任务门面：按 jobId 回读任务视图。 */
  private final JobFacade jobs;
  /** 工作流门面：解析已发布工作流并挂接正式生成 run。 */
  private final WorkflowFacade workflows;
  /** 正式课程生成入参补全（偏好 + 工作流）。 */
  private final CourseGenerationInputEnricher courseGenerationInputEnricher;

  /**
   * 列出可用教学模式（课程体系 / 热点资讯）。
   *
   * @return 包装后的模式列表
   */
  @GetMapping("/modes")
  @Operation(summary = "列出教学模式")
  public ApiResult<List<ContentModeView>> modes() {
    // 委托内容门面返回静态模式定义
    return ApiResult.ok(content.listModes());
  }

  /**
   * 工作台首页课程聚合数据（默认主题）。
   *
   * @return 含 workspace、courses、metrics 的工作台视图
   */
  @GetMapping("/courses")
  @Operation(summary = "工作台首页课程聚合数据")
  public ApiResult<WorkbenchDataView> courses() {
    // themeId=null 时走默认课程体系主题
    return ApiResult.ok(content.workbenchData(null));
  }

  /**
   * 按主题返回工作台聚合数据。
   *
   * @param themeId 主题 id 或 code
   * @return 指定主题下的工作台视图（热点主题可无课程列表）
   */
  @GetMapping("/themes/{themeId}/courses")
  @Operation(summary = "按主题返回工作台数据")
  public ApiResult<WorkbenchDataView> themeCourses(@PathVariable String themeId) {
    return ApiResult.ok(content.workbenchData(themeId));
  }

  /**
   * 获取单课概要详情。
   *
   * @param courseId 课程 id
   * @return 课程视图（含系列、字数、媒资摘要等）
   */
  @GetMapping("/courses/{courseId}")
  @Operation(summary = "获取课程详情")
  public ApiResult<CourseView> course(@PathVariable String courseId) {
    return ApiResult.ok(content.getCourse(courseId));
  }

  /**
   * 获取课程正文与媒体（原稿 + 公众号稿，URL 已重写）。
   *
   * @param courseId 课程 id
   * @return 正文与媒资列表
   */
  @GetMapping({"/courses/{courseId}/content", "/themes/{themeId}/courses/{courseId}/content"})
  @Operation(summary = "获取课程正文与媒体")
  public ApiResult<CourseContentView> courseContent(@PathVariable String courseId) {
    // themeId 路径变量仅作路由兼容，正文按 courseId 加载
    return ApiResult.ok(content.getCourseContent(courseId));
  }

  /**
   * 列出课程媒资（配图等）。
   *
   * @param courseId 课程 id
   * @return 媒资视图列表
   */
  @GetMapping("/courses/{courseId}/media")
  @Operation(summary = "列出课程媒体")
  public ApiResult<List<CourseMediaView>> courseMedia(@PathVariable String courseId) {
    return ApiResult.ok(content.listCourseMedia(courseId));
  }

  /**
   * 启动课程相关生成任务；正式课程内容生成会补全偏好并挂接工作流。
   *
   * @param courseId 课程 id（作为任务 entityId）
   * @param body     可选入参；缺省 type 为课程内容生成
   * @return 新建任务的 jobId 与 type
   */
  @PostMapping("/courses/{courseId}/generate")
  @Operation(summary = "启动课程生成任务")
  public ApiResult<GenerationJobCreateView> generateCourse(
      @PathVariable String courseId, @Valid @RequestBody(required = false) FlexiblePayload body) {
    FlexiblePayload payload = FlexiblePayload.ofNullable(body);
    String type = String.valueOf(payload.asMap().getOrDefault("type", JobTypes.COURSE_CONTENT_GENERATION));
    if (JobTypes.COURSE_CONTENT_GENERATION.equals(type)) {
      payload = courseGenerationInputEnricher.enrichFormalCourseGenerationInput(courseId, payload);
    }
    String jobId = content.createGenerationJob(type, courseId, payload);
    if (JobTypes.COURSE_CONTENT_GENERATION.equals(type)) {
      Object workflowId = payload.asMap().get("workflowId");
      if (workflowId != null && !String.valueOf(workflowId).isBlank()) {
        workflows.attachJobRun(String.valueOf(workflowId), jobId);
      }
    }
    return ApiResult.ok(GenerationJobCreateView.builder().jobId(jobId).type(type).build());
  }

  /**
   * 启动正式课程内容生成，并立即返回任务详情。
   *
   * @param courseId 课程 id
   * @param body     可选生成入参（字数/配图/工作流等）
   * @return 新建任务的完整 {@link JobView}
   */
  @PostMapping("/courses/{courseId}/generate-content")
  @Operation(summary = "启动课程内容生成")
  public ApiResult<JobView> generateCourseContent(
      @PathVariable String courseId, @Valid @RequestBody(required = false) FlexiblePayload body) {
    FlexiblePayload payload =
        courseGenerationInputEnricher.enrichFormalCourseGenerationInput(courseId, body);
    String jobId = content.createGenerationJob(JobTypes.COURSE_CONTENT_GENERATION, courseId, payload);
    workflows.attachJobRun(String.valueOf(payload.asMap().get("workflowId")), jobId);
    return ApiResult.ok(jobs.get(jobId));
  }

  /**
   * 读取课程生成偏好（字数/配图档位）。
   *
   * @param courseId 课程 id
   * @return 偏好视图；无记录时返回默认档位
   */
  @GetMapping("/courses/{courseId}/generation-settings")
  @Operation(summary = "读取课程生成偏好")
  public ApiResult<CourseGenerationSettingsView> getGenerationSettings(@PathVariable String courseId) {
    return ApiResult.ok(content.getGenerationSettings(courseId));
  }

  /**
   * 保存课程生成偏好。
   *
   * @param courseId 课程 id
   * @param body     偏好请求体；可为 null 表示仅确保默认行存在
   * @return 保存后的偏好视图
   */
  @PutMapping("/courses/{courseId}/generation-settings")
  @Operation(summary = "保存课程生成偏好")
  public ApiResult<CourseGenerationSettingsView> putGenerationSettings(
      @PathVariable String courseId,
      @Valid @RequestBody(required = false) CourseGenerationSettingsRequest body) {
    return ApiResult.ok(content.putGenerationSettings(courseId, body));
  }

  /**
   * 更新课程发布生命周期（published / draft）。
   *
   * @param courseId 课程 id
   * @param body     含 {@code published} 布尔；null 视为未发布
   * @return 更新后的生命周期视图
   */
  @PatchMapping("/courses/{courseId}/lifecycle")
  @Operation(summary = "更新课程发布生命周期")
  public ApiResult<CourseLifecycleView> patchCourseLifecycle(
      @PathVariable String courseId,
      @Valid @RequestBody(required = false) CourseLifecycleRequest body) {
    // 仅当显式 true 才视为已发布
    boolean published = body != null && Boolean.TRUE.equals(body.getPublished());
    return ApiResult.ok(content.patchLifecycle(courseId, published));
  }

  /**
   * 列出课程内容生成任务，可按 course_id 过滤。
   *
   * @param courseId 可选课程 id；空则返回该类型近期任务
   * @return 任务视图列表
   */
  @GetMapping("/course-generation-jobs")
  @Operation(summary = "列出课程生成任务")
  public ApiResult<List<JobView>> courseGenerationJobs(
      @RequestParam(value = "course_id", required = false) String courseId) {
    return ApiResult.ok(content.listCourseGenerationJobs(courseId));
  }
}
