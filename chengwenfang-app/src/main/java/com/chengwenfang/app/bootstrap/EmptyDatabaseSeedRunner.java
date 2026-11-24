package com.chengwenfang.app.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 空库启动时自动导入内置提示词包、工作流与演示主题，不退出进程。
 * <p>已有数据不覆盖。补种或强制覆盖仍用 {@code scripts/bootstrap} 脚本。
 */
@Slf4j
@Component
@Order(15)
@Profile("!bootstrap-seed & !test")
@RequiredArgsConstructor
public class EmptyDatabaseSeedRunner implements ApplicationRunner {
  /** 种子导入。 */
  private final BuiltinCatalogSeedService seedService;

  /** 为 false 时跳过自动导入。 */
  @Value("${chengwenfang.bootstrap.auto-seed:true}")
  private boolean autoSeed;

  /**
   * 空库则导入；失败只记日志，避免热点演示主题缺 Key 时拖垮启动。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    if (!autoSeed) {
      log.info("已关闭空库自动种子（chengwenfang.bootstrap.auto-seed=false）");
      return;
    }
    try {
      if (!seedService.isEmptyLibrary()) {
        log.info("库内已有提示词包或工作流，跳过自动种子");
        return;
      }
      log.info("检测到空库，开始自动导入内置提示词包、三条主工作流与演示主题");
      seedService.importConfigured(false);
      log.info("空库自动种子完成");
    } catch (Exception exception) {
      log.error(
          "空库自动种子未完成，应用继续启动。可稍后执行补种脚本 scripts/bootstrap。原因：{}",
          exception.getMessage(),
          exception);
    }
  }
}
