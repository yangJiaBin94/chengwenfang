package com.chengwenfang.content.application;

import com.chengwenfang.content.domain.entity.ContentThemeEntity;
import com.chengwenfang.content.facade.dto.request.CurriculumDraftSaveRequest;
import com.chengwenfang.content.facade.dto.request.ThemeBriefingPatchRequest;
import com.chengwenfang.content.facade.dto.request.ThemeDraftCreateRequest;
import com.chengwenfang.content.facade.dto.request.ThemePatchRequest;
import com.chengwenfang.content.facade.dto.view.ContentModeView;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.CourseSeriesView;
import com.chengwenfang.content.facade.dto.view.CurriculumDraftView;
import com.chengwenfang.content.facade.dto.view.HotNewsThemeCommitView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumCommitView;
import com.chengwenfang.content.facade.dto.view.ThemeCurriculumGenerateView;
import com.chengwenfang.content.facade.dto.view.ThemeRouteView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内容主题读写、引导式问卷与课表草案 commit（门面，委派到生命周期/问卷/草案/提交协作类）。
 */
@Service
@RequiredArgsConstructor
public class ThemeService {
  /** 主题生命周期。 */
  private final ThemeLifecycleService lifecycle;
  /** 问卷读写。 */
  private final ThemeBriefingService briefing;
  /** 课表草案。 */
  private final CurriculumDraftService drafts;
  /** 课表确认提交。 */
  private final CurriculumCommitService commits;

  /**
   * 列出模式（DB）；课程体系主题来自 content_themes。
   *
   * @return 模式列表
   */
  public List<ContentModeView> listModes() {
    return lifecycle.listModes();
  }

  /**
   * 列出模式下属未删除主题（含 disabled，供管理折叠区）。
   *
   * @param modeId 模式 id 或 code
   * @return 主题列表
   */
  public List<ContentThemeView> listThemes(String modeId) {
    return lifecycle.listThemes(modeId);
  }

  /**
   * 列出模式下属主题。
   *
   * @param modeId         模式 id 或 code
   * @param includeDeleted true 时仅返回已软删主题；false 排除已删
   * @return 主题列表
   */
  public List<ContentThemeView> listThemes(String modeId, boolean includeDeleted) {
    return lifecycle.listThemes(modeId, includeDeleted);
  }

  /**
   * 主题详情。
   *
   * @param themeId 主题 id 或 code
   * @return 主题视图
   */
  public ContentThemeView getTheme(String themeId) {
    return lifecycle.getTheme(themeId);
  }

  /**
   * 创建向导草稿主题。
   *
   * @param modeId  模式 id/code（课程体系或热点资讯）
   * @param request 可选初始字段
   * @return 新建主题视图
   */
  @Transactional
  public ContentThemeView createDraft(String modeId, ThemeDraftCreateRequest request) {
    return lifecycle.createDraft(modeId, request);
  }

  /**
   * 合并保存问卷。
   *
   * @param themeId 主题 id/code
   * @param request 补丁
   * @return 更新后主题
   */
  @Transactional
  public ContentThemeView patchBriefing(String themeId, ThemeBriefingPatchRequest request) {
    return briefing.patchBriefing(themeId, request);
  }

  /**
   * 入队课表生成任务。
   *
   * @param themeId 主题 id/code
   * @return job 视图
   */
  @Transactional
  public ThemeCurriculumGenerateView enqueueCurriculumGenerate(String themeId) {
    return drafts.enqueueCurriculumGenerate(themeId);
  }

  /**
   * 读取课表草案。
   *
   * @param themeId 主题 id/code
   * @return 草案；无则空结构
   */
  public CurriculumDraftView getCurriculumDraft(String themeId) {
    return drafts.getCurriculumDraft(themeId);
  }

  /**
   * 人工保存课表草案。
   *
   * @param themeId 主题 id/code
   * @param request 草案
   * @return 校验后的草案
   */
  @Transactional
  public CurriculumDraftView saveCurriculumDraft(String themeId, CurriculumDraftSaveRequest request) {
    return drafts.saveCurriculumDraft(themeId, request);
  }

  /**
   * Job/编排写入 AI 生成的课表草案（覆盖未确认草案）。
   *
   * @param themeId 主题 id
   * @param draft   草案
   */
  @Transactional
  public void applyGeneratedCurriculum(String themeId, CurriculumDraftView draft) {
    drafts.applyGeneratedCurriculum(themeId, draft);
  }

  /**
   * 确保内置种子主题行存在（固定 id；已存在则跳过创建）。
   *
   * @param themeId     固定主题 id
   * @param modeId      模式 id/code
   * @param code        主题 code
   * @param name        名称
   * @param description 描述
   * @param sortOrder   排序
   * @return 主题视图
   */
  @Transactional
  public ContentThemeView ensureSeedTheme(
      String themeId,
      String modeId,
      String code,
      String name,
      String description,
      int sortOrder) {
    return lifecycle.ensureSeedTheme(themeId, modeId, code, name, description, sortOrder);
  }

  /**
   * 确认课表并落库系列与课壳。
   *
   * @param themeId 主题 id/code
   * @return commit 结果
   */
  @Transactional
  public ThemeCurriculumCommitView commitCurriculum(String themeId) {
    return commits.commitCurriculum(themeId);
  }

  /**
   * 确认热点主题问卷并启用（不创建渠道；渠道由应用层编排）。
   *
   * @param themeId 主题 id/code
   * @return 启用结果骨架（渠道数由编排层回填）
   */
  @Transactional
  public HotNewsThemeCommitView commitHotNewsTheme(String themeId) {
    return briefing.commitHotNewsTheme(themeId);
  }

  /**
   * 补丁主题元数据或停用/启用。
   *
   * @param themeId 主题 id/code
   * @param request 补丁
   * @return 更新后主题
   */
  @Transactional
  public ContentThemeView patchTheme(String themeId, ThemePatchRequest request) {
    return lifecycle.patchTheme(themeId, request);
  }

  /**
   * 假删主题：只写 deleted_at，不物理删除系列/课程/正文。
   *
   * @param themeId 主题 id/code
   */
  @Transactional
  public void deleteTheme(String themeId) {
    lifecycle.deleteTheme(themeId);
  }

  /**
   * 恢复已假删主题，清空 deleted_at 并置为停用。
   *
   * @param themeId 主题 id/code
   * @return 恢复后的主题视图
   */
  @Transactional
  public ContentThemeView restoreTheme(String themeId) {
    return lifecycle.restoreTheme(themeId);
  }

  /**
   * 主题路线（已落库系列+课）。
   *
   * @param themeId 主题 id/code
   * @return 路线视图
   */
  public ThemeRouteView getRoute(String themeId) {
    return lifecycle.getRoute(themeId);
  }

  /**
   * 主题下系列列表。
   *
   * @param themeId 主题 id/code
   * @return 系列视图
   */
  public List<CourseSeriesView> listSeries(String themeId) {
    return lifecycle.listSeries(themeId);
  }

  /**
   * 解析未删除的主题；已软删则 404。
   *
   * @param themeId id 或 code
   * @return 实体
   */
  public ContentThemeEntity requireActiveTheme(String themeId) {
    return lifecycle.requireActiveTheme(themeId);
  }

  /**
   * 解析已启用主题；未启用或已停用时拒绝写操作。
   *
   * @param themeId id 或 code
   * @return 实体
   */
  public ContentThemeEntity requireEnabledTheme(String themeId) {
    return lifecycle.requireEnabledTheme(themeId);
  }

  /**
   * 解析主题实体（含已软删，供恢复与元数据）。
   *
   * @param themeId id 或 code
   * @return 实体
   */
  public ContentThemeEntity requireTheme(String themeId) {
    return lifecycle.requireTheme(themeId);
  }
}
