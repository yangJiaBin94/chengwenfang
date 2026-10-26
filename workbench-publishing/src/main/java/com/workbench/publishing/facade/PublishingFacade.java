package com.workbench.publishing.facade;


import com.workbench.publishing.facade.dto.request.PublishingSnapshotCreateRequest;
import com.workbench.publishing.facade.dto.request.LayoutThemeSaveRequest;
import com.workbench.publishing.facade.dto.view.PublishingRenderView;
import com.workbench.publishing.facade.dto.view.PublishingSnapshotView;
import com.workbench.publishing.facade.dto.view.LayoutThemeView;
import java.util.List;

/** 公众号排版与主题门面：渲染 Markdown、管理主题与发布快照。 */
public interface PublishingFacade {
  /** 按主题将 Markdown 渲染为公众号兼容 HTML。 */
  PublishingRenderView renderMarkdown(String markdown, String themeKey);

  /** 列出可用排版主题。 */
  List<LayoutThemeView> listThemes();

  /** 创建排版主题。 */
  LayoutThemeView createTheme(LayoutThemeSaveRequest body);

  /** 更新主题配置。 */
  LayoutThemeView updateTheme(String id, LayoutThemeSaveRequest body);

  /** 删除主题。 */
  void deleteTheme(String id);

  /** 克隆主题。 */
  LayoutThemeView cloneTheme(String id);

  /** 设置主题启用/停用状态。 */
  LayoutThemeView setStatus(String id, String status);

  /** 为文章创建发布排版快照。 */
  PublishingSnapshotView createSnapshot(String articleId, String markdown, PublishingSnapshotCreateRequest body);

  /** 列出文章的发布快照。 */
  List<PublishingSnapshotView> listSnapshots(String articleId);

  /**
   * 列出文章的发布快照（可按 document_id 双键查询）。
   *
   * @param articleId  遗留文章 ID
   * @param documentId 统一文档 ID；可空
   * @return 快照列表
   */
  List<PublishingSnapshotView> listSnapshots(String articleId, String documentId);
}
