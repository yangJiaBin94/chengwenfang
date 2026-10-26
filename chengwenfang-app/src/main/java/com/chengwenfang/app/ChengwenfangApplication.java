package com.chengwenfang.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot 入口：扫描各 chengwenfang-* 模块并启动 HTTP 与后台 Job Worker。 */
@SpringBootApplication(scanBasePackages = "com.chengwenfang")
@EnableScheduling
@MapperScan({
    "com.chengwenfang.job.infrastructure.mapper",
    "com.chengwenfang.ai.infrastructure.mapper",
    "com.chengwenfang.skill.infrastructure.mapper",
    "com.chengwenfang.content.infrastructure.mapper",
    "com.chengwenfang.file.infrastructure.mapper",
    "com.chengwenfang.mode.hotnews.infrastructure.mapper",
    "com.chengwenfang.publishing.infrastructure.mapper",
    "com.chengwenfang.notification.infrastructure.mapper",
    "com.chengwenfang.quality.infrastructure.mapper",
    "com.chengwenfang.sourcing.infrastructure.mapper",
    "com.chengwenfang.mode.course.infrastructure.mapper"
})
public class ChengwenfangApplication {
  public static void main(String[] args) {
    SpringApplication.run(ChengwenfangApplication.class, args);
  }
}
