package com.chengwenfang.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC 配置（跨域、消息转换等）。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
  /** 允许本地 Vite 前端访问 /api，并暴露 SSE 的 Last-Event-ID。 */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        // 5173 默认开发端口；5174 用于与另一实例并存（-BesidePrivate）
        .allowedOrigins(
            "http://127.0.0.1:5173",
            "http://localhost:5173",
            "http://127.0.0.1:5174",
            "http://localhost:5174")
        .allowedMethods("*")
        .allowedHeaders("*")
        .exposedHeaders("Last-Event-ID");
  }
}
