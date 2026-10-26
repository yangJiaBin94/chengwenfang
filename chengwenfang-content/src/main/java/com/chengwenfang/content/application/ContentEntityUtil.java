package com.chengwenfang.content.application;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.JacksonJsonSupport;
import com.chengwenfang.content.domain.entity.ArticleEntity;
import com.chengwenfang.content.domain.entity.CourseEntity;
import com.chengwenfang.content.domain.entity.CourseOutputSourceEntity;
import com.chengwenfang.content.domain.entity.MediaAssetEntity;
import com.chengwenfang.content.domain.entity.WorkOutputEntity;
import com.chengwenfang.content.facade.constant.ChannelCodes;
import com.chengwenfang.content.facade.dto.view.CourseMediaView;
import com.chengwenfang.content.facade.enums.ArticleKind;
import com.chengwenfang.content.infrastructure.mapper.CourseOutputSourceMapper;
import com.chengwenfang.content.infrastructure.mapper.MediaAssetMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 内容域实体校验与通用工具（哈希、JSON、路径、媒资 URL 重写）。
 */
@Service
public class ContentEntityUtil {
  private final CourseStore courseStore;
  private final WorkOutputMapper workOutputMapper;
  private final CourseOutputSourceMapper courseOutputSourceMapper;
  private final MediaAssetMapper mediaAssetMapper;
  private final JacksonJsonSupport json;

  /**
   * @param courseStore              课程作品存储
   * @param workOutputMapper         作品产出
   * @param courseOutputSourceMapper 课程同步来源
   * @param mediaAssetMapper         媒体资产
   * @param objectMapper             JSON
   */
  public ContentEntityUtil(
      CourseStore courseStore,
      WorkOutputMapper workOutputMapper,
      CourseOutputSourceMapper courseOutputSourceMapper,
      MediaAssetMapper mediaAssetMapper,
      ObjectMapper objectMapper) {
    this.courseStore = courseStore;
    this.workOutputMapper = workOutputMapper;
    this.courseOutputSourceMapper = courseOutputSourceMapper;
    this.mediaAssetMapper = mediaAssetMapper;
    this.json = new JacksonJsonSupport(objectMapper);
  }

  /**
   * 按 id 加载课程，不存在则抛 404。
   *
   * @param courseId 课程 id
   * @return 课程实体
   * @throws BusinessException 不存在时
   */
  public CourseEntity requireCourse(String courseId) {
    // 课程缺失统一抛业务 404
    CourseEntity course = courseStore.selectById(courseId);
    if (course == null) {
      throw new BusinessException("course_not_found", "课程不存在", 404);
    }
    return course;
  }

  /**
   * 按 id 加载文章，不存在则抛 404。
   *
   * @param articleId 文章 id
   * @return 文章实体
   * @throws BusinessException 不存在时
   */
  public ArticleEntity requireArticle(String articleId) {
    ArticleEntity article = findArticleOrNull(articleId);
    if (article == null) {
      throw new BusinessException(ErrorCodes.ARTICLE_NOT_FOUND, "文章不存在", 404);
    }
    return article;
  }

  /**
   * 按 id 加载课程文章内存投影；无对应产出则 null。
   *
   * @param articleId 文章/产出 id
   * @return 投影实体；缺失为 null
   */
  public ArticleEntity findArticleOrNull(String articleId) {
    return projectCourseArticleFromOutput(articleId);
  }

  /**
   * 按产出 id 合成课程文章投影。
   *
   * @param articleId 产出/文章 id
   * @return 内存实体；无则 null
   */
  private ArticleEntity projectCourseArticleFromOutput(String articleId) {
    if (StrUtil.isBlank(articleId)) {
      return null;
    }
    WorkOutputEntity output = workOutputMapper.selectById(articleId);
    if (output == null) {
      return null;
    }
    ArticleEntity article = new ArticleEntity();
    article.setId(articleId);
    article.setCourseId(output.getWorkId());
    article.setKind(ChannelCodes.WECHAT_MP.equals(output.getChannelCode())
        ? ArticleKind.WECHAT_ARTICLE.getValue()
        : ArticleKind.COURSE_DRAFT.getValue());
    article.setTitle(output.getTitle());
    article.setStatus(output.getStatus());
    article.setCurrentVersionNo(output.getCurrentVersionNo());
    article.setContentHash(output.getContentHash());
    article.setWordCount(output.getWordCount());
    article.setVersionToken(output.getVersionToken());
    CourseOutputSourceEntity source = courseOutputSourceMapper.selectById(articleId);
    if (source != null) {
      article.setFilePath(source.getSyncRelativePath());
      article.setSourceFileId(source.getSourceFileId());
    }
    return article;
  }

  /**
   * 用 work_outputs 覆盖内存中的 title/status/version/hash/wordCount。
   *
   * @param article 文章实体
   */
  public void overlayVersionIdentityFromDocuments(ArticleEntity article) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return;
    }
    WorkOutputEntity output = workOutputMapper.selectById(article.getId());
    if (output == null) {
      return;
    }
    if (StrUtil.isNotBlank(output.getTitle())) {
      article.setTitle(output.getTitle());
    }
    if (StrUtil.isNotBlank(output.getStatus())) {
      article.setStatus(output.getStatus());
    }
    if (output.getVersionToken() != null) {
      article.setVersionToken(output.getVersionToken());
    }
    if (output.getCurrentVersionNo() != null) {
      article.setCurrentVersionNo(output.getCurrentVersionNo());
    }
    if (StrUtil.isNotBlank(output.getContentHash())) {
      article.setContentHash(output.getContentHash());
    }
    if (output.getWordCount() != null) {
      article.setWordCount(output.getWordCount());
    }
  }

  /**
   * 按 id 加载媒体资产，不存在则抛 404。
   *
   * @param mediaId 媒资 id
   * @return 媒体资产
   * @throws BusinessException 不存在时
   */
  public MediaAssetEntity requireMedia(String mediaId) {
    MediaAssetEntity media = mediaAssetMapper.selectById(mediaId);
    if (media == null) {
      throw new BusinessException("media_not_found", "媒资不存在", 404);
    }
    return media;
  }

  /**
   * 计算字符串的 SHA-256 十六进制摘要。
   *
   * @param value 原文；null 按空串
   * @return hex 摘要；算法不可用时空串
   */
  public static String sha256(String value) {
    // null 按空串；算法异常返回空串
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      return "";
    }
  }

  /**
   * 粗略字数：去掉空白后的字符数。
   *
   * @param text 正文
   * @return 字数
   */
  public static int wordCount(String text) {
    // 去空白后字符数作为中文字数近似
    if (text == null || text.isBlank()) {
      return 0;
    }
    return text.replaceAll("\\s+", "").length();
  }

  /**
   * 将对象序列化为 JSON 字符串；失败时返回 {@code []}。
   *
   * @param value 任意可序列化对象
   * @return JSON 文本
   */
  public String writeJson(Object value) {
    // 序列化失败时返回空数组 JSON，避免拖垮写库
    return this.json.writeOrEmptyArray(value);
  }

  /**
   * 将 JSON 字符串解析为 Map；失败返回空 Map。
   *
   * @param json JSON 文本
   * @return 解析结果或空 Map
   */
  public Map<String, Object> readMap(String payload) {
    // 非法 JSON 降级为空 Map，避免抛到上层
    return this.json.readMapOrImmutableEmpty(payload);
  }

  /**
   * 按扩展名猜测 Content-Type。
   *
   * @param path 文件路径
   * @return MIME 类型字符串
   */
  public static String guessContentType(String path) {
    // 仅按扩展名猜测 MIME
    String lower = path == null ? "" : path.toLowerCase();
    if (lower.endsWith(".png")) return "image/png";
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
    if (lower.endsWith(".gif")) return "image/gif";
    if (lower.endsWith(".webp")) return "image/webp";
    if (lower.endsWith(".md") || lower.endsWith(".md.txt") || lower.endsWith(".txt")) return "text/markdown";
    return "application/octet-stream";
  }

  /**
   * 从路径截取文件名。
   *
   * @param path 路径
   * @return 文件名；空路径返回 {@code image}
   */
  public static String fileNameOf(String path) {
    // 取最后一段路径作为文件名
    if (path == null || path.isBlank()) {
      return "image";
    }
    String normalized = path.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    return slash >= 0 ? normalized.substring(slash + 1) : normalized;
  }

  /**
   * 将正文中的旧 public 路径 / 资产文件名替换为文件服务 URL。
   *
   * @param content    原文
   * @param mediaViews 媒资列表
   * @return 替换后正文
   */
  public static String rewriteContentMediaUrls(String content, List<CourseMediaView> mediaViews) {
    // 按 publicPath 与历史 assets 路径做字符串替换
    if (content == null || content.isBlank() || mediaViews == null || mediaViews.isEmpty()) {
      return content == null ? "" : content;
    }
    String out = content;
    for (CourseMediaView media : mediaViews) {
      String url = media.getUrl() == null ? "" : media.getUrl();
      String publicPath = media.getPublicPath();
      String name = media.getName();
      if (publicPath != null && !publicPath.isBlank()) {
        out = out.replace(publicPath, url);
      }
      if (name != null && !name.isBlank()) {
        out = out.replace("/content/learning-content/topics/ai-basics/assets/" + name, url);
        out = out.replace("content/learning-content/topics/ai-basics/assets/" + name, url);
      }
    }
    return out;
  }
}
