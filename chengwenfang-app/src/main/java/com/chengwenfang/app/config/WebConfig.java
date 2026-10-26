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
        .allowedOrigins("http://127.0.0.1:5173", "http://localhost:5173")
        .allowedMethods("*")
        .allowedHeaders("*")
        .exposedHeaders("Last-Event-ID");
  }
}
