package com.chengwenfang.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应信封。
 *
 * @param <T> 业务数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一 API 响应信封")
public class ApiResult<T> {
  /** 业务码：成功为 {@link #CODE_OK}，失败为英文错误码。 */
  @Schema(description = "业务码，成功为 ok", example = "ok")
  private String code;
  /** 提示文案（中文）。 */
  @Schema(description = "提示文案（中文）", example = "成功")
  private String msg;
  /** 业务数据；失败时可为空。 */
  @Schema(description = "业务数据；失败时可为空")
  private T data;

  /** 成功码。 */
  public static final String CODE_OK = "ok";

  /** 成功且带数据。 */
  public static <T> ApiResult<T> ok(T data) {
    return ApiResult.<T>builder().code(CODE_OK).msg("成功").data(data).build();
  }

  /** 成功无数据。 */
  public static ApiResult<Void> ok() {
    return ApiResult.<Void>builder().code(CODE_OK).msg("成功").data(null).build();
  }

  /** 成功自定义文案。 */
  public static <T> ApiResult<T> ok(String msg, T data) {
    return ApiResult.<T>builder().code(CODE_OK).msg(msg).data(data).build();
  }

  /** 失败。 */
  public static <T> ApiResult<T> fail(String code, String msg) {
    return ApiResult.<T>builder().code(code).msg(msg).data(null).build();
  }
}
