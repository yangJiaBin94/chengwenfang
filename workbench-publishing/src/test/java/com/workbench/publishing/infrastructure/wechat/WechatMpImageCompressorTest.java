package com.workbench.publishing.infrastructure.wechat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.workbench.common.BusinessException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 公众号推送图片质量压缩单测：不缩放、不改原素材、超限压到 ≤1MB。
 */
class WechatMpImageCompressorTest {

  @TempDir
  Path tempDir;

  @Test
  void withinLimit_returnsOriginalBytesUnchanged() throws Exception {
    byte[] original = buildPng(64, 64, false);
    assertThat(original.length).isLessThan(WechatMpImageCompressor.MAX_IMAGE_BYTES);
    byte[] snapshot = Arrays.copyOf(original, original.length);

    WechatMpImageCompressor.Session session = new WechatMpImageCompressor.Session();
    try {
      WechatMpImageCompressor.PreparedImage prepared =
          session.ensureWithinLimit(original, "正文图片", "/api/images/small.png");
      assertThat(prepared.compressed()).isFalse();
      assertThat(prepared.bytes()).isSameAs(original);
      assertThat(original).containsExactly(snapshot);
      assertThat(prepared.tempFile()).isNull();
    } finally {
      session.cleanup();
    }
  }

  @Test
  void overLimit_compressesWithoutChangingDimensionsOrOriginal() throws Exception {
    // 平滑渐变 BMP：体积易超 1MB，但 JPEG 质量压缩可压进限制且不改分辨率
    byte[] original = buildSmoothBmp(1800, 1200);
    assertThat(original.length).isGreaterThan(WechatMpImageCompressor.MAX_IMAGE_BYTES);
    byte[] snapshot = Arrays.copyOf(original, original.length);
    Path fixture = tempDir.resolve("original-fixture.bmp");
    Files.write(fixture, original);
    long fixtureSizeBefore = Files.size(fixture);

    BufferedImage before = ImageIO.read(new ByteArrayInputStream(original));
    int width = before.getWidth();
    int height = before.getHeight();

    WechatMpImageCompressor.Session session = new WechatMpImageCompressor.Session();
    Path tempFile;
    try {
      WechatMpImageCompressor.PreparedImage prepared =
          session.ensureWithinLimit(original, "正文图片", fixture.toString());
      assertThat(prepared.compressed()).isTrue();
      assertThat(prepared.bytes().length).isLessThanOrEqualTo(WechatMpImageCompressor.MAX_IMAGE_BYTES);
      assertThat(prepared.suggestedFileName()).endsWith(".jpg");
      assertThat(prepared.tempFile()).isNotNull();
      assertThat(Files.exists(prepared.tempFile())).isTrue();
      tempFile = prepared.tempFile();

      BufferedImage after = ImageIO.read(new ByteArrayInputStream(prepared.bytes()));
      assertThat(after.getWidth()).isEqualTo(width);
      assertThat(after.getHeight()).isEqualTo(height);

      // 原 byte[] 与夹具文件均未被改写
      assertThat(original).containsExactly(snapshot);
      assertThat(Files.size(fixture)).isEqualTo(fixtureSizeBefore);
      assertThat(Files.readAllBytes(fixture)).containsExactly(snapshot);
    } finally {
      session.cleanup();
    }
    assertThat(Files.exists(tempFile)).isFalse();
  }

  @Test
  void emptySource_throwsBusinessException() {
    WechatMpImageCompressor.Session session = new WechatMpImageCompressor.Session();
    try {
      assertThatThrownBy(() -> session.ensureWithinLimit(new byte[0], "封面图片", "empty"))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("无法读取");
    } finally {
      session.cleanup();
    }
  }

  /**
   * 生成平滑渐变 BMP（近似未压缩），体积稳定超过 1MB，且 JPEG 可质量压进限制。
   *
   * @param width  宽
   * @param height 高
   * @return BMP 字节
   */
  private static byte[] buildSmoothBmp(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int r = (x * 255) / Math.max(1, width - 1);
        int g = (y * 255) / Math.max(1, height - 1);
        int b = ((x + y) * 255) / Math.max(1, width + height - 2);
        image.setRGB(x, y, (r << 16) | (g << 8) | b);
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "bmp", out)) {
      throw new IllegalStateException("ImageIO 无法写出 BMP");
    }
    return out.toByteArray();
  }

  private static byte[] buildPng(int width, int height, boolean fillNoise) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, width, height);
      if (fillNoise) {
        graphics.setColor(Color.DARK_GRAY);
        graphics.drawLine(0, 0, width, height);
      }
    } finally {
      graphics.dispose();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
