package com.chengwenfang.job.application;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** Job 模块自动配置：扫描 {@code com.chengwenfang.job} 组件（含 Worker）。 */
@AutoConfiguration
@ComponentScan("com.chengwenfang.job")
public class JobAutoConfiguration {}
