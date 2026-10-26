package com.workbench.infra.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 基础设施模块自动配置：扫描 infra 组件，并注册各模块 {@code com.workbench.**.mapper}。
 */
@AutoConfiguration
@ComponentScan("com.workbench.infra")
@MapperScan("com.workbench.**.infrastructure.mapper")
public class InfraAutoConfiguration {}
