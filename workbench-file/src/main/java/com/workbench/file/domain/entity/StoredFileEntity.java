package com.workbench.file.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.workbench.file.facade.StorageProvider;
import com.workbench.file.facade.enums.StorageType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 已登记文件元数据（表 {@code stored_files}），物理内容由 {@link StorageProvider} 管理。 */
@TableName("stored_files")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredFileEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 存储类型名，对应 {@link StorageType}。 */
  @TableField("storage_type")
  private String storageType;
  /** 桶名或根标识；LOCAL 通常为空。 */
  @TableField("bucket_or_root")
  private String bucketOrRoot;
  /** 存储内对象键（相对路径），与物理位置对应。 */
  @TableField("object_key")
  private String objectKey;
  /** 原始文件名。 */
  @TableField("file_name")
  private String fileName;
  /** MIME 类型。 */
  @TableField("content_type")
  private String contentType;
  /** 文件大小（字节）。 */
  @TableField("size_bytes")
  private Long sizeBytes;
  /** 内容哈希（如 SHA-256 十六进制），用于完整性校验。 */
  @TableField("content_hash")
  private String contentHash;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
