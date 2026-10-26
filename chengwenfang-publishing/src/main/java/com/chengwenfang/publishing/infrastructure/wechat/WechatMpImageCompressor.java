package com.chengwenfang.publishing.infrastructure.wechat;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * 公众号推送用图片压缩：超过 1MB 时仅做 JPEG 质量压缩（不缩放），不改原素材。
 * <p>手法对齐业务侧 HuaWeiObsUtil（{@code ColorConvertOp} + {@code ImageWriteParam}），
 * 并按微信正文图硬限循环降质量直至 ≤1MB。
 */
@Slf4j
public final class WechatMpImageCompressor {

  /** 微信正文/封面上传大小上限（字节）。 */
  public static final int MAX_IMAGE_BYTES = 1024 * 1024;

  /** 软目标，略低于硬限留余量。 */
  private static final int SOFT_TARGET_BYTES = 980 * 1024;

  /** 起始 JPEG 质量。 */
  private static final float QUALITY_START = 0.85f;

  /** 质量下限。 */
  private static final float QUALITY_FLOOR = 0.40f;

  /** 质量步进。 */
  private static final float QUALITY_STEP = 0.05f;

  private WechatMpImageCompressor() {}

  /**
   * 一轮推送内的压缩会话：登记临时文件，结束后统一删除。
   */
  public static final class Session {
    /** 本轮产生的临时压缩文件。 */
    private final List<Path> tempFiles = new ArrayList<>();

    /**
     * 确保图片字节 ≤1MB：未超限原样返回；超限则 JPEG 质量压缩（不改宽高）。
     *
     * @param source      原图像字节（只读，不回写）
     * @param label       中文用途标签（正文图片/封面图片）
     * @param originalSrc 原 src，仅用于日志与建议文件名
     * @return 可上传字节与是否压缩、建议文件名
     */
    public PreparedImage ensureWithinLimit(byte[] source, String label, String originalSrc) {
      if (source == null || source.length == 0) {
        throw new BusinessException(
            ErrorCodes.WECHAT_MP_DRAFT_FAILED, "无法读取" + label + "：" + originalSrc, 400);
      }
      if (source.length <= MAX_IMAGE_BYTES) {
        return new PreparedImage(source, false, suggestOriginalName(originalSrc), null);
      }
      try {
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(source));
        if (decoded == null) {
          throw new BusinessException(
              ErrorCodes.WECHAT_MP_DRAFT_FAILED,
              label + "无法解码，无法压缩至微信 1MB 限制：" + originalSrc,
              400);
        }
        // 对齐参考实现：统一转 RGB，避免透明通道/特殊色彩导致 JPEG 写入异常
        ColorConvertOp convertOp = new ColorConvertOp(null);
        BufferedImage rgb =
            convertOp.filter(
                decoded,
                new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB));

        byte[] best = null;
        float usedQuality = QUALITY_START;
        for (float quality = QUALITY_START; quality >= QUALITY_FLOOR - 1e-4f; quality -= QUALITY_STEP) {
          byte[] compressed = encodeJpeg(rgb, clampQuality(quality));
          usedQuality = clampQuality(quality);
          best = compressed;
          if (compressed.length <= SOFT_TARGET_BYTES) {
            break;
          }
          if (compressed.length <= MAX_IMAGE_BYTES) {
            break;
          }
        }
        if (best == null || best.length > MAX_IMAGE_BYTES) {
          int afterKb = best == null ? 0 : best.length / 1024;
          throw new BusinessException(
              ErrorCodes.WECHAT_MP_DRAFT_FAILED,
              label
                  + "超过微信 1MB 限制且质量压缩后仍超限（原约 "
                  + (source.length / 1024)
                  + "KB，压缩后约 "
                  + afterKb
                  + "KB，尺寸 "
                  + rgb.getWidth()
                  + "x"
                  + rgb.getHeight()
                  + "）："
                  + originalSrc,
              400);
        }
        Path temp = writeTempJpeg(best);
        tempFiles.add(temp);
        log.info(
            "公众号推送图片已质量压缩 label={} src={} {}KB→{}KB quality={} size={}x{} temp={}",
            label,
            originalSrc,
            source.length / 1024,
            best.length / 1024,
            String.format("%.2f", usedQuality),
            rgb.getWidth(),
            rgb.getHeight(),
            temp.getFileName());
        return new PreparedImage(best, true, "image.jpg", temp);
      } catch (BusinessException ex) {
        throw ex;
      } catch (IOException ex) {
        throw new BusinessException(
            ErrorCodes.WECHAT_MP_DRAFT_FAILED,
            label + "压缩失败：" + originalSrc + "（" + ex.getMessage() + "）",
            500);
      }
    }

    /**
     * 删除本轮登记的临时压缩文件（忽略单个删除失败）。
     */
    public void cleanup() {
      for (Path temp : tempFiles) {
        try {
          FileUtil.del(temp.toFile());
        } catch (Exception ignored) {
          log.warn("删除公众号推送临时压缩图失败 path={}", temp);
        }
      }
      tempFiles.clear();
    }
  }

  /**
   * 压缩准备结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class PreparedImage {
    /** 待上传字节。 */
    private final byte[] bytes;
    /** 是否经过压缩。 */
    private final boolean compressed;
    /** 上传表单文件名建议。 */
    private final String suggestedFileName;
    /** 临时文件路径；未落盘则为 null。 */
    private final Path tempFile;
  }

  private static float clampQuality(float quality) {
    if (quality < QUALITY_FLOOR) {
      return QUALITY_FLOOR;
    }
    if (quality > 1.0f) {
      return 1.0f;
    }
    return quality;
  }

  /**
   * 按指定质量将 RGB 图编码为 JPEG 字节（不改宽高）。
   *
   * @param image   RGB 图像
   * @param quality 0～1
   * @return JPEG 字节
   */
  static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
    if (!writers.hasNext()) {
      throw new IOException("当前环境无 JPEG ImageWriter");
    }
    ImageWriter writer = writers.next();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
      writer.setOutput(output);
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (param.canWriteCompressed()) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
      }
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return buffer.toByteArray();
  }

  private static Path writeTempJpeg(byte[] bytes) throws IOException {
    Path dir = Path.of(System.getProperty("java.io.tmpdir"), "chengwenfang-wechat-mp");
    Files.createDirectories(dir);
    Path temp = Files.createTempFile(dir, "push-", ".jpg");
    try (OutputStream out = Files.newOutputStream(temp)) {
      out.write(bytes);
    }
    return temp;
  }

  private static String suggestOriginalName(String src) {
    if (StrUtil.isBlank(src)) {
      return "image.png";
    }
    String name = src;
    int slash = Math.max(src.lastIndexOf('/'), src.lastIndexOf('\\'));
    if (slash >= 0 && slash + 1 < src.length()) {
      name = src.substring(slash + 1);
    }
    int query = name.indexOf('?');
    if (query > 0) {
      name = name.substring(0, query);
    }
    return StrUtil.blankToDefault(name, "image.png");
  }
}
