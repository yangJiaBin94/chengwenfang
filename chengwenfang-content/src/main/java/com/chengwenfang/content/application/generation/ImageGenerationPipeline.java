package com.chengwenfang.content.application.generation;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.AiImageFacade;
import com.chengwenfang.ai.facade.dto.request.ImageGenerateRequest;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.request.PersistGeneratedImageRequest;
import com.chengwenfang.content.facade.dto.view.GeneratedImageView;
import com.chengwenfang.infra.support.StorageService;
import com.chengwenfang.job.domain.entity.JobEntity;
import com.chengwenfang.job.facade.JobExecutionContext;
import com.chengwenfang.job.facade.dto.job.ImageGenerationJobInput;
import com.chengwenfang.job.facade.dto.job.ImageGenerationJobOutput;
import com.chengwenfang.job.facade.dto.job.JobPayloads;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 文章配图生成管道。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenerationPipeline {
  /** 内容门面。 */
  private final ContentFacade content;
  /** 图像 AI。 */
  private final AiImageFacade images;
  /** 文件存储。 */
  private final StorageService storage;
  /**
   * 执行文章配图生成管道。
   *
   * @param job     任务实体（input：prompt/articleId/kind/size 等）
   * @param context 执行上下文
   * @throws Exception 生图失败或结果为空
   */
  public void run(JobEntity job, JobExecutionContext context) throws Exception {
    log.info("执行管道 imageGeneration jobId={} entityId={}", job.getId(), job.getEntityId());
    ImageGenerationJobInput input = ImageGenerationJobInput.from(JobPayloads.parseMap(job.getInputJson()));
    String prompt = String.valueOf(input.getOrDefault("prompt", "chengwenfang illustration"));
    String articleId = StrUtil.firstNonBlank(stringOrNull(input.get("articleId")), stringOrNull(job.getEntityId()));
    String documentId = stringOrNull(input.get("documentId"));
    String kind = String.valueOf(input.getOrDefault("kind", "inline"));
    String size = String.valueOf(input.getOrDefault("size", "1024x1024"));
    int sequenceNo = input.get("sequenceNo") instanceof Number number
          ? number.intValue()
        : Integer.parseInt(String.valueOf(input.getOrDefault("sequenceNo", 0)));

    step(job, context, "image_plan", 20, "规划配图生成");
    step(job, context, "image_generate", 60, "生成配图");

    byte[] pngBytes;
    String model;
    try {
      var result = images.generate(new ImageGenerateRequest(prompt, size, false));
      model = result.getModel();
      if (result.getB64Json() == null || result.getB64Json().isBlank()) {
        throw new IllegalStateException("配图生成结果为空");
      }
      pngBytes = Base64.getDecoder().decode(result.getB64Json());
    } catch (IllegalStateException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("配图生成失败：" + exception.getMessage(), exception);
    }
    if (pngBytes.length == 0) {
      throw new IllegalStateException("配图生成结果为空");
    }

    String imageId = IdsUtil.newId();
    Path dir = storage.generatedImagesDir();
    Path file = dir.resolve(imageId + ".png");
    Files.write(file, pngBytes);
    String contentHash = sha256Bytes(pngBytes);
    String relativePath = "generated-images/" + imageId + ".png";

    PersistGeneratedImageRequest fields = PersistGeneratedImageRequest.builder()
        .id(imageId)
        .articleId(articleId)
        .documentId(documentId)
        .kind(kind)
        .sequenceNo(sequenceNo)
        .prompt(prompt)
        .model(model)
        .size(size)
        .outputFormat("png")
        .filePath(relativePath)
        .contentHash(contentHash)
        .width(parseSize(size, true))
        .height(parseSize(size, false))
        .jobId(job.getId())
        .build();
    GeneratedImageView saved = content.persistGeneratedImage(fields);

    step(job, context, "done", 100, "配图任务完成");
    context.completeWith(ImageGenerationJobOutput.from(saved, articleId, documentId));
  }

  /**
   * 上报一步进度（已取消则跳过）；顺带续租。
   *
   * @param job      任务
   * @param context  上下文
   * @param name     步骤名
   * @param progress 进度 0–100
   * @param message  人类可读说明
   */
  private void step(JobEntity job, JobExecutionContext context, String name, int progress, String message) {
    if (context.isCancelled(job.getId())) {
      return;
    }
    context.renewLease(job.getId());
    context.progress(job.getId(), name, progress, message);
  }

  /**
   * 将任意值转为非空字符串；blank/null 字符串返回 null。
   *
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String stringOrNull(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value);
    return text.isBlank() || "null".equals(text) ? null : text;
  }

  /**
   * 从 {@code WxH} 解析宽或高。
   *
   * @param size  尺寸串
   * @param width true 取宽
   * @return 像素或 null
   */
  private static Integer parseSize(String size, boolean width) {
    if (size == null || !size.contains("x")) {
      return null;
    }
    String[] parts = size.toLowerCase().split("x");
    if (parts.length != 2) {
      return null;
    }
    try {
      return Integer.parseInt(parts[width ? 0 : 1].trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * 计算字节 SHA-256 hex。
   *
   * @param bytes 二进制；null 按空数组
   * @return hex 摘要
   */
  private static String sha256Bytes(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes == null ? new byte[0] : bytes));
    } catch (Exception ignored) {
      return "";
    }
  }
}
