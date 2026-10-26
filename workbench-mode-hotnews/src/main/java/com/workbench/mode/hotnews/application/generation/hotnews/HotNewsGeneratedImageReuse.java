package com.workbench.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.workbench.content.facade.dto.view.GeneratedImageView;
import java.util.List;

/**
 * 热点配图失败重试时的已成功图片挑选。
 * <p>仅复用「同一 Job」内同 kind/序号的成功图；新开「生成热点文章」是新 Job，不得拿历史 Job 的图。
 */
public final class HotNewsGeneratedImageReuse {
  private HotNewsGeneratedImageReuse() {}

  /**
   * 从已落库列表中挑选当前 Job 的候选（不校验文件是否存在）。
   * <p>调用方传入的列表应按「同序号下越新越靠前」排序（与 {@code listImages} 一致）。
   *
   * @param images     文章已有配图
   * @param jobId      当前任务 ID；空白则不复用
   * @param kind       封面或正文插图
   * @param sequenceNo 序号（封面为 0）
   * @return 候选视图；无则 null
   */
  public static GeneratedImageView pickCandidate(
      List<GeneratedImageView> images,
      String jobId,
      HotNewsImageKind kind,
      int sequenceNo) {
    if (images == null || images.isEmpty() || kind == null || StrUtil.isBlank(jobId)) {
      return null;
    }
    String kindValue = kind.getValue();
    for (GeneratedImageView image : images) {
      if (image == null || StrUtil.isBlank(image.getId())) {
        continue;
      }
      if (!kindValue.equals(image.getKind())) {
        continue;
      }
      int imageSequence = image.getSequenceNo() == null ? Integer.MIN_VALUE : image.getSequenceNo();
      if (imageSequence != sequenceNo) {
        continue;
      }
      // 列表已按创建时间降序，同 Job 首次命中即为该槽位最新成功图
      if (jobId.equals(image.getJobId())) {
        return image;
      }
    }
    return null;
  }
}
