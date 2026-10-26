package com.workbench.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot 入口：扫描各 workbench-* 模块并启动 HTTP 与后台 Job Worker。 */
@SpringBootApplication(scanBasePackages = "com.workbench")
@EnableScheduling
@MapperScan({
    "com.workbench.job.infrastructure.mapper",
    "com.workbench.ai.infrastructure.mapper",
    "com.workbench.skill.infrastructure.mapper",
    "com.workbench.content.infrastructure.mapper",
    "com.workbench.file.infrastructure.mapper",
    "com.workbench.mode.hotnews.infrastructure.mapper",
    "com.workbench.publishing.infrastructure.mapper",
    "com.workbench.notification.infrastructure.mapper",
    "com.workbench.quality.infrastructure.mapper",
    "com.workbench.sourcing.infrastructure.mapper",
    "com.workbench.mode.course.infrastructure.mapper"
})
public class WorkbenchApplication {
  public static void main(String[] args) {
    SpringApplication.run(WorkbenchApplication.class, args);
  }
}
