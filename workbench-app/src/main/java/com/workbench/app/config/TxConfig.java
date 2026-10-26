package com.workbench.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 事务管理器等基础设施配置。 */
@Configuration
@EnableTransactionManagement
public class TxConfig {}
