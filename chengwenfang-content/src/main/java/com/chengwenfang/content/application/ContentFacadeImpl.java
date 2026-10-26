package com.chengwenfang.content.application;

import com.chengwenfang.common.FlexiblePayload;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.request.ArticleLayoutSaveRequest;
import com.chengwenfang.content.facade.dto.request.ArticleUpdateRequest;
import com.chengwenfang.content.facade.dto.request.CourseGenerationSettingsRequest;
import com.chengwenfang.content.facade.dto.request.CurriculumDraftSaveRequest;
import com.chengwenfang.content.facade.dto.request.HumanReviewRequest;
import com.chengwenfang.content.facade.dto.request.PersistGeneratedImageRequest;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.request.ThemeDraftCreateRequest;
import com.chengwenfang.content.facade.dto.request.ThemePatchRequest;
import com.chengwenfang.content.facade.dto.view.ArticleLayoutView;
import com.chengwenfang.content.facade.dto.view.ArticleVersionView;
import com.chengwenfang.content.facade.dto.view.ArticleView;
import com.chengwenfang.content.facade.dto.view.ContentModeView;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.CourseContentView;
import com.chengwenfang.content.facade.dto.view.CourseGenerationCommitView;
import com.chengwenfang.content.facade.dto.view.CourseGenerationSettingsView;
import com.chengwenfang.content.facade.dto.view.CourseLifecycleView;
import com.chengwenfang.content.facade.dto.view.CourseMediaView;
import com.chengwenfang.content.facade.dto.view.CourseSeriesView;
import com.chengwenfang.content.facade.dto.view.CourseShellView;
import com.chengwenfang.content.facade.dto.view.CourseView;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import com.chengwenfang.content.facade.dto.view.HumanReviewImportEntry;
import com.chengwenfang.content.facade.dto.view.HumanReviewView;
import com.chengwenfang.content.facade.dto.view.ImportHumanReviewsView;
import com.chengwenfang.content.facade.dto.view.RepairResponseView;
import com.chengwenfang.content.facade.dto.view.SeriesNeighborsView;
import com.chengwenfang.content.facade.dto.view.SyncLearningContentView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumCommitView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumGenerateView;
import com.chengwenfang.content.facade.dto.view.ThemeRouteView;
import com.chengwenfang.content.facade.dto.view.ChengwenfangDataView;
import com.chengwenfang.job.facade.dto.view.JobView;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ContentFacade} 默认实现：薄门面，按职责转发到领域协作服务。
 *
 * <ul>
 *   <li>{@link CourseShellService} — 课程壳、成文坊首页、媒资与系列邻课</li>
 *   <li>{@link LearningContentSyncService} — 学习内容目录同步入库</li>
 *   <li>{@link ArticleVersionService} — 文章读写、版本、生成提交与修复</li>
 *   <li>{@link GenerationSettingsAndJobService} — 生成偏好与异步任务入队</li>
 *   <li>{@link GeneratedImageService} — AI 配图持久化与读写</li>
 *   <li>{@link HumanReviewService} — 人工审核</li>
 *   <li>{@link ArticleLayoutService} — 公众号排版配置</li>
 *   <li>{@link ThemeService} — 主题向导与 CRUD</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ContentFacadeImpl implements ContentFacade {

  /** 课程壳、成文坊数据与媒资。 */
  private final CourseShellService shell;
  /** 学习内容目录 → 库表同步。 */
  private final LearningContentSyncService sync;
  /** 文章版本、生成提交与修复流水线。 */
  private final ArticleVersionService articles;
  /** 生成偏好读写与生成/配图 Job 入队。 */
  private final GenerationSettingsAndJobService generation;
  /** 生成配图登记与文件访问。 */
  private final GeneratedImageService images;
  /** 人工审核读写与历史导入。 */
  private final HumanReviewService reviews;
  /** 文章排版主题与选项。 */
  private final ArticleLayoutService layouts;
  /** 主题向导与 CRUD。 */
  private final ThemeService themes;
  /** 产出正文与身份解析。 */
  private final WorkContentStoreImpl workContents;

  /** {@inheritDoc} */
  @Override
  public List<CourseView> listCourses() {
    return shell.listCourses();
  }

  /** {@inheritDoc} */
  @Override
  public ChengwenfangDataView chengwenfangData(String themeId) {
    return shell.chengwenfangData(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public List<ContentModeView> listModes() {
    return themes.listModes();
  }

  /** {@inheritDoc} */
  @Override
  public List<ContentThemeView> listThemes(String modeId) {
    return themes.listThemes(modeId, false);
  }

  /** {@inheritDoc} */
  @Override
  public List<ContentThemeView> listThemes(String modeId, boolean includeDeleted) {
    return themes.listThemes(modeId, includeDeleted);
  }

  /** {@inheritDoc} */
  @Override
  public ContentThemeView getTheme(String themeId) {
    return themes.getTheme(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public ContentThemeView createThemeDraft(String modeId, ThemeDraftCreateRequest request) {
    return themes.createDraft(modeId, request);
  }

  /** {@inheritDoc} */
  @Override
  public ContentThemeView ensureSeedTheme(
      String themeId,
      String modeId,
      String code,
      String name,
      String description,
      int sortOrder) {
    return themes.ensureSeedTheme(themeId, modeId, code, name, description, sortOrder);
  }

  /** {@inheritDoc} */
  @Override
  public ContentThemeView patchThemeBriefing(String themeId, ThemeBriefingPatchRequest request) {
    return themes.patchBriefing(themeId, request);
  }

  /** {@inheritDoc} */
  @Override
  public ThemeCurriculumGenerateView enqueueThemeCurriculumGenerate(String themeId) {
    return themes.enqueueCurriculumGenerate(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public CurriculumDraftView getCurriculumDraft(String themeId) {
    return themes.getCurriculumDraft(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public CurriculumDraftView saveCurriculumDraft(String themeId, CurriculumDraftSaveRequest request) {
    return themes.saveCurriculumDraft(themeId, request);
  }

  /** {@inheritDoc} */
  @Override
  public void applyGeneratedCurriculum(String themeId, CurriculumDraftView draft) {
    themes.applyGeneratedCurriculum(themeId, draft);
  }

  /** {@inheritDoc} */
  @Override
  public ThemeCurriculumCommitView commitCurriculum(String themeId) {
    return themes.commitCurriculum(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public HotNewsThemeCommitView commitHotNewsTheme(String themeId) {
    return themes.commitHotNewsTheme(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public void assertThemeActive(String themeId) {
    themes.requireActiveTheme(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public void assertThemeEnabled(String themeId) {
    themes.requireEnabledTheme(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public ContentThemeView patchTheme(String themeId, ThemePatchRequest request) {
    return themes.patchTheme(themeId, request);
  }

  /** {@inheritDoc} */
  @Override
  public void deleteTheme(String themeId) {
    themes.deleteTheme(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public ContentThemeView restoreTheme(String themeId) {
    return themes.restoreTheme(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public ThemeRouteView getThemeRoute(String themeId) {
    return themes.getRoute(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public List<CourseSeriesView> listThemeSeries(String themeId) {
    return themes.listSeries(themeId);
  }

  /** {@inheritDoc} */
  @Override
  public CourseView getCourse(String courseId) {
    return shell.getCourse(courseId);
  }

  /** {@inheritDoc} */
  @Override
  public CourseContentView getCourseContent(String courseId) {
    return shell.getCourseContent(courseId);
  }

  /** {@inheritDoc} */
  @Override
  public List<CourseMediaView> listCourseMedia(String courseId) {
    return shell.listCourseMedia(courseId);
  }

  /** {@inheritDoc} */
  @Override
  public CourseMediaView getMediaAsset(String mediaId) {
    return shell.getMediaAsset(mediaId);
  }

  /** {@inheritDoc} */
  @Override
  public Path getMediaFile(String mediaId) {
    return shell.getMediaFile(mediaId);
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getMediaBytes(String mediaId) {
    return shell.getMediaBytes(mediaId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public SyncLearningContentView syncLearningContent() {
    return sync.syncLearningContent();
  }

  /** {@inheritDoc} */
  @Override
  public CourseGenerationSettingsView getGenerationSettings(String courseId) {
    return generation.getGenerationSettings(courseId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public CourseGenerationSettingsView putGenerationSettings(
      String courseId, CourseGenerationSettingsRequest body) {
    return generation.putGenerationSettings(courseId, body);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public CourseLifecycleView patchLifecycle(String courseId, boolean published) {
    return shell.patchLifecycle(courseId, published);
  }

  /** {@inheritDoc} */
  @Override
  public List<JobView> listCourseGenerationJobs(String courseId) {
    return generation.listCourseGenerationJobs(courseId);
  }

  /** {@inheritDoc} */
  @Override
  public List<ArticleVersionView> listArticleVersions(String articleId) {
    return articles.listArticleVersions(articleId);
  }

  /** {@inheritDoc} */
  @Override
  public List<ArticleVersionView> listArticleVersions(String articleId, String documentId) {
    return articles.listArticleVersions(articleId, documentId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleView restoreArticleVersion(String articleId, String versionId) {
    return articles.restoreArticleVersion(articleId, versionId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleView restoreArticleVersion(String articleId, String versionId, String documentId) {
    return articles.restoreArticleVersion(articleId, versionId, documentId);
  }

  /** {@inheritDoc} */
  @Override
  public ArticleView getArticle(String articleId) {
    return articles.getArticle(articleId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleView updateArticle(String articleId, ArticleUpdateRequest body) {
    return articles.updateArticle(articleId, body);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public CourseShellView ensureCourseShell(String courseId) {
    return shell.ensureCourseShell(courseId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public CourseGenerationCommitView commitCourseGeneration(
      String courseId, String draftMarkdown, String wechatMarkdown) {
    return articles.commitCourseGeneration(courseId, draftMarkdown, wechatMarkdown);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleView generateArticleKind(String articleId, String kind, String markdown) {
    return articles.generateArticleKind(articleId, kind, markdown);
  }

  /** {@inheritDoc} */
  @Override
  public String createGenerationJob(String type, String entityId, FlexiblePayload input) {
    return generation.createGenerationJob(type, entityId, input);
  }

  /** {@inheritDoc} */
  @Override
  public String enqueueImageJob(String articleId, FlexiblePayload input) {
    return generation.enqueueImageJob(articleId, input);
  }

  /** {@inheritDoc} */
  @Override
  public List<GeneratedImageView> listImages(String articleId) {
    return images.listImages(articleId);
  }

  /** {@inheritDoc} */
  @Override
  public List<GeneratedImageView> listImages(String articleId, String documentId) {
    return images.listImages(articleId, documentId);
  }

  /** {@inheritDoc} */
  @Override
  public GeneratedImageView getImage(String imageId) {
    return images.getImage(imageId);
  }

  /** {@inheritDoc} */
  @Override
  public Path getImageFile(String imageId) {
    return images.getImageFile(imageId);
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getImageBytes(String imageId) {
    return images.getImageBytes(imageId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void deleteImage(String imageId) {
    images.deleteImage(imageId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public GeneratedImageView persistGeneratedImage(PersistGeneratedImageRequest fields) {
    return images.persistGeneratedImage(fields);
  }

  /** {@inheritDoc} */
  @Override
  public SeriesNeighborsView findSeriesNeighbors(String courseId) {
    return shell.findSeriesNeighbors(courseId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void syncPreviousLessonPreview(String courseId) {
    shell.syncPreviousLessonPreview(courseId);
  }

  /** {@inheritDoc} */
  @Override
  public String startRepair(String articleId) {
    return articles.startRepair(articleId);
  }

  /** {@inheritDoc} */
  @Override
  public String startRepair(String articleId, String documentId) {
    return articles.startRepair(articleId, documentId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleView repairMetadata(String articleId) {
    return articles.repairMetadata(articleId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public RepairResponseView applyArticleRepair(
      String articleId,
      String jobId,
      String repairedContent,
      Map<String, Object> beforeAudit,
      Map<String, Object> afterAudit,
      List<String> resolvedCodes,
      List<String> addedCodes) {
    return articles.applyArticleRepair(
        articleId, jobId, repairedContent, beforeAudit, afterAudit, resolvedCodes, addedCodes);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public RepairResponseView applyArticleRepair(
      String articleId,
      String jobId,
      String repairedContent,
      Map<String, Object> beforeAudit,
      Map<String, Object> afterAudit,
      List<String> resolvedCodes,
      List<String> addedCodes,
      String documentId) {
    return articles.applyArticleRepair(
        articleId,
        jobId,
        repairedContent,
        beforeAudit,
        afterAudit,
        resolvedCodes,
        addedCodes,
        documentId);
  }

  /** {@inheritDoc} */
  @Override
  public HumanReviewView getHumanReview(String articleId) {
    return reviews.getHumanReview(articleId);
  }

  /** {@inheritDoc} */
  @Override
  public HumanReviewView getHumanReview(String articleId, String documentId) {
    return reviews.getHumanReview(articleId, documentId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public HumanReviewView putHumanReview(String articleId, HumanReviewRequest body) {
    return reviews.putHumanReview(articleId, body);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void deleteHumanReview(String articleId) {
    reviews.deleteHumanReview(articleId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public void deleteHumanReview(String articleId, String documentId) {
    reviews.deleteHumanReview(articleId, documentId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleLayoutView getOrInitLayout(String articleId) {
    return layouts.getOrInitLayout(articleId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleLayoutView getOrInitLayout(String articleId, String documentId) {
    return layouts.getOrInitLayout(articleId, documentId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleLayoutView saveLayout(String articleId, ArticleLayoutSaveRequest body) {
    return layouts.saveLayout(articleId, body);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ArticleLayoutView saveLayout(
      String articleId, ArticleLayoutSaveRequest body, String documentId) {
    return layouts.saveLayout(articleId, body, documentId);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ImportHumanReviewsView importHumanReviews(List<HumanReviewImportEntry> entries) {
    return reviews.importHumanReviews(entries);
  }

  /** {@inheritDoc} */
  @Override
  public String applyFixtureRepairs(String content) {
    return articles.applyFixtureRepairs(content);
  }

  /** {@inheritDoc} */
  @Override
  public com.chengwenfang.content.facade.dto.WorkOutputRef findOutputRef(String outputId) {
    return workContents.findOutputRef(outputId);
  }

  /** {@inheritDoc} */
  @Override
  public String resolvePublishingOutputId(String workOrOutputId) {
    return workContents.resolvePublishingOutputId(workOrOutputId);
  }
}
