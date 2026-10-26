package com.chengwenfang.file.application;

import com.chengwenfang.file.domain.entity.StoredFileEntity;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** 存储文件实体与 API 视图转换。 */
@Component
public class StoredFileViewMapper {

  /**
   * 实体转文件视图。
   *
   * @param file 存储文件实体
   * @return 文件视图；入参为空时返回 null
   */
  public FileView toView(StoredFileEntity file) {
    if (file == null) {
      return null;
    }
    return FileView.builder()
        .id(file.getId())
        .storageType(file.getStorageType())
        .bucketOrRoot(file.getBucketOrRoot())
        .objectKey(file.getObjectKey())
        .fileName(file.getFileName())
        .contentType(file.getContentType())
        .sizeBytes(file.getSizeBytes())
        .contentHash(file.getContentHash())
        .url("/api/files/" + file.getId())
        .createdAt(dateTimeToString(file.getCreatedAt()))
        .updatedAt(dateTimeToString(file.getUpdatedAt()))
        .build();
  }

  /**
   * 实体转正文存储视图。
   *
   * @param file 存储文件实体
   * @return 正文存储视图；入参为空时返回 null
   */
  public StoredFileView toStoredView(StoredFileEntity file) {
    if (file == null) {
      return null;
    }
    return StoredFileView.builder()
        .id(file.getId())
        .storageType(file.getStorageType())
        .bucketOrRoot(file.getBucketOrRoot())
        .objectKey(file.getObjectKey())
        .fileName(file.getFileName())
        .contentType(file.getContentType())
        .sizeBytes(file.getSizeBytes())
        .contentHash(file.getContentHash())
        .url("/api/files/" + file.getId())
        .createdAt(dateTimeToString(file.getCreatedAt()))
        .updatedAt(dateTimeToString(file.getUpdatedAt()))
        .build();
  }

  /**
   * {@link FileView} 转 {@link StoredFileView}。
   *
   * @param view 文件视图
   * @return 正文存储视图；入参为空时返回 null
   */
  public StoredFileView toStoredView(FileView view) {
    if (view == null) {
      return null;
    }
    return StoredFileView.builder()
        .id(view.getId())
        .storageType(view.getStorageType())
        .bucketOrRoot(view.getBucketOrRoot())
        .objectKey(view.getObjectKey())
        .fileName(view.getFileName())
        .contentType(view.getContentType())
        .sizeBytes(view.getSizeBytes())
        .contentHash(view.getContentHash())
        .url(view.getUrl())
        .createdAt(view.getCreatedAt())
        .updatedAt(view.getUpdatedAt())
        .build();
  }

  /**
   * LocalDateTime 转 ISO 字符串。
   *
   * @param value 时间
   * @return 字符串；空则 null
   */
  private static String dateTimeToString(LocalDateTime value) {
    return value == null ? null : value.toString();
  }
}
