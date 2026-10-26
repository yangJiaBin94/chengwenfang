package com.workbench.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单次模型调用审计记录（表 {@code model_calls}）。 */
@TableName("model_calls")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelCallEntity {
  /** 主键 ID。 */
  @TableId("id")
  private String id;
  /** 所属任务（Worker 上下文写入；同步调用为空）。 */
  @TableField("job_id")
  private String jobId;
  /** 所属作品（后续任务补写）。 */
  @TableField("work_id")
  private String workId;
  /** 调用场景，如 connection_test、image、业务 scene。 */
  @TableField("scene")
  private String scene;
  /** 模型名称。 */
  @TableField("model")
  private String model;
  /** 调用协议。 */
  @TableField("protocol")
  private String protocol;
  /** 结果状态：succeeded / failed。 */
  @TableField("status")
  private String status;
  /** 调用耗时（毫秒）。 */
  @TableField("duration_ms")
  private Long durationMs;
  /** 输入字符数近似（非 token）。 */
  @TableField("input_chars")
  private Integer inputChars;
  /** 输出字符数近似（非 token）。 */
  @TableField("output_chars")
  private Integer outputChars;
  /** 错误码。 */
  @TableField("error_code")
  private String errorCode;
  /** AI 供应商配置 ID。 */
  @TableField("provider_config_id")
  private String providerConfigId;
  /** 创建时间。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

}
