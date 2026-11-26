package com.chengwenfang.identity.application;

import com.chengwenfang.identity.facade.AuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 身份模块 Spring 配置。 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class IdentityConfiguration {}
