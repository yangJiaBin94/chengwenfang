package com.chengwenfang.file.facade;


import com.chengwenfang.file.facade.enums.StorageType;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * 存储后端抽象：按 {@link StorageType} 持久化、打开与删除对象。
 */
public interface StorageProvider {
  /** 本实现对应的存储类型。 */
  StorageType type();

  /**
   * 写入字节并返回后续 open/delete 使用的 objectKey。
   *
   * @param objectKey 调用方指定的对象键
   * @param bytes 文件内容
   * @return 规范化后的 objectKey
   */
  String store(String objectKey, byte[] bytes);

  /** 打开对象输入流。 */
  InputStream open(String objectKey);

  /** 解析为本地可读路径（本地存储直接返回；远端实现可能先落地）。 */
  Path resolveLocalPath(String objectKey);

  /** 判断对象是否存在。 */
  boolean exists(String objectKey);

  /** 删除对象；失败时可静默忽略（依实现而定）。 */
  void delete(String objectKey);
}
