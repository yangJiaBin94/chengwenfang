package com.chengwenfang.app.adapter.web.content;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.mode.hotnews.application.hotnews.HotNewsThemeCommitOrchestrator;
import com.chengwenfang.mode.hotnews.application.hotnews.HotNewsThemeGithubQueryAssistService;
import com.chengwenfang.mode.hotnews.application.hotnews.HotNewsThemeScoringRubricAssistService;
import com.chengwenfang.mode.hotnews.application.hotnews.HotNewsThemeSearchQueryAssistService;
import com.chengwenfang.mode.hotnews.application.hotnews.HotNewsThemeSourceChannelGenerateService;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.request.CurriculumDraftSaveRequest;
import com.chengwenfang.content.facade.dto.request.HotNewsGithubQuerySuggestRequest;
import com.chengwenfang.content.facade.dto.request.HotNewsScoringRubricSuggestRequest;
import com.chengwenfang.content.facade.dto.request.HotNewsSearchQuerySuggestRequest;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.request.ThemeDraftCreateRequest;
import com.chengwenfang.content.facade.dto.request.ThemePatchRequest;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.CourseSeriesView;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.dto.view.HotNewsGithubQuerySuggestView;
import com.chengwenfang.content.facade.dto.view.HotNewsScoringRubricSuggestView;
import com.chengwenfang.content.facade.dto.view.HotNewsSearchQuerySuggestView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumCommitView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumGenerateView;
import com.chengwenfang.content.facade.dto.view.ThemeRouteView;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsSourceChannelGenerateView;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.view.JobView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 内容主题 CRUD 与提问式创建向导 API。
 */
@Tag(name = "主题", description = "课程体系与热点资讯主题向导与管理")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ThemeController {
  private final ContentFacade content;
  private final JobFacade jobs;
  /** 热点主题确认启用编排。 */
  private final HotNewsThemeCommitOrchestrator hotNewsThemeCommit;
  /** 热点主题搜索词 AI 建议。 */
  private final HotNewsThemeSearchQueryAssistService hotNewsSearchQueryAssist;
  /** 热点主题评分量表 AI 生成。 */
  private final HotNewsThemeScoringRubricAssistService hotNewsScoringRubricAssist;
  /** 热点主题 GitHub 查询串 AI 生成。 */
  private final HotNewsThemeGithubQueryAssistService hotNewsGithubQueryAssist;
  /** 来源管理：AI 生成并校验落库渠道。 */
  private final HotNewsThemeSourceChannelGenerateService hotNewsSourceChannelGenerate;
  /** 热点域：删除主题前检查进行中任务。 */
  private final HotNewsFacade hotNews;

  /**
   * 列出模式下属主题。
   *
   * @param modeId         模式 id 或 code
   * @param includeDeleted true 时仅返回已软删主题
   * @return 主题列表
   */
  @GetMapping("/modes/{modeId}/themes")
  @Operation(summary = "列出模式下属主题")
  public ApiResult<List<ContentThemeView>> listThemes(
      @PathVariable String modeId,
      @RequestParam(defaultValue = "false") boolean includeDeleted) {
    return ApiResult.ok(content.listThemes(modeId, includeDeleted));
  }

  /**
   * 创建主题草稿。
   *
   * @param modeId  模式 id 或 code
   * @param request 可选初始字段
   * @return 新建主题
   */
  @PostMapping("/modes/{modeId}/themes/drafts")
  @Operation(summary = "创建主题草稿")
  public ApiResult<ContentThemeView> createDraft(
      @PathVariable String modeId,
      @RequestBody(required = false) ThemeDraftCreateRequest request) {
    return ApiResult.ok(content.createThemeDraft(modeId, request));
  }

  /**
   * 主题详情。
   *
   * @param themeId 主题 id 或 code
   * @return 主题视图
   */
  @GetMapping("/themes/{themeId}")
  @Operation(summary = "主题详情")
  public ApiResult<ContentThemeView> getTheme(@PathVariable String themeId) {
    return ApiResult.ok(content.getTheme(themeId));
  }

  /**
   * 合并保存问卷。
   *
   * @param themeId 主题 id
   * @param request 问卷补丁
   * @return 更新后主题
   */
  @PatchMapping("/themes/{themeId}/briefing")
  @Operation(summary = "保存主题问卷")
  public ApiResult<ContentThemeView> patchBriefing(
      @PathVariable String themeId,
      @Valid @RequestBody ThemeBriefingPatchRequest request) {
    return ApiResult.ok(content.patchThemeBriefing(themeId, request));
  }

  /**
   * 入队课表生成。
   *
   * @param themeId 主题 id
   * @return jobId
   */
  @PostMapping("/themes/{themeId}/curriculum:generate")
  @Operation(summary = "生成课表草案")
  public ApiResult<ThemeCurriculumGenerateView> generateCurriculum(@PathVariable String themeId) {
    ThemeCurriculumGenerateView view = content.enqueueThemeCurriculumGenerate(themeId);
    return ApiResult.ok(view);
  }

  /**
   * 查询课表生成任务状态。
   *
   * @param themeId 主题 id
   * @return 最近任务；无则 null data
   */
  @GetMapping("/themes/{themeId}/curriculum-job")
  @Operation(summary = "课表生成任务状态")
  public ApiResult<JobView> curriculumJob(@PathVariable String themeId) {
    ContentThemeView theme = content.getTheme(themeId);
    if (theme.getDeletedAt() != null) {
      throw new BusinessException(ErrorCodes.THEME_DELETED, "主题已删除，请先恢复", 404);
    }
    JobView latest = jobs.findLatestByThemeAndType(
        theme.getId(), JobTypes.THEME_CURRICULUM_GENERATION);
    return ApiResult.ok(latest);
  }

  /**
   * 读取课表草案。
   *
   * @param themeId 主题 id
   * @return 草案
   */
  @GetMapping("/themes/{themeId}/curriculum-draft")
  @Operation(summary = "读取课表草案")
  public ApiResult<CurriculumDraftView> getDraft(@PathVariable String themeId) {
    return ApiResult.ok(content.getCurriculumDraft(themeId));
  }

  /**
   * 人工编辑课表草案。
   *
   * @param themeId 主题 id
   * @param request 草案
   * @return 校验后草案
   */
  @PutMapping("/themes/{themeId}/curriculum-draft")
  @Operation(summary = "保存课表草案")
  public ApiResult<CurriculumDraftView> saveDraft(
      @PathVariable String themeId,
      @Valid @RequestBody CurriculumDraftSaveRequest request) {
    return ApiResult.ok(content.saveCurriculumDraft(themeId, request));
  }

  /**
   * 确认课表并落库课壳。
   *
   * @param themeId 主题 id
   * @return commit 结果
   */
  @PostMapping("/themes/{themeId}/curriculum:commit")
  @Operation(summary = "确认课表落库")
  public ApiResult<ThemeCurriculumCommitView> commit(@PathVariable String themeId) {
    return ApiResult.ok(content.commitCurriculum(themeId));
  }

  /**
   * 确认热点主题问卷并启用，同时创建初始搜索渠道。
   *
   * @param themeId 主题 id
   * @return 启用结果
   */
  @PostMapping("/themes/{themeId}/hot-news:commit")
  @Operation(summary = "确认热点主题并创建初始渠道")
  public ApiResult<HotNewsThemeCommitView> commitHotNews(@PathVariable String themeId) {
    return ApiResult.ok(hotNewsThemeCommit.commit(themeId));
  }

  /**
   * 根据主题名称/说明/关注实体，AI 总结可检索短搜索词（失败回退规则）。
   *
   * @param request 主题上下文
   * @return 规范化短词列表
   */
  @PostMapping("/themes/hot-news:suggest-search-queries")
  @Operation(summary = "AI 建议热点主题搜索词")
  public ApiResult<HotNewsSearchQuerySuggestView> suggestHotNewsSearchQueries(
      @RequestBody(required = false) HotNewsSearchQuerySuggestRequest request) {
    return ApiResult.ok(hotNewsSearchQueryAssist.suggest(request));
  }

  /**
   * 根据主题上下文生成写作价值评分量表（维度 + 权重）。
   *
   * @param request 主题上下文
   * @return 量表；AI 失败时回退启发式
   */
  @PostMapping("/themes/hot-news:suggest-scoring-rubric")
  @Operation(summary = "AI 生成热点主题评分量表")
  public ApiResult<HotNewsScoringRubricSuggestView> suggestHotNewsScoringRubric(
      @RequestBody(required = false) HotNewsScoringRubricSuggestRequest request) {
    return ApiResult.ok(hotNewsScoringRubricAssist.suggest(request));
  }

  /**
   * 根据主题名称/说明/搜索词，AI 生成 GitHub Search 查询串。
   *
   * @param request 主题上下文
   * @return 查询串；AI 失败时 fromAi=false 且 query 为空
   */
  @PostMapping("/themes/hot-news:suggest-github-query")
  @Operation(summary = "AI 建议热点主题 GitHub 查询串")
  public ApiResult<HotNewsGithubQuerySuggestView> suggestHotNewsGithubQuery(
      @RequestBody(required = false) HotNewsGithubQuerySuggestRequest request) {
    return ApiResult.ok(hotNewsGithubQueryAssist.suggest(request));
  }

  /**
   * 按主题 AI 生成来源渠道：文本模型推荐后做连通性校验，仅落库可用项。
   *
   * @param themeId 主题 id
   * @return 新建与跳过摘要
   */
  @PostMapping("/themes/{themeId}/hot-news/channels:generate-ai")
  @Operation(summary = "AI 生成并校验落库来源渠道")
  public ApiResult<HotNewsSourceChannelGenerateView> generateHotNewsSourceChannels(
      @PathVariable String themeId) {
    return ApiResult.ok(hotNewsSourceChannelGenerate.generate(themeId));
  }

  /**
   * 补丁主题元数据或停用。
   *
   * @param themeId 主题 id
   * @param request 补丁
   * @return 更新后主题
   */
  @PatchMapping("/themes/{themeId}")
  @Operation(summary = "更新主题")
  public ApiResult<ContentThemeView> patchTheme(
      @PathVariable String themeId,
      @RequestBody ThemePatchRequest request) {
    return ApiResult.ok(content.patchTheme(themeId, request));
  }

  /**
   * 删除主题。
   *
   * @param themeId 主题 id
   * @return 空
   */
  @DeleteMapping("/themes/{themeId}")
  @Operation(summary = "删除主题")
  public ApiResult<Void> deleteTheme(@PathVariable String themeId) {
    ContentThemeView theme = content.getTheme(themeId);
    // 热点主题额外拦截抓取/研究/成文进行中任务（Job input 未必带 themeId）
    if (ContentModeIds.isHotNews(theme.getModeId(), null)
        && hotNews.hasActiveJobsForTheme(theme.getId())) {
      throw new BusinessException(
          ErrorCodes.THEME_DELETE_FORBIDDEN, "主题仍有进行中的任务，请完成或取消后再删除", 400);
    }
    content.deleteTheme(themeId);
    return ApiResult.ok(null);
  }

  /**
   * 恢复已假删主题。
   *
   * @param themeId 主题 id
   * @return 恢复后主题（默认停用）
   */
  @PostMapping("/themes/{themeId}/restore")
  @Operation(summary = "恢复已删除主题")
  public ApiResult<ContentThemeView> restoreTheme(@PathVariable String themeId) {
    return ApiResult.ok(content.restoreTheme(themeId));
  }

  /**
   * 主题课程路线。
   *
   * @param themeId 主题 id
   * @return 路线
   */
  @GetMapping("/themes/{themeId}/route")
  @Operation(summary = "主题课程路线")
  public ApiResult<ThemeRouteView> route(@PathVariable String themeId) {
    return ApiResult.ok(content.getThemeRoute(themeId));
  }

  /**
   * 主题下系列。
   *
   * @param themeId 主题 id
   * @return 系列列表
   */
  @GetMapping("/themes/{themeId}/series")
  @Operation(summary = "主题下系列列表")
  public ApiResult<List<CourseSeriesView>> series(@PathVariable String themeId) {
    return ApiResult.ok(content.listThemeSeries(themeId));
  }
}
