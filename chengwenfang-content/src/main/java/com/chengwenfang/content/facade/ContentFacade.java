package com.chengwenfang.content.facade;


import com.chengwenfang.common.FlexiblePayload;
import com.chengwenfang.content.facade.dto.WorkOutputRef;
import com.chengwenfang.content.facade.dto.request.ArticleLayoutSaveRequest;
import com.chengwenfang.content.facade.dto.request.ArticleUpdateRequest;
import com.chengwenfang.content.facade.dto.request.CourseGenerationSettingsRequest;
import com.chengwenfang.content.facade.dto.request.HumanReviewRequest;
import com.chengwenfang.content.facade.dto.request.PersistGeneratedImageRequest;
import com.chengwenfang.content.facade.dto.view.ArticleLayoutView;
import com.chengwenfang.content.facade.dto.view.ArticleVersionView;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.dto.view.ContentModeView;
import com.chengwenfang.content.facade.dto.view.CourseContentView;
import com.chengwenfang.content.facade.dto.view.CourseGenerationCommitView;
import com.chengwenfang.content.facade.dto.view.CourseGenerationSettingsView;
import com.chengwenfang.content.facade.dto.view.CourseLifecycleView;
import com.chengwenfang.content.facade.dto.view.CourseMediaView;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import com.chengwenfang.content.facade.dto.view.CourseView;
import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import com.chengwenfang.content.facade.dto.view.HumanReviewImportEntry;
import com.chengwenfang.content.facade.dto.view.HumanReviewView;
import com.chengwenfang.content.facade.dto.view.ImportHumanReviewsView;
import com.chengwenfang.content.facade.dto.view.RepairResponseView;
import com.chengwenfang.content.facade.dto.view.SeriesNeighborsView;
import com.chengwenfang.content.facade.dto.view.SyncLearningContentView;
import com.chengwenfang.content.facade.dto.view.ChengwenfangDataView;
import com.chengwenfang.content.facade.dto.request.CurriculumDraftSaveRequest;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.request.ThemeDraftCreateRequest;
import com.chengwenfang.content.facade.dto.request.ThemePatchRequest;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.CourseSeriesView;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumCommitView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumGenerateView;
import com.chengwenfang.content.facade.dto.view.ThemeRouteView;
import com.chengwenfang.job.facade.dto.view.JobView;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 课程内容门面：课程壳、文章版本、配图/媒资、生成偏好、人工审核与排版、主题向导。
 */
public interface ContentFacade {
  /** 列出全部课程（按系列排序）。 */
  List<CourseView> listCourses();

  /** 组装工作台首页数据（主题、课程列表与进度指标）。 */
  ChengwenfangDataView chengwenfangData(String themeId);

  /** 列出可用教学模式（课程体系 / 热点资讯）。 */
  List<ContentModeView> listModes();

  /** 列出模式下属主题。 */
  List<ContentThemeView> listThemes(String modeId);

  /** 列出模式下属主题；includeDeleted 为 true 时仅返回已软删主题。 */
  List<ContentThemeView> listThemes(String modeId, boolean includeDeleted);

  /** 主题详情。 */
  ContentThemeView getTheme(String themeId);

  /** 创建主题草稿。 */
  ContentThemeView createThemeDraft(String modeId, ThemeDraftCreateRequest request);

  /**
   * 确保内置种子主题存在（固定 id/code，created_via=seed）；已存在则直接返回。
   *
   * @param themeId     固定主题 id
   * @param modeId      模式 id/code
   * @param code        主题 code
   * @param name        名称
   * @param description 描述
   * @param sortOrder   排序
   * @return 主题视图
   */
  ContentThemeView ensureSeedTheme(
      String themeId,
      String modeId,
      String code,
      String name,
      String description,
      int sortOrder);

  /** 合并问卷 briefing。 */
  ContentThemeView patchThemeBriefing(String themeId, ThemeBriefingPatchRequest request);

  /** 入队课表生成任务。 */
  ThemeCurriculumGenerateView enqueueThemeCurriculumGenerate(String themeId);

  /** 读取课表草案。 */
  CurriculumDraftView getCurriculumDraft(String themeId);

  /** 保存课表草案。 */
  CurriculumDraftView saveCurriculumDraft(String themeId, CurriculumDraftSaveRequest request);

  /** Job 写入 AI 课表草案。 */
  void applyGeneratedCurriculum(String themeId, CurriculumDraftView draft);

  /** 确认课表落库。 */
  ThemeCurriculumCommitView commitCurriculum(String themeId);

  /**
   * 确认热点主题问卷并启用（不创建渠道）。
   *
   * @param themeId 主题 id/code
   * @return 启用结果
   */
  HotNewsThemeCommitView commitHotNewsTheme(String themeId);

  /**
   * 断言主题存在且未软删（供跨域接口守卫）。
   *
   * @param themeId 主题 id/code
   */
  void assertThemeActive(String themeId);

  /**
   * 断言主题存在、未软删且已启用（采集/写操作守卫）。
   *
   * @param themeId 主题 id/code
   */
  void assertThemeEnabled(String themeId);

  /** 补丁主题元数据/状态。 */
  ContentThemeView patchTheme(String themeId, ThemePatchRequest request);

  /** 假删主题（写 deleted_at，不物理删下游）。 */
  void deleteTheme(String themeId);

  /** 恢复已假删主题（清空 deleted_at，状态为 disabled）。 */
  ContentThemeView restoreTheme(String themeId);

  /** 主题课程路线。 */
  ThemeRouteView getThemeRoute(String themeId);

  /** 主题下系列列表。 */
  List<CourseSeriesView> listThemeSeries(String themeId);

  /** 获取单课概要视图。 */
  CourseView getCourse(String courseId);

  /**
   * 获取课程正文与媒资：原稿、公众号稿，并将历史 public 路径重写为可访问 URL。
   */
  CourseContentView getCourseContent(String courseId);

  /** 列出课程媒资（配图等）。 */
  List<CourseMediaView> listCourseMedia(String courseId);

  /** 获取单个媒资元数据。 */
  CourseMediaView getMediaAsset(String mediaId);

  /** 解析媒资本地文件路径。 */
  Path getMediaFile(String mediaId);

  /** 读取媒资二进制内容。 */
  byte[] getMediaBytes(String mediaId);

  /**
   * 从 public/data/chengwenfang-data.json 同步学习内容到库：
   * 课程元数据、正文文件、媒资登记，并回写正文中的图片 URL。
   */
  SyncLearningContentView syncLearningContent();

  /** 读取课程生成偏好（字数/配图档位）。 */
  CourseGenerationSettingsView getGenerationSettings(String courseId);

  /** 保存课程生成偏好。 */
  CourseGenerationSettingsView putGenerationSettings(String courseId, CourseGenerationSettingsRequest body);

  /** 更新课程发布生命周期（published / draft）。 */
  CourseLifecycleView patchLifecycle(String courseId, boolean published);

  /** 列出该课程相关的内容生成任务。 */
  List<JobView> listCourseGenerationJobs(String courseId);

  /** 列出文章历史版本。 */
  List<ArticleVersionView> listArticleVersions(String articleId);

  /**
   * 列出文章历史版本。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   */
  List<ArticleVersionView> listArticleVersions(String articleId, String documentId);

  /** 将文章内容回滚到指定版本。 */
  ArticleView restoreArticleVersion(String articleId, String versionId);

  /**
   * 将文章内容回滚到指定版本。
   *
   * @param articleId  文章 id
   * @param versionId  版本 id
   * @param documentId 统一文档 id；可空
   */
  ArticleView restoreArticleVersion(String articleId, String versionId, String documentId);

  /** 获取文章详情。 */
  ArticleView getArticle(String articleId);

  /** 更新文章（含 versionToken 乐观锁）。 */
  ArticleView updateArticle(String articleId, ArticleUpdateRequest body);

  /** 确保课程壳存在：缺失时创建原稿与公众号文章占位。 */
  CourseShellView ensureCourseShell(String courseId);

  /** 提交课程生成结果，写入原稿与公众号正文。 */
  CourseGenerationCommitView commitCourseGeneration(String courseId, String draftMarkdown, String wechatMarkdown);

  /** 按文章类型写入生成结果；articleId 也可传 courseId 再按 kind 解析。 */
  ArticleView generateArticleKind(String articleId, String kind, String markdown);

  /** 创建异步生成任务，返回 jobId。 */
  String createGenerationJob(String type, String entityId, FlexiblePayload input);

  /** 入队配图生成任务。 */
  String enqueueImageJob(String articleId, FlexiblePayload input);

  /** 列出文章已生成配图（自动解析 documentId）。 */
  List<GeneratedImageView> listImages(String articleId);

  /**
   * 列出文章已生成配图。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；空则服务端解析
   */
  List<GeneratedImageView> listImages(String articleId, String documentId);

  /** 获取配图元数据。 */
  GeneratedImageView getImage(String imageId);

  /** 解析配图文件路径（优先 fileId）。 */
  Path getImageFile(String imageId);

  /** 读取配图字节。 */
  byte[] getImageBytes(String imageId);

  /** 删除配图记录及文件（尽力清理）。 */
  void deleteImage(String imageId);

  /**
   * 持久化生成配图：必要时将 filePath 登记为 fileId，再只写 file_id。
   */
  GeneratedImageView persistGeneratedImage(PersistGeneratedImageRequest fields);

  /** 查询同系列前后课与完整课程序列，供正式生成上下文使用。 */
  SeriesNeighborsView findSeriesNeighbors(String courseId);

  /** 将上一课「下节课预告」同步为当前课标题。 */
  void syncPreviousLessonPreview(String courseId);

  /** 启动文章修复任务。 */
  String startRepair(String articleId);

  /**
   * 启动文章修复任务。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   */
  String startRepair(String articleId, String documentId);

  /** 补齐配图元数据（绑定 articleId 等），必要时清理夹具符号并落库。 */
  ArticleView repairMetadata(String articleId);

  /** 应用 AI/修复流水线结果：前后版本快照 + repair_runs 记录。 */
  RepairResponseView applyArticleRepair(
      String articleId,
      String jobId,
      String repairedContent,
      Map<String, Object> beforeAudit,
      Map<String, Object> afterAudit,
      List<String> resolvedCodes,
      List<String> addedCodes);

  /**
   * 应用 AI/修复流水线结果：前后版本快照 + repair_runs 记录。
   *
   * @param documentId 统一文档 id；可空
   */
  RepairResponseView applyArticleRepair(
      String articleId,
      String jobId,
      String repairedContent,
      Map<String, Object> beforeAudit,
      Map<String, Object> afterAudit,
      List<String> resolvedCodes,
      List<String> addedCodes,
      String documentId);

  /** 读取文章最新人工审核。 */
  HumanReviewView getHumanReview(String articleId);

  /**
   * 读取文章最新人工审核。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   */
  HumanReviewView getHumanReview(String articleId, String documentId);

  /** 写入或更新人工审核（approved / rejected）。 */
  HumanReviewView putHumanReview(String articleId, HumanReviewRequest body);

  /** 删除文章全部人工审核。 */
  void deleteHumanReview(String articleId);

  /**
   * 删除文章人工审核。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   */
  void deleteHumanReview(String articleId, String documentId);

  /** 获取排版配置，不存在则初始化空配置。 */
  ArticleLayoutView getOrInitLayout(String articleId);

  /**
   * 获取排版配置，不存在则初始化空配置。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   */
  ArticleLayoutView getOrInitLayout(String articleId, String documentId);

  /** 保存排版主题与选项。 */
  ArticleLayoutView saveLayout(String articleId, ArticleLayoutSaveRequest body);

  /**
   * 保存排版主题与选项。
   *
   * @param articleId  文章 id
   * @param body       保存请求
   * @param documentId 统一文档 id；可空
   */
  ArticleLayoutView saveLayout(String articleId, ArticleLayoutSaveRequest body, String documentId);

  /** 批量导入历史人工审核到公众号文章。 */
  ImportHumanReviewsView importHumanReviews(List<HumanReviewImportEntry> entries);

  /** 夹具修复：替换箭头与注释标记（仅本地 fixture / IT 路径使用）。 */
  String applyFixtureRepairs(String content);

  /**
   * 按产出 id 反查作品与模式；缺失返回 null。
   *
   * @param outputId 产出 id（原 documentId）
   * @return 身份投影；无则 null
   */
  WorkOutputRef findOutputRef(String outputId);

  /**
   * 解析下游公众号轨产出 id（作品 id 或产出 id 均可）。
   *
   * @param workOrOutputId 作品 id 或产出 id
   * @return 产出 id；无法解析时 null
   */
  String resolvePublishingOutputId(String workOrOutputId);
}
