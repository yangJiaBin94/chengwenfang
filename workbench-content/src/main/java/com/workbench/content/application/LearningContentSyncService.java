package com.workbench.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.common.BusinessException;
import com.workbench.common.IdsUtil;
import com.workbench.content.domain.entity.ArticleEntity;
import com.workbench.content.domain.entity.CourseEntity;
import com.workbench.content.domain.entity.CourseOutputSourceEntity;
import com.workbench.content.domain.entity.CourseSeriesEntity;
import com.workbench.content.domain.entity.MediaAssetEntity;
import com.workbench.content.domain.entity.WorkOutputEntity;
import com.workbench.content.facade.constant.CourseOutputChannels;
import com.workbench.content.facade.constant.MediaOrigins;
import com.workbench.content.facade.dto.view.CourseMediaView;
import com.workbench.content.facade.dto.view.SyncLearningContentView;
import com.workbench.content.infrastructure.mapper.CourseOutputSourceMapper;
import com.workbench.content.infrastructure.mapper.CourseSeriesMapper;
import com.workbench.content.infrastructure.mapper.MediaAssetMapper;
import com.workbench.content.infrastructure.mapper.WorkOutputMapper;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.FileView;
import com.workbench.infra.support.StorageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从 workbench-data.json 同步学习内容到库，并重写正文媒资 URL。
 */
@Service
public class LearningContentSyncService {
  private final CourseStore courseStore;
  private final CourseSeriesMapper courseSeriesMapper;
  private final MediaAssetMapper mediaAssetMapper;
  private final CourseOutputSourceMapper courseOutputSourceMapper;
  private final WorkOutputMapper workOutputMapper;
  private final WorkContentStoreImpl workContents;
  private final FileFacade files;
  private final StorageService storage;
  private final ObjectMapper objectMapper;
  private final CourseShellService shell;
  private final ContentEntityUtil support;

  public LearningContentSyncService(
      CourseStore courseStore,
      CourseSeriesMapper courseSeriesMapper,
      MediaAssetMapper mediaAssetMapper,
      CourseOutputSourceMapper courseOutputSourceMapper,
      WorkOutputMapper workOutputMapper,
      WorkContentStoreImpl workContents,
      FileFacade files,
      StorageService storage,
      ObjectMapper objectMapper,
      CourseShellService shell,
      ContentEntityUtil support) {
    this.courseStore = courseStore;
    this.courseSeriesMapper = courseSeriesMapper;
    this.mediaAssetMapper = mediaAssetMapper;
    this.courseOutputSourceMapper = courseOutputSourceMapper;
    this.workOutputMapper = workOutputMapper;
    this.workContents = workContents;
    this.files = files;
    this.storage = storage;
    this.objectMapper = objectMapper;
    this.shell = shell;
    this.support = support;
  }

  /**
   * 从 public/data/workbench-data.json 同步学习内容到库：
   * 课程元数据、正文文件、媒资登记，并回写正文中的图片 URL。
   *
   * @return 同步统计（课程数、媒资数、索引路径）
   * @throws BusinessException 索引文件缺失或 JSON 非法时
   */
  @Transactional
  public SyncLearningContentView syncLearningContent() {
    Path indexPath = storage.contentRoot().resolve("public/data/workbench-data.json");
    if (!Files.isRegularFile(indexPath)) {
      throw new BusinessException(
          "learning_index_missing",
          "内容根目录下缺少 public/data/workbench-data.json",
          404);
    }
    Map<String, Object> index;
    try {
      index = objectMapper.readValue(indexPath.toFile(), new TypeReference<Map<String, Object>>() {});
    } catch (Exception exception) {
      throw new BusinessException("learning_index_invalid", exception.getMessage(), 400);
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> courses = (List<Map<String, Object>>) index.getOrDefault("courses", List.of());
    int courseCount = 0;
    int mediaCount = 0;
    LocalDateTime now = LocalDateTime.now();
    for (Map<String, Object> row : courses) {
      if (row == null || row.get("id") == null) {
        continue;
      }
      String courseId = String.valueOf(row.get("id"));
      shell.ensureCourseShell(courseId);
      CourseEntity course = support.requireCourse(courseId);
      if (row.get("title") != null) {
        course.setTitle(String.valueOf(row.get("title")));
      }
      if (row.get("summary") != null) {
        course.setSummary(String.valueOf(row.get("summary")));
      }
      if (row.get("lessonNo") instanceof Number number) {
        course.setLessonNo(number.intValue());
      } else if (row.get("lessonNo") != null) {
        try {
          course.setLessonNo(Integer.parseInt(String.valueOf(row.get("lessonNo"))));
        } catch (NumberFormatException ignored) {
          // 课序号非法时保留库中原值
        }
      }
      if (row.get("status") != null) {
        course.setStatus(String.valueOf(row.get("status")));
      }
      if (row.get("progress") instanceof Number number) {
        course.setProgress(number.intValue());
      }
      if (row.get("series") != null) {
        course.setSeriesId(ensureSeriesId(String.valueOf(row.get("series")), ContentViewMapper.DEFAULT_THEME_ID));
        course.setThemeId(ContentViewMapper.DEFAULT_THEME_ID);
      }
      course.setUpdatedAt(now);
      courseStore.updateById(course);

      String draftPath = row.get("draftPath") == null ? null : String.valueOf(row.get("draftPath"));
      String wechatPath = row.get("wechatPath") == null ? null : String.valueOf(row.get("wechatPath"));
      upsertArticleFromPath(course.getCurrentDraftDocumentId(), draftPath, now);
      upsertArticleFromPath(course.getCurrentWechatDocumentId(), wechatPath, now);

      // 清空并重建课程同步媒资（publicPath 仅用于本轮正文重写）
      mediaAssetMapper.delete(new LambdaQueryWrapper<MediaAssetEntity>()
          .eq(MediaAssetEntity::getWorkId, courseId)
          .eq(MediaAssetEntity::getOrigin, MediaOrigins.SYNCED));
      List<String> publicPaths = extractImagePublicPaths(row);
      List<CourseMediaView> mediaViews = new ArrayList<>();
      int seq = 0;
      for (String publicPath : publicPaths) {
        String sourcePath = toSourcePath(publicPath);
        FileView stored;
        try {
          stored = files.registerLocal(
              sourcePath, ContentEntityUtil.fileNameOf(publicPath), ContentEntityUtil.guessContentType(sourcePath));
        } catch (BusinessException ex) {
          if (!"file_missing".equals(ex.getCode())) {
            throw ex;
          }
          String publicKey = publicPath.startsWith("/") ? "public" + publicPath : "public/" + publicPath;
          stored = files.registerLocal(
              publicKey, ContentEntityUtil.fileNameOf(publicPath), ContentEntityUtil.guessContentType(publicKey));
        }
        MediaAssetEntity media = MediaAssetEntity.builder()
            .id(IdsUtil.newId())
            .workId(courseId)
            .outputId(course.getCurrentWechatDocumentId())
            .origin(MediaOrigins.SYNCED)
            .mediaType("image")
            .usageKind("image")
            .altText(ContentEntityUtil.fileNameOf(publicPath))
            .sequenceNo(seq++)
            .fileId(stored.getId())
            .createdAt(now)
            .updatedAt(now)
            .build();
        mediaAssetMapper.insert(media);
        mediaViews.add(CourseMediaView.builder()
            .id(media.getId())
            .courseId(media.getWorkId())
            .articleId(media.getOutputId())
            .fileId(media.getFileId())
            .kind(media.getUsageKind())
            .name(media.getAltText())
            .path(stored.getUrl())
            .url(stored.getUrl())
            .sourcePath(stored.getObjectKey())
            .publicPath(publicPath)
            .sequenceNo(media.getSequenceNo())
            .build());
        mediaCount++;
      }

      rewriteAndPersistArticleMedia(course.getCurrentDraftDocumentId(), mediaViews, now);
      rewriteAndPersistArticleMedia(course.getCurrentWechatDocumentId(), mediaViews, now);
      courseCount++;
    }

    return SyncLearningContentView.builder()
        .ok(true)
        .courses(courseCount)
        .media(mediaCount)
        .indexPath(indexPath.toString())
        .build();
  }

  /**
   * 从相对路径读取 Markdown 写入产出版本，并登记源文件。
   *
   * @param articleId    目标产出 id
   * @param relativePath 内容根相对路径；空则跳过
   * @param now          时间戳
   * @throws BusinessException 文件可读但读取失败时 500
   */
  public void upsertArticleFromPath(String articleId, String relativePath, LocalDateTime now) {
    if (articleId == null || relativePath == null || relativePath.isBlank()) {
      return;
    }
    ArticleEntity article = support.findArticleOrNull(articleId);
    if (article == null) {
      return;
    }
    String normalized = relativePath.replace('\\', '/');
    Path file = storage.resolveContent(normalized);
    String registeredKey = normalized;
    if (!Files.isRegularFile(file)) {
      Path publicMirror = storage.resolveContent(toPublicTxtPath(normalized));
      if (Files.isRegularFile(publicMirror)) {
        file = publicMirror;
        registeredKey = toPublicTxtPath(normalized);
      } else {
        article.setFilePath(normalized);
        article.setUpdatedAt(now);
        upsertCourseSource(articleId, normalized, null);
        syncOutputShell(article);
        return;
      }
    }
    try {
      String markdown = Files.readString(file, StandardCharsets.UTF_8);
      FileView stored = files.registerLocal(
          registeredKey,
          ContentEntityUtil.fileNameOf(registeredKey),
          "text/markdown");
      article.setContent(markdown);
      article.setContentHash(ContentEntityUtil.sha256(markdown));
      article.setWordCount(ContentEntityUtil.wordCount(markdown));
      article.setFilePath(normalized);
      article.setSourceFileId(stored.getId());
      article.setUpdatedAt(now);
      ensureOutputForArticle(article);
      support.overlayVersionIdentityFromDocuments(article);
      String previousBody = workContents.readCurrent(article.getId());
      boolean changed = previousBody == null || !markdown.equals(previousBody);
      if (changed) {
        int written = workContents.appendVersion(
            article.getId(), markdown, "learning_sync", "Sync learning content body", null);
        article.setCurrentVersionNo(written);
      }
      upsertCourseSource(articleId, normalized, stored.getId());
      syncOutputShell(article);
    } catch (Exception exception) {
      throw new BusinessException("content_read_failed", "读取失败 " + relativePath + "：" + exception.getMessage(), 500);
    }
  }

  /**
   * 重写文章正文中的 legacy 媒资路径并落库。
   *
   * @param articleId  文章 id；空则跳过
   * @param mediaViews 课程媒资视图（含 url/publicPath）
   * @param now        时间戳
   */
  public void rewriteAndPersistArticleMedia(String articleId, List<CourseMediaView> mediaViews, LocalDateTime now) {
    if (articleId == null) {
      return;
    }
    ArticleEntity article = support.findArticleOrNull(articleId);
    if (article == null) {
      return;
    }
    String body = workContents.readCurrent(article.getId());
    if (StrUtil.isBlank(body)) {
      body = article.getContent() == null ? "" : article.getContent();
    }
    if (body.isBlank()) {
      return;
    }
    String rewritten = ContentEntityUtil.rewriteContentMediaUrls(body, mediaViews);
    if (rewritten.equals(body)) {
      return;
    }
    support.overlayVersionIdentityFromDocuments(article);
    article.setContent(rewritten);
    article.setContentHash(ContentEntityUtil.sha256(rewritten));
    article.setWordCount(ContentEntityUtil.wordCount(rewritten));
    article.setUpdatedAt(now);
    ensureOutputForArticle(article);
    int written = workContents.appendVersion(
        article.getId(), rewritten, "learning_sync", "Rewrite learning media urls", null);
    article.setCurrentVersionNo(written);
  }

  /**
   * 从索引行提取图片 public 路径：优先 imageAssets.path，其次 imagePublicPaths。
   *
   * @param row 课程索引 Map
   * @return 去重后的路径列表
   */
  public static List<String> extractImagePublicPaths(Map<String, Object> row) {
    List<String> out = new ArrayList<>();
    Object assets = row.get("imageAssets");
    if (assets instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> map && map.get("path") != null) {
          out.add(String.valueOf(map.get("path")));
        }
      }
    }
    if (!out.isEmpty()) {
      return out.stream().distinct().toList();
    }
    Object paths = row.get("imagePublicPaths");
    if (paths instanceof List<?> list) {
      for (Object item : list) {
        if (item != null) {
          out.add(String.valueOf(item));
        }
      }
    }
    return out.stream().distinct().toList();
  }

  /**
   * 按系列名确保系列行存在，返回 seriesId。
   *
   * @param seriesName 系列显示名
   * @return 系列 id；名为空时 null
   */
  public String ensureSeriesId(String seriesName) {
    return ensureSeriesId(seriesName, ContentViewMapper.DEFAULT_THEME_ID);
  }

  /**
   * 按主题 + 系列名确保系列行存在。
   *
   * @param seriesName 系列显示名
   * @param themeId    主题 id
   * @return 系列 id；名为空时 null
   */
  public String ensureSeriesId(String seriesName, String themeId) {
    if (seriesName == null || seriesName.isBlank()) {
      return null;
    }
    String resolvedThemeId = themeId == null || themeId.isBlank()
        ? ContentViewMapper.DEFAULT_THEME_ID
        : themeId;
    CourseSeriesEntity existing = courseSeriesMapper.selectOne(new LambdaQueryWrapper<CourseSeriesEntity>()
        .eq(CourseSeriesEntity::getThemeId, resolvedThemeId)
        .eq(CourseSeriesEntity::getName, seriesName)
        .last("LIMIT 1"));
    if (existing != null) {
      return existing.getId();
    }
    LocalDateTime now = LocalDateTime.now();
    CourseSeriesEntity series = new CourseSeriesEntity();
    series.setId("series-" + ContentEntityUtil.sha256(resolvedThemeId + ":" + seriesName).substring(0, 16));
    series.setThemeId(resolvedThemeId);
    series.setName(seriesName);
    series.setSortOrder(100);
    series.setCreatedAt(now);
    series.setUpdatedAt(now);
    courseSeriesMapper.insert(series);
    return series.getId();
  }

  /**
   * 将 public 风格路径转为可登记的源路径（优先 content/）。
   *
   * @param publicPath 索引中的 public 路径
   * @return 归一化源路径
   */
  public static String toSourcePath(String publicPath) {
    String normalized = publicPath == null ? "" : publicPath.replace('\\', '/');
    if (normalized.startsWith("/content/")) {
      return "content/" + normalized.substring("/content/".length());
    }
    if (normalized.startsWith("content/")) {
      return normalized;
    }
    if (normalized.startsWith("public/")) {
      return normalized;
    }
    return normalized.startsWith("/") ? normalized.substring(1) : normalized;
  }

  /**
   * 将 content/*.md 映射为 public/content/*.md.txt 镜像路径。
   *
   * @param relativePath 相对路径
   * @return public 镜像路径
   */
  public static String toPublicTxtPath(String relativePath) {
    String normalized = relativePath.replace('\\', '/');
    if (normalized.startsWith("content/")) {
      String rest = normalized.substring("content/".length());
      if (rest.endsWith(".md")) {
        return "public/content/" + rest + ".txt";
      }
      return "public/content/" + rest;
    }
    return normalized;
  }

  /**
   * 确保产出壳存在。
   *
   * @param article 文章投影
   */
  private void ensureOutputForArticle(ArticleEntity article) {
    if (article == null || StrUtil.isBlank(article.getId()) || StrUtil.isBlank(article.getCourseId())) {
      return;
    }
    workContents.ensureOutput(
        article.getId(),
        article.getCourseId(),
        CourseOutputChannels.channelCode(article.getKind()),
        article.getTitle(),
        article.getStatus());
  }

  /**
   * 回写产出壳标题/状态。
   *
   * @param article 文章投影
   */
  private void syncOutputShell(ArticleEntity article) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return;
    }
    WorkOutputEntity output = workOutputMapper.selectById(article.getId());
    if (output == null) {
      return;
    }
    boolean dirty = false;
    if (StrUtil.isNotBlank(article.getTitle()) && !article.getTitle().equals(output.getTitle())) {
      output.setTitle(article.getTitle());
      dirty = true;
    }
    if (StrUtil.isNotBlank(article.getStatus()) && !article.getStatus().equals(output.getStatus())) {
      output.setStatus(article.getStatus());
      dirty = true;
    }
    if (dirty) {
      output.setUpdatedAt(LocalDateTime.now());
      workOutputMapper.updateById(output);
    }
  }

  /**
   * 写入或更新课程产出同步来源。
   *
   * @param outputId         产出 id
   * @param syncRelativePath 同步相对路径
   * @param sourceFileId     源文件 id；可空
   */
  private void upsertCourseSource(String outputId, String syncRelativePath, String sourceFileId) {
    if (StrUtil.isBlank(outputId)) {
      return;
    }
    CourseOutputSourceEntity existing = courseOutputSourceMapper.selectById(outputId);
    if (existing == null) {
      courseOutputSourceMapper.insert(CourseOutputSourceEntity.builder()
          .outputId(outputId)
          .syncRelativePath(syncRelativePath)
          .sourceFileId(sourceFileId)
          .build());
      return;
    }
    existing.setSyncRelativePath(syncRelativePath);
    if (sourceFileId != null) {
      existing.setSourceFileId(sourceFileId);
    }
    courseOutputSourceMapper.updateById(existing);
  }
}
