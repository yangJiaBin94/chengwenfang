package com.chengwenfang.app.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.identity.facade.constant.AuthPermissions;
import com.chengwenfang.identity.facade.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截：API 始终强制登录与写权限。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

  /**
   * 注册登录与写权限校验拦截器。
   *
   * @param registry 拦截器注册表
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new SaInterceptor(
                handle -> {
                  // EventSource 无法带 Header，允许 ?satoken= 注入当前请求令牌
                  bindQueryTokenIfPresent();
                  SaRouter.match("/api/**")
                      .notMatch(
                          "/api/auth/login",
                          "/api/auth/register",
                          "/api/auth/status",
                          "/api/system/builtin-ready",
                          "/api/system/health",
                          "/actuator/**")
                      .check(router -> StpUtil.checkLogin());
                  // 写操作：设置类需 settings:write；其余需 work:write（ADMIN 角色一并放行）
                  SaRouter.match(SaHttpMethod.POST, SaHttpMethod.PUT, SaHttpMethod.PATCH, SaHttpMethod.DELETE)
                      .match("/api/**")
                      .notMatch(
                          "/api/auth/**",
                          "/api/system/**",
                          "/actuator/**")
                      .check(
                          router -> {
                            if (StpUtil.hasRole(UserRole.ADMIN.getValue())) {
                              return;
                            }
                            String path = currentPath();
                            // 设置与发布凭证仅管理员；其余写操作需 work:write
                            if (StrUtil.startWith(path, "/api/settings")
                                || StrUtil.startWith(path, "/api/publishing")) {
                              StpUtil.checkRole(UserRole.ADMIN.getValue());
                            } else {
                              StpUtil.checkPermission(AuthPermissions.WORK_WRITE);
                            }
                          });
                }))
        .addPathPatterns("/**");
  }

  /**
   * 将查询参数 satoken 写入当前请求的 Token 上下文。
   */
  private static void bindQueryTokenIfPresent() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return;
    }
    HttpServletRequest request = attributes.getRequest();
    String queryToken = request.getParameter("satoken");
    if (StrUtil.isNotBlank(queryToken)) {
      StpUtil.setTokenValue(queryToken);
    }
  }

  /**
   * @return 当前请求 path，缺省空串
   */
  private static String currentPath() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return "";
    }
    return StrUtil.nullToEmpty(attributes.getRequest().getRequestURI());
  }
}
