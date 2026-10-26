package com.chengwenfang.file.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.file.domain.entity.StoredFileEntity;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import com.chengwenfang.file.facade.StorageProvider;
import com.chengwenfang.file.facade.enums.StorageType;
import com.chengwenfang.file.infrastructure.mapper.StoredFileMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;

/**
 * {@link FileFacade} 默认实现：按 storage_type 路由到 {@link StorageProvider}，并维护 stored_files 元数据。
 */
@Slf4j
@Service
public class FileFacadeImpl implements FileFacade {
  /** stored_files 元数据表读写。 */
  private final StoredFileMapper storedFileMapper;
  /** 按 {@link StorageType} 索引的存储实现。 */
  private final Map<StorageType, StorageProvider> providers;
  /** 实体到对外文件视图的映射。 */
  private final StoredFileViewMapper viewMapper;

  /**
   * 注入 Mapper、全部 StorageProvider，并按 type 建索引。
   *
   * @param storedFileMapper 元数据 Mapper
   * @param providers        Spring 注入的全部存储实现
   * @param viewMapper       视图映射器
   */
  public FileFacadeImpl(
      StoredFileMapper storedFileMapper,
      List<StorageProvider> providers,
      StoredFileViewMapper viewMapper) {
    this.storedFileMapper = storedFileMapper;
    // 同 type 多个实现时保留先注册者，顺序稳定用 LinkedHashMap
    this.providers = providers.stream()
        .collect(Collectors.toMap(StorageProvider::type, Function.identity(), (existing, replacement) -> existing, LinkedHashMap::new));
    this.viewMapper = viewMapper;
  }

  /**
   * 按 id 返回文件视图（含 url、哈希等元数据）。
   *
   * @param fileId 文件主键
   * @return 文件对外视图
   * @throws BusinessException 文件不存在时抛出 404
   */
  @Override
  public FileView get(String fileId) {
    return view(require(fileId));
  }

  /**
   * 按 id 集合批量返回文件视图（一次 IN 查询）。
   *
   * @param fileIds 文件主键集合；可空
   * @return fileId → 视图；缺失 id 不出现在结果中
   */
  @Override
  public Map<String, FileView> getMany(Collection<String> fileIds) {
    if (fileIds == null || fileIds.isEmpty()) {
      return Map.of();
    }
    List<String> ids = fileIds.stream()
        .filter(StrUtil::isNotBlank)
        .distinct()
        .toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<StoredFileEntity> rows = storedFileMapper.selectByIds(ids);
    Map<String, FileView> result = new LinkedHashMap<>();
    for (StoredFileEntity row : rows) {
      if (row != null && StrUtil.isNotBlank(row.getId())) {
        result.put(row.getId(), view(row));
      }
    }
    return result;
  }

  /**
   * 解析文件对应的本地磁盘路径。
   *
   * @param fileId 文件主键
   * @return 本地绝对路径
   * @throws BusinessException 文件不存在或存储类型未实现时
   */
  @Override
  public Path getLocalPath(String fileId) {
    StoredFileEntity file = require(fileId);
    return provider(file).resolveLocalPath(file.getObjectKey());
  }

  /**
   * 读取文件全部字节。
   *
   * @param fileId 文件主键
   * @return 文件内容字节数组
   * @throws BusinessException 文件不存在、存储未实现或读盘失败时
   */
  @Override
  public byte[] getBytes(String fileId) {
    try {
      return Files.readAllBytes(getLocalPath(fileId));
    } catch (BusinessException exception) {
      // 业务异常原样抛出，避免包装成通用读失败
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException(com.chengwenfang.common.ErrorCodes.FILE_MISSING, exception.getMessage(), 500);
    }
  }

  /**
   * 登记已存在本地文件；同 LOCAL+object_key 则刷新元数据。
   *
   * @param objectKey   对象键（路径）；会规范化斜杠与前导 /
   * @param fileName    展示用文件名；空则保留原值或从 key 推导
   * @param contentType MIME 类型；空则不覆盖已有值
   * @return 登记或刷新后的文件视图
   * @throws BusinessException key 为空、物理文件不存在或元数据读取失败时
   */
  @Override
  @Transactional
  public FileView registerLocal(String objectKey, String fileName, String contentType) {
    String key = normalizeKey(objectKey);
    StoredFileEntity existing = findByTypeAndKey(StorageType.LOCAL, key);
    LocalDateTime now = LocalDateTime.now();
    if (existing != null) {
      // 已登记：刷新文件名/类型/大小/哈希
      existing.setFileName(fileName == null || fileName.isBlank() ? existing.getFileName() : fileName);
      if (contentType != null && !contentType.isBlank()) {
        existing.setContentType(contentType);
      }
      Path path = provider(StorageType.LOCAL).resolveLocalPath(key);
      try {
        existing.setSizeBytes(Files.size(path));
        existing.setContentHash(sha256(Files.readAllBytes(path)));
      } catch (Exception ignored) {
        // keep previous metadata
      }
      existing.setUpdatedAt(now);
      storedFileMapper.updateById(existing);
      return view(existing);
    }

    // 新登记：物理文件必须已存在
    StorageProvider local = provider(StorageType.LOCAL);
    if (!local.exists(key)) {
      throw new BusinessException("file_missing", "本地文件不存在：" + key, 404);
    }
    Path path = local.resolveLocalPath(key);
    StoredFileEntity entity = new StoredFileEntity();
    entity.setId(IdsUtil.newId());
    entity.setStorageType(StorageType.LOCAL.name());
    entity.setBucketOrRoot(null);
    entity.setObjectKey(key);
    entity.setFileName(fileName == null || fileName.isBlank() ? fileNameOf(key) : fileName);
    entity.setContentType(contentType);
    try {
      entity.setSizeBytes(Files.size(path));
      entity.setContentHash(sha256(Files.readAllBytes(path)));
    } catch (Exception exception) {
      throw new BusinessException("file_meta_failed", exception.getMessage(), 500);
    }
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    storedFileMapper.insert(entity);
    return view(entity);
  }

  /**
   * 写入本地字节并 upsert stored_files 行。
   *
   * @param objectKey   对象键；会规范化
   * @param fileName    展示用文件名；空则保留原值或从 key 推导
   * @param contentType MIME 类型
   * @param bytes       文件内容；不可为 null
   * @return 写入或更新后的文件视图
   * @throws BusinessException bytes 为空或 key 非法时
   */
  @Override
  @Transactional
  public FileView saveLocalBytes(String objectKey, String fileName, String contentType, byte[] bytes) {
    if (bytes == null) {
      throw new BusinessException("file_bytes_required", "bytes 不能为空", 400);
    }
    String key = normalizeKey(objectKey);
    // 先写物理文件，再 upsert 元数据行
    provider(StorageType.LOCAL).store(key, bytes);
    LocalDateTime now = LocalDateTime.now();
    StoredFileEntity existing = findByTypeAndKey(StorageType.LOCAL, key);
    if (existing != null) {
      existing.setFileName(fileName == null || fileName.isBlank() ? existing.getFileName() : fileName);
      existing.setContentType(contentType);
      existing.setSizeBytes((long) bytes.length);
      existing.setContentHash(sha256(bytes));
      existing.setUpdatedAt(now);
      storedFileMapper.updateById(existing);
      return view(existing);
    }
    StoredFileEntity entity = new StoredFileEntity();
    entity.setId(IdsUtil.newId());
    entity.setStorageType(StorageType.LOCAL.name());
    entity.setObjectKey(key);
    entity.setFileName(fileName == null || fileName.isBlank() ? fileNameOf(key) : fileName);
    entity.setContentType(contentType);
    entity.setSizeBytes((long) bytes.length);
    entity.setContentHash(sha256(bytes));
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    storedFileMapper.insert(entity);
    return view(entity);
  }

  /**
   * 以 UTF-8 原样写入文本并登记元数据。
   *
   * @param objectKey 存储键
   * @param fileName  展示文件名；可空则从 key 推断
   * @param text      文本；null 按空串
   * @return 登记后的文件视图
   */
  @Override
  @Transactional
  public StoredFileView writeText(String objectKey, String fileName, String text) {
    // null 按空串；字节内容即 UTF-8 原文，不做换行规范化
    byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
    FileView saved = saveLocalBytes(objectKey, fileName, "text/plain; charset=utf-8", bytes);
    return viewMapper.toStoredView(saved);
  }

  /**
   * 按 fileId 以 UTF-8 原样读出文本。
   *
   * @param fileId 文件主键
   * @return 文本；空文件返回空串
   */
  @Override
  public String readText(String fileId) {
    byte[] bytes = getBytes(fileId);
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * 删除物理文件并移除元数据行。
   *
   * @param fileId 文件主键
   * @throws BusinessException 文件不存在或存储类型未实现时
   */
  @Override
  @Transactional
  public void delete(String fileId) {
    StoredFileEntity file = require(fileId);
    // 先删物理对象，再删元数据，避免留下孤儿文件记录
    provider(file).delete(file.getObjectKey());
    storedFileMapper.deleteById(fileId);
  }

  /**
   * 按 id 加载元数据行，缺失则 404。
   *
   * @param fileId 文件主键
   * @return 非空实体
   * @throws BusinessException 记录不存在时
   */
  private StoredFileEntity require(String fileId) {
    StoredFileEntity file = storedFileMapper.selectById(fileId);
    if (file == null) {
      throw new BusinessException("file_not_found", "文件不存在", 404);
    }
    return file;
  }

  /**
   * 按实体上的 storage_type 解析对应 {@link StorageProvider}。
   *
   * @param file 已落库的文件实体
   * @return 对应存储实现
   * @throws BusinessException 类型未实现时
   */
  private StorageProvider provider(StoredFileEntity file) {
    return provider(StorageType.from(file.getStorageType()));
  }

  /**
   * 按枚举取存储实现。
   *
   * @param type 存储类型
   * @return 非空 Provider
   * @throws BusinessException 该类型未注册实现时返回 501
   */
  private StorageProvider provider(StorageType type) {
    StorageProvider provider = providers.get(type);
    if (provider == null) {
      throw new BusinessException(
          "storage_not_implemented",
          "Storage type not implemented: " + type,
          501);
    }
    return provider;
  }

  /**
   * 按存储类型与 object_key 查找已有元数据行。
   *
   * @param type      存储类型
   * @param objectKey 规范化后的对象键
   * @return 匹配行；不存在则为 null
   */
  private StoredFileEntity findByTypeAndKey(StorageType type, String objectKey) {
    return storedFileMapper.selectOne(new LambdaQueryWrapper<StoredFileEntity>()
        .eq(StoredFileEntity::getStorageType, type.name())
        .eq(StoredFileEntity::getObjectKey, objectKey)
        .last("LIMIT 1"));
  }

  /**
   * 将实体映射为对外文件视图。
   *
   * @param file 元数据实体
   * @return 文件视图
   */
  private FileView view(StoredFileEntity file) {
    return viewMapper.toView(file);
  }

  /**
   * 规范化 objectKey：统一斜杠并去掉前导 /；空白则抛错。
   *
   * @param objectKey 原始对象键
   * @return 规范化后的键
   * @throws BusinessException 为空时
   */
  private static String normalizeKey(String objectKey) {
    if (StrUtil.isBlank(objectKey)) {
      throw new BusinessException("file_key_required", "objectKey 不能为空", 400);
    }
    String key = objectKey.replace('\\', '/');
    while (key.startsWith("/")) {
      key = key.substring(1);
    }
    return key;
  }

  /**
   * 从 objectKey 末段推导文件名。
   *
   * @param key 规范化后的对象键
   * @return 最后一段路径名；无斜杠时返回整串
   */
  private static String fileNameOf(String key) {
    int slash = key.lastIndexOf('/');
    return slash >= 0 ? key.substring(slash + 1) : key;
  }

  /**
   * 计算内容 SHA-256 十六进制摘要；算法不可用时退回长度相关兜底哈希。
   *
   * @param bytes 文件内容
   * @return 小写十六进制摘要
   */
  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception ignored) {
      return HexFormat.of().formatHex(String.valueOf(bytes.length).getBytes(StandardCharsets.UTF_8));
    }
  }
}
