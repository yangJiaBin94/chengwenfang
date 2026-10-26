package com.chengwenfang.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 上游提供方配置（表 {@code ai_provider_configs}），按 kind 区分 text/image。 */
@TableName("ai_provider_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderConfigEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 配置种类：{@code text} 或 {@code image}。 */
  @TableField("kind")
  private String kind;
  /** 配置显示名。 */
  @TableField("name")
  private String name;
  /** 模型名称。 */
  @TableField("model")
  private String model;
  /** 上游 Base URL。 */
  @TableField("base_url")
  private String baseUrl;
  /** API Key 密文（AES-GCM；勿写入日志或公开响应）。 */
  @TableField("api_key_ciphertext")
  private String apiKeyCiphertext;
  /**
   * 调用协议：如 {@code openai_chat}、{@code openai_responses}；影响文本补全路径。
   */
  @TableField("protocol")
  private String protocol;
  /** 请求超时（秒）。 */
  @TableField("timeout_seconds")
  private Integer timeoutSeconds;
  /** 最大重试次数。 */
  @TableField("max_retries")
  private Integer maxRetries;
  /** 采样温度。 */
  @TableField("temperature")
  private Double temperature;
  /** 最大输出 token 数。 */
  @TableField("max_output_tokens")
  private Integer maxOutputTokens;
  /** 图片生成质量。 */
  @TableField("image_quality")
  private String imageQuality;
  /** 是否启用。 */
  @TableField("enabled")
  private Boolean enabled;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;
  /** 最近更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
