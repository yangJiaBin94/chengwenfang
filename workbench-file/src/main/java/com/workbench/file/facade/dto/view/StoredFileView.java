package com.workbench.file.facade.dto.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本写入登记后的文件视图（字段与 {@link FileView} 对齐，供正文存文件 API 使用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFileView {
  /** 文件 ID。 */
  private String id;
  /** 存储类型。 */
  private String storageType;
  /** 桶名或本地根。 */
  private String bucketOrRoot;
  /** 对象键/相对路径。 */
  private String objectKey;
  /** 原始文件名。 */
  private String fileName;
  /** MIME 类型。 */
  private String contentType;
  /** 字节大小。 */
  private Long sizeBytes;
  /** 内容哈希。 */
  private String contentHash;
  /** 下载 URL。 */
  private String url;
  /** 创建时间。 */
  private String createdAt;
  /** 更新时间。 */
  private String updatedAt;
}
