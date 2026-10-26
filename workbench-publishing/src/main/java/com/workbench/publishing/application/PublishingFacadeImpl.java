package com.workbench.publishing.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.common.BusinessException;
import com.workbench.common.IdsUtil;
import com.workbench.content.facade.constant.OutputFormatCodes;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.StoredFileView;
import com.workbench.publishing.domain.entity.PublishingSnapshotEntity;
import com.workbench.publishing.facade.PublishingFacade;
import com.workbench.publishing.facade.dto.request.LayoutThemeSaveRequest;
import com.workbench.publishing.facade.dto.request.PublishingSnapshotCreateRequest;
import com.workbench.publishing.facade.dto.view.LayoutThemeView;
import com.workbench.publishing.facade.dto.view.PublishingRenderView;
import com.workbench.publishing.facade.dto.view.PublishingSnapshotView;
import com.workbench.publishing.infrastructure.mapper.PublishingSnapshotMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PublishingFacade} 实现：委托主题 CRUD 与快照；Markdown 渲染委派产出渲染扩展点。
 */
@Slf4j
@Service
public class PublishingFacadeImpl implements PublishingFacade {
  /** 排版主题 CRUD 与配置加载。 */
  private final LayoutThemeService layoutThemes;
  /** 发布快照表 Mapper。 */
  private final PublishingSnapshotMapper snapshotMapper;
  /** 文件门面（HTML 落盘）。 */
  private final FileFacade files;
  /** JSON 序列化（options 哈希）。 */
  private final ObjectMapper objectMapper;
  /** 产出渲染注册表。 */
  private final OutputRendererRegistry outputRenderers;

  /**
   * 注入主题服务、快照 Mapper、文件门面、ObjectMapper 与渲染注册表。
   *
   * @param layoutThemes    排版主题服务
   * @param snapshotMapper  快照表 Mapper
   * @param files           文件门面
   * @param objectMapper    JSON 读写
   * @param outputRenderers 产出渲染注册表
   */
  public PublishingFacadeImpl(
      LayoutThemeService layoutThemes,
      PublishingSnapshotMapper snapshotMapper,
      FileFacade files,
      ObjectMapper objectMapper,
      OutputRendererRegistry outputRenderers) {
    this.layoutThemes = layoutThemes;
    this.snapshotMapper = snapshotMapper;
    this.files = files;
    this.objectMapper = objectMapper;
    this.outputRenderers = outputRenderers;
  }

  /**
   * 按主题将 Markdown 渲染为公众号兼容 HTML（委派文章渲染器）。
   *
   * @param markdown 原文 Markdown；null 按空串
   * @param themeKey 主题版本 ID 或主题 key；空/blank/null 字符串则用默认空配置
   * @return 含 html、哈希、主题信息的渲染视图
   */
  @Override
  public PublishingRenderView renderMarkdown(String markdown, String themeKey) {
    return outputRenderers.require(OutputFormatCodes.ARTICLE).render(markdown, themeKey);
  }

  /** {@inheritDoc} */
  @Override
  public List<LayoutThemeView> listThemes() {
    return layoutThemes.listThemes();
  }

  /** {@inheritDoc} */
  @Override
  public LayoutThemeView createTheme(LayoutThemeSaveRequest body) {
    return layoutThemes.createTheme(body);
  }

  /** {@inheritDoc} */
  @Override
  public LayoutThemeView updateTheme(String id, LayoutThemeSaveRequest body) {
    return layoutThemes.updateTheme(id, body);
  }

  /** {@inheritDoc} */
  @Override
  public void deleteTheme(String id) {
    layoutThemes.deleteTheme(id);
  }

  /** {@inheritDoc} */
  @Override
  public LayoutThemeView cloneTheme(String id) {
    return layoutThemes.cloneTheme(id);
  }

  /** {@inheritDoc} */
  @Override
  public LayoutThemeView setStatus(String id, String status) {
    return layoutThemes.setStatus(id, status);
  }

  /**
   * 为产出创建发布排版快照（先渲染，HTML 存文件再落库）。
   *
   * @param articleId 文章 ID（视图回填用）
   * @param markdown  快照对应 Markdown
   * @param body      创建选项；null 按空；须含 documentId / themeVersionId / articleVersionNo
   * @return 新建快照视图
   */
  @Override
  @Transactional
  public PublishingSnapshotView createSnapshot(
      String articleId, String markdown, PublishingSnapshotCreateRequest body) {
    PublishingSnapshotCreateRequest input = body == null ? new PublishingSnapshotCreateRequest() : body;
    String outputId = StrUtil.trim(StrUtil.nullToEmpty(input.getDocumentId()));
    if (StrUtil.isBlank(outputId)) {
      throw new BusinessException("output_not_found", "缺少产出 ID，无法创建发布快照", 400);
    }
    String themeVersionId = input.getThemeVersionId() == null ? "" : input.getThemeVersionId();
    int versionNo = input.getArticleVersionNo() == null ? 1 : input.getArticleVersionNo();
    // 先渲染拿到 html / 哈希
    PublishingRenderView rendered = renderMarkdown(markdown, themeVersionId);
    String html = rendered.getHtml() == null ? "" : rendered.getHtml();
    String snapshotId = IdsUtil.newId();
    // HTML 落文件，表只存 file_id
    String objectKey = "renders/" + outputId + "/" + snapshotId + ".html";
    StoredFileView stored = files.writeText(objectKey, snapshotId + ".html", html);
    LocalDateTime now = LocalDateTime.now();
    PublishingSnapshotEntity entity = new PublishingSnapshotEntity();
    entity.setId(snapshotId);
    entity.setOutputId(outputId);
    entity.setOutputVersionNo(versionNo);
    entity.setLayoutThemeVersionId(themeVersionId);
    entity.setLayoutHash(sha256(writeJson(input.getOptions() == null ? Map.of() : input.getOptions())));
    entity.setMarkdownHash(rendered.getMarkdownHash() == null
        ? sha256(markdown == null ? "" : markdown)
        : rendered.getMarkdownHash());
    entity.setHtmlFileId(stored.getId());
    entity.setHtmlHash(rendered.getHtmlHash() == null ? "" : rendered.getHtmlHash());
    entity.setCreatedAt(now);
    snapshotMapper.insert(entity);
    return snapshotView(entity, articleId);
  }

  /**
   * 列出文章的发布快照（按创建时间倒序）。
   *
   * @param articleId 文章 ID
   * @return 快照视图列表
   */
  @Override
  public List<PublishingSnapshotView> listSnapshots(String articleId) {
    return listSnapshots(articleId, null);
  }

  /**
   * 列出产出的发布快照（按创建时间倒序）。
   *
   * @param articleId  遗留文章 ID（视图回填）
   * @param documentId 产出 ID；空则返回空列表
   * @return 快照视图列表
   */
  @Override
  public List<PublishingSnapshotView> listSnapshots(String articleId, String documentId) {
    if (StrUtil.isBlank(documentId)) {
      return List.of();
    }
    return snapshotMapper.selectList(new LambdaQueryWrapper<PublishingSnapshotEntity>()
            .eq(PublishingSnapshotEntity::getOutputId, documentId.trim())
            .orderByDesc(PublishingSnapshotEntity::getCreatedAt))
        .stream()
        .map(entity -> snapshotView(entity, articleId))
        .toList();
  }

  /**
   * 快照实体转视图；images/diagnostics 恒为空列表。
   *
   * @param entity    快照实体
   * @param articleId 文章 ID
   * @return 快照视图
   */
  private PublishingSnapshotView snapshotView(PublishingSnapshotEntity entity, String articleId) {
    String html = "";
    if (StrUtil.isNotBlank(entity.getHtmlFileId())) {
      html = StrUtil.nullToEmpty(files.readText(entity.getHtmlFileId()));
    }
    return PublishingSnapshotView.builder()
        .id(entity.getId())
        .articleId(articleId)
        .articleVersionNo(entity.getOutputVersionNo())
        .themeVersionId(
            entity.getLayoutThemeVersionId() == null ? "" : entity.getLayoutThemeVersionId())
        .html(html)
        .htmlHash(entity.getHtmlHash())
        .markdownHash(entity.getMarkdownHash())
        .imageCount(0)
        .diagnostics(List.of())
        .createdAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString())
        .build();
  }

  /**
   * 对象序列化为 JSON；失败返回 {@code []}。
   *
   * @param value 任意对象
   * @return JSON 文本
   */
  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ignored) {
      return "[]";
    }
  }

  /**
   * 计算字符串 SHA-256 十六进制摘要。
   *
   * @param value 原文
   * @return hex 摘要；算法不可用时返回空串
   */
  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ignored) {
      return "";
    }
  }
}
