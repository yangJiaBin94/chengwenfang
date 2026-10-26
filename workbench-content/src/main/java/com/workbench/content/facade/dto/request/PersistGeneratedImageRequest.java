package com.workbench.content.facade.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 持久化生成配图请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersistGeneratedImageRequest {
  /** 配图 ID（可空，空则自动生成）。 */
  private String id;
  /** 文章 ID（遗留）。 */
  private String articleId;
  /** 统一文档 ID（公众号轨）；空则服务端解析。 */
  private String documentId;
  /** 类型。 */
  private String kind;
  /** 序号。 */
  private Integer sequenceNo;
  /** 提示词。 */
  private String prompt;
  /** Provider 配置 ID。 */
  private String providerConfigId;
  /** 模型名。 */
  private String model;
  /** 尺寸。 */
  private String size;
  /** 质量。 */
  private String quality;
  /** 输出格式。 */
  private String outputFormat;
  /** 已登记的文件 ID（与 filePath 二选一；落库只写 fileId）。 */
  private String fileId;
  /** 落盘后的相对/绝对路径，仅用于登记 stored_files，不写入 generated_images。 */
  private String filePath;
  /** 内容哈希。 */
  private String contentHash;
  /** 宽度。 */
  private Integer width;
  /** 高度。 */
  private Integer height;
  /** 任务 ID。 */
  private String jobId;
}
