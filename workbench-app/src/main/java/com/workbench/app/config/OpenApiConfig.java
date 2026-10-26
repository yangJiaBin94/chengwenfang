package com.workbench.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI / Swagger 文档配置（springdoc）。 */
@Configuration
public class OpenApiConfig {

  /** 注册 API 文档元信息。 */
  @Bean
  public OpenAPI workbenchOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Workbench API")
            .description("内容创作工作台后端接口（OpenAPI 3 / Swagger UI）")
            .version("0.1.0")
            .contact(new Contact().name("Workbench")));
  }
}
