package com.workbench.quality.application.review;

import cn.hutool.core.util.StrUtil;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.dto.view.ArticleView;
import com.workbench.content.facade.dto.view.CourseContentView;
import com.workbench.content.facade.dto.view.CourseMediaView;
import com.workbench.content.facade.dto.view.CourseView;
import com.workbench.quality.facade.AuditFacade;
import com.workbench.quality.facade.dto.request.AuditContextRequest;
import com.workbench.quality.facade.dto.view.AuditResultView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 课程公众号文章审核编排：组装配图上下文后调用确定性审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseArticleAuditService {
  /** Markdown 图片：![alt](url)。 */
  private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
  /** Obsidian 图片：![[path|alt]]。 */
  private static final Pattern OBSIDIAN_IMAGE =
      Pattern.compile("!\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?\\]\\]");

  /** 内容门面：文章与课程媒资。 */
  private final ContentFacade content;
  /** 审核门面：规则引擎。 */
  private final AuditFacade audit;

  /**
   * 对指定文章执行带配图上下文的确定性审核。
   *
   * @param articleId 文章 id
   * @return 审核结果
   */
  public AuditResultView auditArticle(String articleId) {
    return auditArticle(articleId, null);
  }

  /**
   * 对指定文章执行带配图上下文的确定性审核。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空（课程轨通常等于 articleId）
   * @return 审核结果
   */
  public AuditResultView auditArticle(String articleId, String documentId) {
    ArticleView article = content.getArticle(articleId);
    String resolvedDocumentId = StrUtil.blankToDefault(documentId, article.getDocumentId());
    warnIfDocumentMismatch(articleId, documentId, article.getDocumentId());
    AuditResultView result = audit.auditContext(buildContext(article));
    // 确定性结果落统一审核表，便于历史追溯
    audit.persistCourseAudit(articleId, resolvedDocumentId, result);
    log.info("课程文章审核 articleId={} documentId={} score={} failed={}",
        articleId, resolvedDocumentId, result.getScore(), result.getFailed());
    return result;
  }

  /**
   * 补齐配图元数据后复审。
   *
   * @param articleId 文章 id
   * @return 修复后的审核结果
   */
  public AuditResultView repairMetadataAndAudit(String articleId) {
    return repairMetadataAndAudit(articleId, null);
  }

  /**
   * 补齐配图元数据后复审。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档 id；可空
   * @return 修复后的审核结果
   */
  public AuditResultView repairMetadataAndAudit(String articleId, String documentId) {
    // 先绑定缺失的 articleId / 名称，再按最新媒资复审并落库
    ArticleView article = content.repairMetadata(articleId);
    String resolvedDocumentId = StrUtil.blankToDefault(documentId, article.getDocumentId());
    warnIfDocumentMismatch(articleId, documentId, article.getDocumentId());
    AuditResultView result = audit.auditContext(buildContext(article));
    audit.persistCourseAudit(articleId, resolvedDocumentId, result);
    log.info("课程文章元数据修复并复审完成 articleId={} documentId={} score={} warnings={}",
        articleId,
        resolvedDocumentId,
        result.getScore(),
        result.getWarnings());
    return result;
  }

  /**
   * 调用方显式 documentId 与文章解析结果不一致时打告警。
   *
   * @param articleId          文章 id
   * @param explicitDocumentId 请求中的文档 id
   * @param articleDocumentId  文章视图上的文档 id
   */
  private static void warnIfDocumentMismatch(
      String articleId, String explicitDocumentId, String articleDocumentId) {
    if (StrUtil.isBlank(explicitDocumentId)
        || StrUtil.isBlank(articleDocumentId)
        || explicitDocumentId.equals(articleDocumentId)) {
      return;
    }
    log.warn("审核 documentId 与文章不一致 articleId={} expected={} actual={}",
        articleId, articleDocumentId, explicitDocumentId);
  }

  /**
   * 从文章与所属课程组装审核上下文。
   *
   * @param article 文章视图
   * @return 审核请求
   */
  private AuditContextRequest buildContext(ArticleView article) {
    String courseId = article.getCourseId();
    String draft = "";
    String articleFilePath = null;
    List<CourseMediaView> media = List.of();
    if (StrUtil.isNotBlank(courseId)) {
      CourseView course = content.getCourse(courseId);
      CourseContentView courseContent = content.getCourseContent(courseId);
      draft = courseContent == null || courseContent.getDraft() == null
          ? ""
          : courseContent.getDraft();
      articleFilePath = course == null ? null : course.getArticleFilePath();
      media = content.listCourseMedia(courseId);
    }
    String articleBody = StrUtil.nullToEmpty(article.getContent());
    List<Map<String, Object>> imageAssets = new ArrayList<>();
    List<String> imagePublicPaths = new ArrayList<>();
    Set<String> seenPaths = new LinkedHashSet<>();
    for (CourseMediaView item : media) {
      // 引擎认 name/path/articleId/articlePath
      Map<String, Object> asset = toAssetMap(
          StrUtil.blankToDefault(item.getName(), item.getId()),
          StrUtil.blankToDefault(item.getPath(), item.getUrl()),
          StrUtil.blankToDefault(item.getArticleId(), article.getId()),
          articleFilePath);
      String path = String.valueOf(asset.get("path"));
      if (StrUtil.isNotBlank(path) && seenPaths.add(path)) {
        imageAssets.add(asset);
      }
      String publicPath = StrUtil.firstNonBlank(item.getPublicPath(), item.getUrl(), item.getPath());
      if (StrUtil.isNotBlank(publicPath)) {
        imagePublicPaths.add(publicPath);
      }
    }
    // 与「配套图片」页一致：正文/原稿里的 Markdown 图也算结构化资产，避免误报
    for (MarkdownImageRef ref : extractMarkdownImages(articleBody, draft)) {
      if (!seenPaths.add(ref.path())) {
        continue;
      }
      imageAssets.add(toAssetMap(ref.name(), ref.path(), article.getId(), articleFilePath));
      imagePublicPaths.add(ref.path());
    }
    boolean versionSaved = article.getCurrentVersionNo() != null && article.getCurrentVersionNo() > 0;
    return AuditContextRequest.builder()
        .article(articleBody)
        .draft(draft)
        .imageAssets(imageAssets)
        .imagePublicPaths(imagePublicPaths)
        .currentVersionSaved(versionSaved)
        .articleFilePath(articleFilePath)
        .build();
  }

  /**
   * 组装单条审核用配图资产。
   *
   * @param name            显示名
   * @param path            访问路径
   * @param articleId       所属文章
   * @param articleFilePath 文章文件路径（可空）
   * @return 资产 Map
   */
  private static Map<String, Object> toAssetMap(
      String name, String path, String articleId, String articleFilePath) {
    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("name", name);
    asset.put("path", path);
    asset.put("articleId", articleId);
    if (StrUtil.isNotBlank(articleFilePath)) {
      asset.put("articlePath", articleFilePath);
    }
    return asset;
  }

  /**
   * 从公众号正文与原稿提取 Markdown / Obsidian 图片引用。
   *
   * @param article 公众号正文
   * @param draft   课程原稿
   * @return 去重前的图片引用列表
   */
  private static List<MarkdownImageRef> extractMarkdownImages(String article, String draft) {
    List<MarkdownImageRef> refs = new ArrayList<>();
    for (String content : List.of(StrUtil.nullToEmpty(article), StrUtil.nullToEmpty(draft))) {
      Matcher markdown = MARKDOWN_IMAGE.matcher(content);
      while (markdown.find()) {
        String path = StrUtil.trim(markdown.group(2));
        if (StrUtil.isBlank(path)) {
          continue;
        }
        String alt = StrUtil.trim(markdown.group(1));
        refs.add(new MarkdownImageRef(StrUtil.blankToDefault(alt, fileNameOf(path)), path));
      }
      Matcher obsidian = OBSIDIAN_IMAGE.matcher(content);
      while (obsidian.find()) {
        String path = StrUtil.trim(obsidian.group(1));
        if (StrUtil.isBlank(path)) {
          continue;
        }
        String alt = StrUtil.trim(obsidian.group(2));
        refs.add(new MarkdownImageRef(StrUtil.blankToDefault(alt, fileNameOf(path)), path));
      }
    }
    return refs;
  }

  /**
   * 从路径取文件名作为缺省图片名。
   *
   * @param path 图片路径或 URL
   * @return 文件名；无法解析时返回 image
   */
  private static String fileNameOf(String path) {
    String normalized = StrUtil.blankToDefault(path, "").replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
    int query = name.indexOf('?');
    if (query >= 0) {
      name = name.substring(0, query);
    }
    return StrUtil.blankToDefault(name, "image");
  }

  /**
   * 正文中的一张图片引用。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  private static final class MarkdownImageRef {
    /** 显示名。 */
    private final String name;
    /** 路径或 URL。 */
    private final String path;
  }
}
