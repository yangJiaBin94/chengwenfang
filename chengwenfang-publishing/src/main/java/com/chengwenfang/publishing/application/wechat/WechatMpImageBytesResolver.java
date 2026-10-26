package com.chengwenfang.publishing.application.wechat;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.file.facade.FileFacade;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将排版 HTML 中的图片 src 解析为本地或远程字节，供推送公众号草稿使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WechatMpImageBytesResolver {
  /** 工作台文件下载路径。 */
  private static final Pattern FILES_PATH = Pattern.compile("/api/files/([0-9a-fA-F\\-]{36})");
  /** 工作台旧版图片路径。 */
  private static final Pattern IMAGES_PATH = Pattern.compile("/api/images/([0-9a-fA-F\\-]{36})");
  /** 远程下载超时。 */
  private static final int REMOTE_TIMEOUT_MS = 30_000;

  /** 文件门面：按 fileId 读字节。 */
  private final FileFacade fileFacade;
  /** 内容门面：按 generated image id 读字节。 */
  private final ContentFacade contentFacade;

  /**
   * 按图片 src 加载字节；无法解析时返回 null。
   *
   * @param imageSrc 相对路径、绝对 URL 或 data URL
   * @return 图片字节；失败返回 null
   */
  public byte[] resolve(String imageSrc) {
    if (StrUtil.isBlank(imageSrc)) {
      return null;
    }
    String src = imageSrc.trim();
    // data:image/...;base64,...
    if (src.startsWith("data:image")) {
      return decodeDataUrl(src);
    }
    Matcher fileMatcher = FILES_PATH.matcher(src);
    if (fileMatcher.find()) {
      try {
        return fileFacade.getBytes(fileMatcher.group(1));
      } catch (RuntimeException exception) {
        log.warn("按 fileId 读取图片失败 id={}", fileMatcher.group(1));
        return null;
      }
    }
    Matcher imageMatcher = IMAGES_PATH.matcher(src);
    if (imageMatcher.find()) {
      try {
        return contentFacade.getImageBytes(imageMatcher.group(1));
      } catch (RuntimeException exception) {
        log.warn("按 imageId 读取图片失败 id={}", imageMatcher.group(1));
        return null;
      }
    }
    // http(s) 外链：尽力下载
    if (src.startsWith("http://") || src.startsWith("https://")) {
      return downloadRemote(src);
    }
    log.warn("无法识别的图片地址：{}", src);
    return null;
  }

  /**
   * 解码 data URL。
   *
   * @param dataUrl data:image/...;base64,...
   * @return 字节或 null
   */
  private byte[] decodeDataUrl(String dataUrl) {
    int comma = dataUrl.indexOf(',');
    if (comma < 0) {
      return null;
    }
    String meta = dataUrl.substring(0, comma);
    String payload = dataUrl.substring(comma + 1);
    if (!meta.contains(";base64")) {
      return null;
    }
    try {
      return java.util.Base64.getDecoder().decode(payload);
    } catch (IllegalArgumentException exception) {
      log.warn("data URL Base64 解码失败");
      return null;
    }
  }

  /**
   * 下载远程图片。
   *
   * @param url 绝对 URL
   * @return 字节或 null
   */
  private byte[] downloadRemote(String url) {
    try (HttpResponse response = HttpRequest.get(url).timeout(REMOTE_TIMEOUT_MS).execute()) {
      if (!response.isOk()) {
        log.warn("下载远程图片失败 status={} url={}", response.getStatus(), url);
        return null;
      }
      byte[] bytes = response.bodyBytes();
      return bytes == null || bytes.length == 0 ? null : bytes;
    } catch (RuntimeException exception) {
      log.warn("下载远程图片异常 url={}", url);
      return null;
    }
  }
}
