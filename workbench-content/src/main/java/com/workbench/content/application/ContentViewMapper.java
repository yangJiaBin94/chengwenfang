package com.workbench.content.application;

import com.workbench.content.domain.entity.ArticleEntity;
import com.workbench.content.domain.entity.ArticleLayoutEntity;
import com.workbench.content.domain.entity.ArticleVersionEntity;
import com.workbench.content.domain.entity.CourseEntity;
import com.workbench.content.domain.entity.CourseSeriesEntity;
import com.workbench.content.domain.entity.HumanReviewEntity;
import com.workbench.content.domain.entity.MediaAssetEntity;
import com.workbench.content.domain.entity.MediaGenerationEntity;
import com.workbench.content.facade.dto.view.ArticleLayoutView;
import com.workbench.content.facade.dto.view.ArticleVersionView;
import com.workbench.content.facade.dto.view.ArticleView;
import com.workbench.content.facade.dto.view.ContentModeView;
import com.workbench.content.facade.dto.view.ContentThemeView;
import com.workbench.content.facade.dto.view.CourseMediaView;
import com.workbench.content.facade.dto.view.CourseView;
import com.workbench.content.facade.dto.view.GeneratedImageView;
import com.workbench.content.facade.dto.view.HumanReviewView;
import com.workbench.content.infrastructure.assembler.ArticleLayoutViewAssembler;
import com.workbench.content.infrastructure.assembler.ArticleViewAssembler;
import com.workbench.content.infrastructure.assembler.ContentThemeViewAssembler;
import com.workbench.content.infrastructure.assembler.CourseViewAssembler;
import com.workbench.content.infrastructure.assembler.HumanReviewViewAssembler;
import com.workbench.content.infrastructure.assembler.MediaViewAssembler;
import com.workbench.file.facade.dto.view.FileView;
import com.workbench.job.facade.dto.view.JobView;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 内容域视图门面：委派各聚合 assembler（兼容既有注入点）。
 */
@Service
@RequiredArgsConstructor
public class ContentViewMapper {
  /** 默认课程体系主题 id。 */
  public static final String DEFAULT_THEME_ID = ContentThemeViewAssembler.DEFAULT_THEME_ID;
  /** 热点资讯主题 id。 */
  public static final String HOT_NEWS_THEME_ID = ContentThemeViewAssembler.HOT_NEWS_THEME_ID;

  private final ContentThemeViewAssembler themes;
  private final CourseViewAssembler courses;
  private final ArticleViewAssembler articles;
  private final MediaViewAssembler media;
  private final HumanReviewViewAssembler humanReviews;
  private final ArticleLayoutViewAssembler layouts;

  /**
   * 列出可用教学模式。
   *
   * @return 模式视图列表
   */
  public List<ContentModeView> listModes() {
    return themes.listModes();
  }

  /**
   * 迁移未执行时的静态模式/主题回退。
   *
   * @return 固定两项模式
   */
  public List<ContentModeView> staticModesFallback() {
    return themes.staticModesFallback();
  }

  /**
   * 按 themeId/code 解析主题。
   *
   * @param themeId 主题 id 或 code；空则用默认
   * @return 带 mode 信息的主题视图
   */
  public ContentThemeView resolveTheme(String themeId) {
    return themes.resolveTheme(themeId);
  }

  /**
   * 构造「课程体系教学模式」静态定义。
   *
   * @return 模式视图
   */
  public ContentModeView courseSystemMode() {
    return themes.courseSystemMode();
  }

  /**
   * 构造默认课程主题。
   *
   * @return 主题视图
   */
  public ContentThemeView courseSystemTheme() {
    return themes.courseSystemTheme();
  }

  /**
   * 构造「热点资讯模式」静态定义。
   *
   * @return 模式视图
   */
  public ContentModeView hotNewsMode() {
    return themes.hotNewsMode();
  }

  /**
   * 将课程实体组装为对外 {@link CourseView}。
   *
   * @param course       课程实体
   * @param seriesById   系列 id → 实体
   * @param articlesById 文章 id → 实体
   * @return 课程视图
   */
  public CourseView courseView(
      CourseEntity course,
      Map<String, CourseSeriesEntity> seriesById,
      Map<String, ArticleEntity> articlesById) {
    return courses.courseView(course, seriesById, articlesById);
  }

  /**
   * 将课程实体组装为对外视图（列表预加载媒资）。
   *
   * @param course        课程实体
   * @param seriesById    系列映射
   * @param articlesById  文章映射
   * @param mediaByWorkId 媒资按作品分组
   * @param filesById     文件索引
   * @return 课程视图
   */
  public CourseView courseView(
      CourseEntity course,
      Map<String, CourseSeriesEntity> seriesById,
      Map<String, ArticleEntity> articlesById,
      Map<String, List<MediaAssetEntity>> mediaByWorkId,
      Map<String, FileView> filesById) {
    return courses.courseView(course, seriesById, articlesById, mediaByWorkId, filesById);
  }

  /**
   * 从 courseId 推导系列显示名。
   *
   * @param courseId 课程 id
   * @return 系列名
   */
  public static String deriveSeriesName(String courseId) {
    return CourseViewAssembler.deriveSeriesName(courseId);
  }

  /**
   * 将文章实体转为对外视图。
   *
   * @param article 文章实体
   * @return 文章视图
   */
  public ArticleView articleView(ArticleEntity article) {
    return articles.articleView(article);
  }

  /**
   * 将媒体资产转为课程媒资视图。
   *
   * @param mediaAsset 媒体资产
   * @return 媒资视图
   */
  public CourseMediaView mediaAssetView(MediaAssetEntity mediaAsset) {
    return media.mediaAssetView(mediaAsset);
  }

  /**
   * 从 object_key 反推历史 public 路径。
   *
   * @param objectKey 对象键
   * @return public 路径
   */
  static String deriveLegacyPublicPath(String objectKey) {
    return MediaViewAssembler.deriveLegacyPublicPath(objectKey);
  }

  /**
   * 将生成媒资转为配图视图。
   *
   * @param asset      媒体资产
   * @param generation 生成参数
   * @return 配图视图
   */
  public GeneratedImageView generatedMediaAssetView(
      MediaAssetEntity asset, MediaGenerationEntity generation) {
    return media.generatedMediaAssetView(asset, generation);
  }

  /**
   * 将版本实体转为视图。
   *
   * @param version 版本实体
   * @return 版本视图
   */
  public ArticleVersionView versionView(ArticleVersionEntity version) {
    return articles.versionView(version);
  }

  /**
   * 人工审核实体转视图。
   *
   * @param review    审核实体
   * @param versionNo 当前版本号
   * @param articleId 文章 id
   * @return 人工审核视图
   */
  public HumanReviewView humanReviewView(
      HumanReviewEntity review, Integer versionNo, String articleId) {
    return humanReviews.humanReviewView(review, versionNo, articleId);
  }

  /**
   * 将布局实体转为视图。
   *
   * @param layout    布局实体
   * @param articleId 文章 ID
   * @return 布局视图
   */
  public ArticleLayoutView layoutView(ArticleLayoutEntity layout, String articleId) {
    return layouts.layoutView(layout, articleId);
  }

  /**
   * 任务视图透传。
   *
   * @param job 任务视图
   * @return 原样返回
   */
  public JobView jobView(JobView job) {
    return job;
  }

  /**
   * 课程媒资批量预加载入口。
   *
   * @return 课程视图组装器
   */
  public CourseViewAssembler courseAssembler() {
    return courses;
  }
}
