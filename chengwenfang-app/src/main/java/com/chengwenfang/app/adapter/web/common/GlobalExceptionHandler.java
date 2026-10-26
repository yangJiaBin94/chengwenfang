package com.chengwenfang.app.adapter.web.common;

import com.chengwenfang.common.ApiResult;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.TraceIdsUtil;
import java.io.IOException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理：统一返回 {@link ApiResult}，并在日志中带上 traceId。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 处理业务异常：按 {@link BusinessException#getHttpStatus()} 返回，body 为 ApiResult。
   *
   * @param ex 业务异常（含 code、message、httpStatus）
   * @return 失败 ApiResult；traceId 由 {@link TraceIdFilter} 写入响应头
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException ex) {
    log.warn(
        "业务异常 traceId={} code={} status={} msg={}",
        MDC.get(TraceIdsUtil.MDC_KEY),
        ex.getCode(),
        ex.getHttpStatus(),
        ex.getMessage());
    return ResponseEntity.status(ex.getHttpStatus()).body(ApiResult.fail(ex.getCode(), ex.getMessage()));
  }

  /**
   * 处理 {@code @Valid} 请求体校验失败。
   *
   * @param ex 方法参数校验异常
   * @return 400 + validation_error
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResult<Void>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    String message = formatFieldErrors(ex.getBindingResult().getFieldErrors());
    log.warn("入参校验失败 traceId={} msg={}", MDC.get(TraceIdsUtil.MDC_KEY), message);
    return ResponseEntity.status(400).body(ApiResult.fail("validation_error", message));
  }

  /**
   * 处理表单/查询绑定校验失败。
   *
   * @param ex 绑定异常
   * @return 400 + validation_error
   */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<ApiResult<Void>> handleBind(BindException ex) {
    String message = formatFieldErrors(ex.getBindingResult().getFieldErrors());
    log.warn("参数绑定校验失败 traceId={} msg={}", MDC.get(TraceIdsUtil.MDC_KEY), message);
    return ResponseEntity.status(400).body(ApiResult.fail("validation_error", message));
  }

  /**
   * 处理未匹配到 Controller 的请求，避免被当成 500。
   *
   * @param ex 资源不存在异常
   * @return 404 + not_found
   */
  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  public ResponseEntity<ApiResult<Void>> handleNotFound(Exception ex) {
    log.warn("接口不存在 traceId={} msg={}", MDC.get(TraceIdsUtil.MDC_KEY), ex.getMessage());
    return ResponseEntity.status(404).body(ApiResult.fail("not_found", "接口不存在"));
  }

  /**
   * 客户端已断开（SSE/长连接常见）：不再写 JSON，避免 text/event-stream 上二次失败。
   *
   * @param ex 异步响应不可用
   */
  @ExceptionHandler(AsyncRequestNotUsableException.class)
  public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
    log.debug("客户端已断开（异步响应不可用） traceId={} msg={}",
        MDC.get(TraceIdsUtil.MDC_KEY), ex.getMessage());
  }

  /**
   * IO 断开：浏览器关掉 EventSource 时 Tomcat 常抛 ClientAbortException / IOException。
   *
   * @param ex IO 异常
   * @return 非客户端断开时仍返回 500 ApiResult
   */
  @ExceptionHandler(IOException.class)
  public ResponseEntity<ApiResult<Void>> handleIo(IOException ex) {
    if (isClientDisconnected(ex)) {
      log.debug("客户端已断开 traceId={} msg={}", MDC.get(TraceIdsUtil.MDC_KEY), ex.getMessage());
      return null;
    }
    log.error("未处理 IO 异常 traceId={} msg={}", MDC.get(TraceIdsUtil.MDC_KEY), ex.getMessage(), ex);
    return ResponseEntity.status(500).body(ApiResult.fail("internal_error", "系统内部错误，请稍后重试"));
  }

  /**
   * 处理未分类异常：固定 500 + internal_error。
   *
   * @param ex 任意未捕获异常
   * @return 500 状态与通用失败 ApiResult；客户端断开时不写 body
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResult<Void>> handleOther(Exception ex) {
    if (isClientDisconnected(ex)) {
      log.debug("客户端已断开 traceId={} msg={}", MDC.get(TraceIdsUtil.MDC_KEY), ex.getMessage());
      return null;
    }
    log.error("未处理异常 traceId={} msg={}", MDC.get(TraceIdsUtil.MDC_KEY), ex.getMessage(), ex);
    return ResponseEntity.status(500).body(ApiResult.fail("internal_error", "系统内部错误，请稍后重试"));
  }

  /** 识别浏览器/代理主动掐断连接，避免当成 500。 */
  static boolean isClientDisconnected(Throwable error) {
    Throwable current = error;
    while (current != null) {
      String name = current.getClass().getName();
      if (name.contains("ClientAbortException")
          || name.contains("AsyncRequestNotUsableException")) {
        return true;
      }
      String message = current.getMessage();
      if (message != null
          && (message.contains("Broken pipe")
              || message.contains("Connection reset")
              || message.contains("中止了一个已建立的连接")
              || message.contains("你的主机中的软件中止"))) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static String formatFieldErrors(java.util.List<FieldError> errors) {
    if (errors == null || errors.isEmpty()) {
      return "请求参数不合法";
    }
    return errors.stream()
        .map(err -> err.getField() + ": " + (err.getDefaultMessage() == null ? "不合法" : err.getDefaultMessage()))
        .collect(Collectors.joining("；"));
  }
}
