package com.workbench.content.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.IdsUtil;
import com.workbench.content.domain.entity.MediaAssetEntity;
import com.workbench.content.domain.entity.MediaGenerationEntity;
import com.workbench.content.domain.entity.WorkOutputEntity;
import com.workbench.content.facade.constant.MediaOrigins;
import com.workbench.content.facade.dto.request.PersistGeneratedImageRequest;
import com.workbench.content.facade.dto.view.GeneratedImageView;
import com.workbench.content.infrastructure.mapper.MediaAssetMapper;
import com.workbench.content.infrastructure.mapper.MediaGenerationMapper;
import com.workbench.content.infrastructure.mapper.WorkOutputMapper;
import com.workbench.file.facade.FileFacade;
import com.workbench.file.facade.dto.view.FileView;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生成配图的查询、读写与持久化（统一 media_assets / media_generations）。
 */
@Service
public class GeneratedImageService {
  private final MediaAssetMapper mediaAssetMapper;
  private final MediaGenerationMapper mediaGenerationMapper;
  private final WorkOutputMapper workOutputMapper;
  private final FileFacade files;
  private final ContentViewMapper views;
  private final ThemeVisibilityGuard visibility;

  public GeneratedImageService(
      MediaAssetMapper mediaAssetMapper,
      MediaGenerationMapper mediaGenerationMapper,
      WorkOutputMapper workOutputMapper,
      FileFacade files,
      ContentViewMapper views,
      ThemeVisibilityGuard visibility) {
    this.mediaAssetMapper = mediaAssetMapper;
    this.mediaGenerationMapper = mediaGenerationMapper;
    this.workOutputMapper = workOutputMapper;
    this.files = files;
    this.views = views;
    this.visibility = visibility;
  }

  /**
   * 列出文章已生成配图。
   *
   * @param articleId  文章 id
   * @param documentId 统一文档/产出 id；空则服务端按 articleId 解析
   * @return 按序号升序、创建时间降序的配图列表
   */
  public List<GeneratedImageView> listImages(String articleId, String documentId) {
    visibility.assertThemeActiveForArticleId(articleId);
    String outputId = StrUtil.blankToDefault(documentId, null);
    WorkOutputEntity output = StrUtil.isBlank(outputId) ? null : workOutputMapper.selectById(outputId);
    if (output == null && StrUtil.isNotBlank(articleId)) {
      // 课程：articleId 常等于产出 id；热点：articleId=works.id，按作品列生成图
      output = workOutputMapper.selectById(articleId);
      if (output != null) {
        outputId = articleId;
      } else {
        return listGeneratedImagesByWork(articleId);
      }
    }
    if (output == null) {
      return List.of();
    }
    return listGeneratedImages(outputId);
  }

  /**
   * 列出文章已生成配图（自动解析 documentId）。
   *
   * @param articleId 文章 id
   * @return 配图列表
   */
  public List<GeneratedImageView> listImages(String articleId) {
    return listImages(articleId, null);
  }

  /**
   * 获取配图元数据。
   *
   * @param imageId 配图 id
   * @return 配图视图
   * @throws BusinessException 配图不存在时 404
   */
  public GeneratedImageView getImage(String imageId) {
    visibility.assertThemeActiveForImageId(imageId);
    MediaAssetEntity asset = mediaAssetMapper.selectById(imageId);
    if (asset == null || !MediaOrigins.GENERATED.equals(asset.getOrigin())) {
      throw new BusinessException("image_not_found", "配图不存在", 404);
    }
    return views.generatedMediaAssetView(asset, mediaGenerationMapper.selectById(imageId));
  }

  /**
   * 解析配图本地路径（仅 fileId → 文件门面）。
   *
   * @param imageId 配图 id
   * @return 本地文件路径
   * @throws BusinessException 配图或文件缺失时
   */
  public Path getImageFile(String imageId) {
    visibility.assertThemeActiveForImageId(imageId);
    return files.getLocalPath(resolveFileId(imageId));
  }

  /**
   * 读取配图字节。
   *
   * @param imageId 配图 id
   * @return 文件字节
   * @throws BusinessException 读取失败时
   */
  public byte[] getImageBytes(String imageId) {
    visibility.assertThemeActiveForImageId(imageId);
    return files.getBytes(resolveFileId(imageId));
  }

  /**
   * 删除配图记录及文件（尽力清理，文件失败不抛出）。
   *
   * @param imageId 配图 id
   * @throws BusinessException 配图记录不存在时
   */
  @Transactional
  public void deleteImage(String imageId) {
    visibility.assertThemeActiveForImageId(imageId);
    MediaAssetEntity asset = mediaAssetMapper.selectById(imageId);
    if (asset == null) {
      throw new BusinessException("image_not_found", "配图不存在", 404);
    }
    String fileId = asset.getFileId();
    mediaGenerationMapper.deleteById(imageId);
    mediaAssetMapper.deleteById(imageId);
    deleteFileQuietly(fileId);
  }

  /**
   * 持久化生成配图到 media_assets。
   *
   * @param fields 配图字段；不可为 null
   * @return 新建配图视图
   * @throws BusinessException fields 为空或无法得到 fileId 时
   */
  @Transactional
  public GeneratedImageView persistGeneratedImage(PersistGeneratedImageRequest fields) {
    if (fields != null) {
      visibility.assertThemeActiveForArticleId(fields.getArticleId());
    }
    if (fields == null) {
      throw new BusinessException("image_fields_required", "配图字段不能为空", 400);
    }
    LocalDateTime now = LocalDateTime.now();
    String imageId = fields.getId() == null || fields.getId().isBlank() ? IdsUtil.newId() : fields.getId();
    String filePath = fields.getFilePath();
    String fileId = fields.getFileId();
    if (StrUtil.isBlank(fileId) && StrUtil.isNotBlank(filePath) && !"null".equals(filePath)) {
      FileView stored = files.registerLocal(
          filePath,
          ContentEntityUtil.fileNameOf(filePath),
          ContentEntityUtil.guessContentType(filePath));
      fileId = stored.getId();
    }
    if (StrUtil.isBlank(fileId)) {
      throw new BusinessException("image_file_id_required", "配图必须登记为 file_id", 400);
    }
    String outputId = StrUtil.blankToDefault(fields.getDocumentId(), fields.getArticleId());
    WorkOutputEntity output = workOutputMapper.selectById(outputId);
    if (output == null && StrUtil.isNotBlank(fields.getArticleId())) {
      output = workOutputMapper.selectById(fields.getArticleId());
      if (output != null) {
        outputId = fields.getArticleId();
      }
    }
    if (output == null) {
      throw new BusinessException("output_not_found", "产出不存在，无法挂接配图", 404);
    }
    MediaAssetEntity asset = MediaAssetEntity.builder()
        .id(imageId)
        .workId(output.getWorkId())
        .outputId(outputId)
        .origin(MediaOrigins.GENERATED)
        .mediaType("image")
        .usageKind(fields.getKind() == null ? "inline" : fields.getKind())
        .sequenceNo(fields.getSequenceNo() == null ? 0 : fields.getSequenceNo())
        .fileId(fileId)
        .contentHash(fields.getContentHash())
        .width(fields.getWidth())
        .height(fields.getHeight())
        .altText(fields.getKind() == null ? "inline" : fields.getKind())
        .createdAt(now)
        .updatedAt(now)
        .build();
    mediaAssetMapper.insert(asset);
    MediaGenerationEntity generation = MediaGenerationEntity.builder()
        .assetId(imageId)
        .prompt(fields.getPrompt())
        .providerConfigId(fields.getProviderConfigId())
        .model(fields.getModel())
        .size(fields.getSize())
        .quality(fields.getQuality())
        .outputFormat(fields.getOutputFormat() == null ? "png" : fields.getOutputFormat())
        .jobId(fields.getJobId())
        .build();
    mediaGenerationMapper.insert(generation);
    return views.generatedMediaAssetView(asset, generation);
  }

  /**
   * 列出产出下的生成配图。
   *
   * @param outputId 产出 id
   * @return 视图列表
   */
  private List<GeneratedImageView> listGeneratedImages(String outputId) {
    List<MediaAssetEntity> assets = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAssetEntity>()
        .eq(MediaAssetEntity::getOutputId, outputId)
        .eq(MediaAssetEntity::getOrigin, MediaOrigins.GENERATED)
        .orderByAsc(MediaAssetEntity::getSequenceNo)
        .orderByDesc(MediaAssetEntity::getCreatedAt));
    return toGeneratedViews(assets);
  }

  /**
   * 按作品列出全部生成配图（热点 articleId=workId 时用）。
   *
   * @param workId 作品 id
   * @return 视图列表
   */
  private List<GeneratedImageView> listGeneratedImagesByWork(String workId) {
    List<MediaAssetEntity> assets = mediaAssetMapper.selectList(new LambdaQueryWrapper<MediaAssetEntity>()
        .eq(MediaAssetEntity::getWorkId, workId)
        .eq(MediaAssetEntity::getOrigin, MediaOrigins.GENERATED)
        .orderByAsc(MediaAssetEntity::getSequenceNo)
        .orderByDesc(MediaAssetEntity::getCreatedAt));
    return toGeneratedViews(assets);
  }

  /**
   * 媒资行转配图视图。
   *
   * @param assets 媒资列表
   * @return 视图列表
   */
  private List<GeneratedImageView> toGeneratedViews(List<MediaAssetEntity> assets) {
    if (assets == null || assets.isEmpty()) {
      return List.of();
    }
    // 一次批量加载生成参数，避免列表按资产逐条 selectById
    List<String> assetIds = assets.stream()
        .map(MediaAssetEntity::getId)
        .filter(Objects::nonNull)
        .toList();
    Map<String, MediaGenerationEntity> generationsById = assetIds.isEmpty()
        ? Map.of()
        : mediaGenerationMapper.selectByIds(assetIds).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(
                MediaGenerationEntity::getAssetId, Function.identity(), (a, b) -> a, HashMap::new));
    List<GeneratedImageView> viewsOut = new ArrayList<>();
    for (MediaAssetEntity asset : assets) {
      viewsOut.add(views.generatedMediaAssetView(asset, generationsById.get(asset.getId())));
    }
    return viewsOut;
  }

  /**
   * 解析配图 file_id。
   *
   * @param imageId 配图/资产 id
   * @return file id
   */
  private String resolveFileId(String imageId) {
    MediaAssetEntity asset = mediaAssetMapper.selectById(imageId);
    if (asset == null || StrUtil.isBlank(asset.getFileId())) {
      throw new BusinessException("image_file_id_missing", "配图缺少 file_id", 404);
    }
    return asset.getFileId();
  }

  /**
   * 尽力删除文件，忽略失败。
   *
   * @param fileId 文件 id；可空
   */
  private void deleteFileQuietly(String fileId) {
    if (StrUtil.isBlank(fileId)) {
      return;
    }
    try {
      files.delete(fileId);
    } catch (Exception ignored) {
      // 尽力删除，忽略文件侧失败
    }
  }
}
