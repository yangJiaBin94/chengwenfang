package com.chengwenfang.knowledge.application.ingest;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.knowledge.facade.constant.KnowledgeIngestConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;

/**
 * 根据文件名解析上传介质与扩展名校验。
 */
public final class KnowledgeUploadMediaResolver {

  private KnowledgeUploadMediaResolver() {}

  /**
   * 校验扩展名与大小，并返回介质种类。
   *
   * @param fileName 原始文件名
   * @param sizeBytes 字节数
   * @return 介质种类
   */
  public static KnowledgeMediaKind resolveMediaKind(String fileName, long sizeBytes) {
    if (sizeBytes <= 0) {
      throw new BusinessException(ErrorCodes.KNOWLEDGE_UPLOAD_REJECTED, "上传文件为空", 400);
    }
    if (sizeBytes > KnowledgeIngestConstants.MAX_UPLOAD_BYTES) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_UPLOAD_REJECTED,
          "单文件不能超过 20MB",
          400);
    }
    String extension = StrUtil.nullToEmpty(FileNameUtil.extName(fileName)).toLowerCase();
    if (StrUtil.isBlank(extension) || !isAllowed(extension)) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_UPLOAD_REJECTED,
          "不支持的文件类型：" + StrUtil.blankToDefault(extension, "(无扩展名)"),
          400);
    }
    return switch (extension) {
      case "md", "markdown" -> KnowledgeMediaKind.ARTICLE_MARKDOWN;
      case "txt" -> KnowledgeMediaKind.PLAIN_TEXT;
      default -> KnowledgeMediaKind.DOCUMENT;
    };
  }

  /**
   * 取小写扩展名。
   *
   * @param fileName 文件名
   * @return 扩展名
   */
  public static String extensionOf(String fileName) {
    return StrUtil.nullToEmpty(FileNameUtil.extName(fileName)).toLowerCase();
  }

  private static boolean isAllowed(String extension) {
    for (String allowed : KnowledgeIngestConstants.ALLOWED_UPLOAD_EXTENSIONS) {
      if (allowed.equals(extension)) {
        return true;
      }
    }
    return false;
  }
}
