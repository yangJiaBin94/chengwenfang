package com.workbench.job.application;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** Job 模块自动配置：扫描 {@code com.workbench.job} 组件（含 Worker）。 */
@AutoConfiguration
@ComponentScan("com.workbench.job")
public class JobAutoConfiguration {}
