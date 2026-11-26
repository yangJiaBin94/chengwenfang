package com.chengwenfang.app.bootstrap;

import com.chengwenfang.identity.facade.OwnerContext;
import com.chengwenfang.identity.facade.constant.SeedUserIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 仅在 {@code bootstrap-seed} 配置下导入种子后退出。
 * <p>供补种 / 强制覆盖脚本使用；日常启动走 {@link EmptyDatabaseSeedRunner}。
 */
@Slf4j
@Component
@Order(20)
@Profile("bootstrap-seed")
@RequiredArgsConstructor
public class BuiltinSeedImporter implements ApplicationRunner {
  /** 种子导入。 */
  private final BuiltinCatalogSeedService seedService;
  /** Spring 上下文（导入后退出）。 */
  private final ConfigurableApplicationContext context;

  /**
   * 导入种子并结束进程。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    int[] code = {0};
    OwnerContext.runAs(SeedUserIds.ADMIN, () -> {
      try {
        // 脚本可通过 chengwenfang.bootstrap.force 覆盖已有内置行
        seedService.importConfigured(seedService.isForce());
      } catch (Exception exception) {
        log.error("基础配置导入失败：{}", exception.getMessage(), exception);
        code[0] = 1;
      }
    });
    int springCode = SpringApplication.exit(context, () -> code[0]);
    System.exit(springCode);
  }
}
