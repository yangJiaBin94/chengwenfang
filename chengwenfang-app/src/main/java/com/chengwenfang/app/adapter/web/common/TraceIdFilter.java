package com.chengwenfang.app.adapter.web.common;

import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.common.TraceIdsUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个 HTTP 请求建立/传递 traceId，写入 MDC 与响应头，便于整条链路日志检索。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TraceIdFilter extends OncePerRequestFilter {
  /**
   * 解析或生成 traceId，放入 MDC 与响应头后继续过滤链，结束时清理 MDC。
   *
   * @param request     当前 HTTP 请求
   * @param response    当前 HTTP 响应
   * @param filterChain 下游过滤器链
   * @throws ServletException 下游 Servlet 异常
   * @throws IOException      下游 IO 异常
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 优先沿用客户端传入的 traceId，否则新生成
    String incoming = request.getHeader(TraceIdsUtil.HEADER);
    String traceId = StringUtils.hasText(incoming) ? incoming.trim() : IdsUtil.newId();
    MDC.put(TraceIdsUtil.MDC_KEY, traceId);
    response.setHeader(TraceIdsUtil.HEADER, traceId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      // 防止线程复用时 traceId 泄漏到下一个请求
      MDC.remove(TraceIdsUtil.MDC_KEY);
    }
  }
}
