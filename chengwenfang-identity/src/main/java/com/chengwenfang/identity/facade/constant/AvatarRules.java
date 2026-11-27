package com.chengwenfang.identity.facade.constant;

import java.util.Locale;
import java.util.Set;

/**
 * 用户头像上传约束与图片魔数识别。
 */
public final class AvatarRules {
  /** 最大字节数（约 2MB）。 */
  public static final long MAX_BYTES = 2L * 1024 * 1024;

  /** 允许的 Content-Type（与魔数识别结果对齐）。 */
  public static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");

  private AvatarRules() {}

  /**
   * 按文件头魔数识别图片 MIME；无法识别返回 null。
   *
   * @param bytes 文件字节
   * @return image/jpeg、image/png、image/webp 或 null
   */
  public static String detectMime(byte[] bytes) {
    if (bytes == null || bytes.length < 2) {
      return null;
    }
    // JPEG：SOI 为 FF D8（后续标记不必是 FF）
    if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
      return "image/jpeg";
    }
    // PNG：89 50 4E 47 0D 0A 1A 0A
    if (bytes.length >= 8
        && (bytes[0] & 0xFF) == 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4E
        && bytes[3] == 0x47
        && bytes[4] == 0x0D
        && bytes[5] == 0x0A
        && bytes[6] == 0x1A
        && bytes[7] == 0x0A) {
      return "image/png";
    }
    // WEBP：RIFF....WEBP
    if (bytes.length >= 12
        && bytes[0] == 'R'
        && bytes[1] == 'I'
        && bytes[2] == 'F'
        && bytes[3] == 'F'
        && bytes[8] == 'W'
        && bytes[9] == 'E'
        && bytes[10] == 'B'
        && bytes[11] == 'P') {
      return "image/webp";
    }
    return null;
  }

  /**
   * 声明的 Content-Type 是否与魔数一致（声明为空时只认魔数）。
   *
   * @param claimedContentType 客户端声明，可空
   * @param detectedMime       {@link #detectMime(byte[])} 结果
   * @return 一致为 true
   */
  public static boolean matchesClaimed(String claimedContentType, String detectedMime) {
    if (detectedMime == null) {
      return false;
    }
    if (claimedContentType == null || claimedContentType.isBlank()) {
      return true;
    }
    String claimed = normalizeMime(claimedContentType);
    return detectedMime.equals(claimed);
  }

  /**
   * 归一化 MIME：去参数、小写，并将 {@code image/jpg} 映射为 {@code image/jpeg}。
   *
   * @param contentType 原始 Content-Type
   * @return 归一化结果；空输入返回空串
   */
  public static String normalizeMime(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return "";
    }
    String claimed = contentType.trim().toLowerCase(Locale.ROOT);
    int semicolon = claimed.indexOf(';');
    if (semicolon >= 0) {
      claimed = claimed.substring(0, semicolon).trim();
    }
    if ("image/jpg".equals(claimed)) {
      return "image/jpeg";
    }
    return claimed;
  }
}
