package com.chengwenfang.file.facade.enums;

/** 文件存储后端类型；当前仅 LOCAL 有实现，QINIU/MINIO 为预留。 */
public enum StorageType {
  LOCAL,
  QINIU,
  MINIO;

  /**
   * 将字符串解析为存储类型；空值默认 {@link #LOCAL}。
   *
   * @param raw 如 {@code LOCAL}、{@code local}
   */
  public static StorageType from(String raw) {
    if (raw == null || raw.isBlank()) {
      return LOCAL;
    }
    return StorageType.valueOf(raw.trim().toUpperCase());
  }
}
