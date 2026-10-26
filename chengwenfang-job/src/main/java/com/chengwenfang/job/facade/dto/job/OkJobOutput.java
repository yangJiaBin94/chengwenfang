package com.chengwenfang.job.facade.dto.job;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用成功出参：仅含 {@code ok} 字段（Worker 默认完成输出）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OkJobOutput {
  /** 是否成功。 */
  private Boolean ok;
}
