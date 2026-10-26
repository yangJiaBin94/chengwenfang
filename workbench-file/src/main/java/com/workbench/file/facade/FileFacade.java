package com.workbench.file.facade;

import com.workbench.file.facade.dto.view.FileView;
import com.workbench.file.facade.dto.view.StoredFileView;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * 文件门面：按 fileId 读写元数据与字节，并支持本地路径登记/写入。
 */
public interface FileFacade {
  /** 按 id 返回文件视图（含 url、哈希等元数据）。 */
  FileView get(String fileId);

  /**
   * 按 id 集合批量返回文件视图（一次 IN 查询）。
   *
   * @param fileIds 文件主键集合；可空
   * @return fileId → 视图；缺失 id 不出现在结果中
   */
  Map<String, FileView> getMany(Collection<String> fileIds);

  /**
   * 解析文件对应的本地磁盘路径。
   *
   * @throws com.workbench.common.BusinessException 文件不存在或存储类型未实现时
   */
  Path getLocalPath(String fileId);

  /** 读取文件全部字节；读失败时抛出业务异常。 */
  byte[] getBytes(String fileId);

  /**
   * 登记已存在的本地文件，不拷贝字节；同 objectKey 已存在则更新元数据。
   *
   * @param objectKey 存储键（相对路径）
   * @param fileName 显示文件名，可空则从 key 推断
   * @param contentType MIME 类型，可空
   */
  FileView registerLocal(String objectKey, String fileName, String contentType);

  /**
   * 将字节写入 LOCAL 存储并登记/更新元数据行。
   *
   * @param objectKey 存储键
   * @param bytes 文件内容，不可为 null
   */
  FileView saveLocalBytes(String objectKey, String fileName, String contentType, byte[] bytes);

  /**
   * 以 UTF-8 原样写入文本并登记元数据（不做换行或编码转换）。
   *
   * @param objectKey 存储键
   * @param fileName  展示文件名；可空则从 key 推断
   * @param text      文本内容；null 按空串
   * @return 登记后的文件视图
   */
  StoredFileView writeText(String objectKey, String fileName, String text);

  /**
   * 按 fileId 以 UTF-8 原样读出文本。
   *
   * @param fileId 文件主键
   * @return 文本内容；空文件返回空串
   */
  String readText(String fileId);

  /** 删除物理文件并移除元数据行。 */
  void delete(String fileId);
}
