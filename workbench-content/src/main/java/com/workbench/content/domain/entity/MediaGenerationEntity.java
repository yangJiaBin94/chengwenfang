package com.workbench.content.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 生成媒体的参数。 */
@TableName("media_generations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaGenerationEntity {
  /** 媒体资产 ID。 */
  @TableId("asset_id")
  private String assetId;
  /** 生图提示词。 */
  @TableField("prompt")
  private String prompt;
  /** AI 配置 ID。 */
  @TableField("provider_config_id")
  private String providerConfigId;
  /** 模型名。 */
  @TableField("model")
  private String model;
  /** 尺寸。 */
  @TableField("size")
  private String size;
  /** 质量档。 */
  @TableField("quality")
  private String quality;
  /** 输出格式。 */
  @TableField("output_format")
  private String outputFormat;
  /** 生成任务 ID。 */
  @TableField("job_id")
  private String jobId;
}
