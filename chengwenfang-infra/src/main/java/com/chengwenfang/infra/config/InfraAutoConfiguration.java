package com.chengwenfang.infra.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 基础设施模块自动配置：扫描 infra 组件，并注册各模块 {@code com.chengwenfang.**.mapper}。
 */
@AutoConfiguration
@ComponentScan("com.chengwenfang.infra")
@MapperScan("com.chengwenfang.**.infrastructure.mapper")
public class InfraAutoConfiguration {}
