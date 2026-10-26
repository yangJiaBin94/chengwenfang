package com.chengwenfang.ai.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态请求中的单张图片。
 * <p>优先使用 {@code filePath}；否则使用 {@code base64Data} + {@code mimeType}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionImagePart {
  /** 本地绝对路径（JPEG/PNG 等）。 */
  private String filePath;
  /** Base64 编码的图片数据（不含 data: 前缀）。 */
  private String base64Data;
  /** MIME，如 image/jpeg；默认 image/jpeg。 */
  private String mimeType;
}
