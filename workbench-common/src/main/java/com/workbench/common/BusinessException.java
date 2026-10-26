package com.workbench.common;

import lombok.Getter;

/**
 * 业务异常：携带稳定错误码与建议 HTTP 状态，供全局异常处理映射为 API 错误体。
 * 不用于表达基础设施故障（应抛未检查的技术异常）。
 */
@Getter
public class BusinessException extends RuntimeException {
  /** 机器可读错误码，例如 file_not_found、version_conflict。 */
  private final String code;
  /** 建议返回给客户端的 HTTP 状态码。 */
  private final int httpStatus;

  public BusinessException(String code, String message, int httpStatus) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
  }

}
