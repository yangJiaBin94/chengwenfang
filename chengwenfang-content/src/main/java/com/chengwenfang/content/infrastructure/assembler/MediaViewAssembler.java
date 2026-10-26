package com.chengwenfang.content.infrastructure.assembler;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.domain.entity.MediaAssetEntity;
import com.chengwenfang.content.domain.entity.MediaGenerationEntity;
import com.chengwenfang.content.facade.dto.view.CourseMediaView;
import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 媒资与配图视图组装。
 */
@Component
@RequiredArgsConstructor
public class MediaViewAssembler {

  /** 文件门面。 */
  private final FileFacade files;

  /**
   * 将媒体资产转为课程媒资视图（拼接文件 url/name）。
   *
   * @param media 媒体资产
   * @return 媒资视图
   */
  public CourseMediaView mediaAssetView(MediaAssetEntity media) {
    return mediaAssetView(media, null);
  }

  /**
   * 将媒体资产转为课程媒资视图；可传入预加载的文件视图以避免逐条查询。
   *
   * @param media     媒体资产
   * @param filesById fileId → 文件视图；缺省则现场查询
   * @return 媒资视图
   */
  public CourseMediaView mediaAssetView(MediaAssetEntity media, Map<String, FileView> filesById) {
    FileView file = resolveFile(media.getFileId(), filesById);
    return CourseMediaView.builder()
        .id(media.getId())
        .courseId(media.getWorkId())
        .articleId(media.getOutputId())
        .fileId(media.getFileId())
        .kind(media.getUsageKind())
        .name(media.getAltText() == null ? file.getFileName() : media.getAltText())
        .path(file.getUrl())
        .url(file.getUrl())
        .sourcePath(file.getObjectKey())
        .publicPath(deriveLegacyPublicPath(file.getObjectKey()))
        .sequenceNo(media.getSequenceNo())
        .build();
  }

  /**
   * 将课程生成媒体资产转为配图视图（URL 兼容 /api/images/{id}）。
   *
   * @param asset      媒体资产
   * @param generation 生成参数；可空
   * @return 配图视图
   */
  public GeneratedImageView generatedMediaAssetView(
      MediaAssetEntity asset, MediaGenerationEntity generation) {
    String fileId = asset.getFileId();
    return GeneratedImageView.builder()
        .id(asset.getId())
        .articleId(asset.getOutputId())
        .kind(asset.getUsageKind())
        .sequenceNo(asset.getSequenceNo())
        .prompt(generation == null ? null : generation.getPrompt())
        .size(generation == null ? null : generation.getSize())
        .fileId(fileId)
        .publicUrl("/api/images/" + asset.getId())
        .contentHash(asset.getContentHash())
        .width(asset.getWidth())
        .height(asset.getHeight())
        .jobId(generation == null ? null : generation.getJobId())
        .createdAt(asset.getCreatedAt() == null ? null : asset.getCreatedAt().toString())
        .build();
  }

  /**
   * 从 stored_files.object_key 反推历史上文中的 public 路径。
   *
   * @param objectKey 对象键；可空
   * @return 以 / 开头的 public 路径；无法识别时 null
   */
  public static String deriveLegacyPublicPath(String objectKey) {
    if (StrUtil.isBlank(objectKey)) {
      return null;
    }
    String normalized = objectKey.replace('\\', '/');
    if (normalized.startsWith("public/")) {
      return "/" + normalized.substring("public/".length());
    }
    if (normalized.startsWith("public")) {
      return normalized.substring("public".length());
    }
    return null;
  }

  /**
   * 解析文件视图：优先用批量预加载 Map，否则单条查询。
   *
   * @param fileId    文件 id
   * @param filesById 预加载缓存；可空
   * @return 文件视图；找不到为 null
   */
  private FileView resolveFile(String fileId, Map<String, FileView> filesById) {
    if (filesById != null && filesById.containsKey(fileId)) {
      return filesById.get(fileId);
    }
    return files.get(fileId);
  }
}
