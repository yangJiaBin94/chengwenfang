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
 * 空库或演示主题基线未齐时自动导入内置提示词包、工作流与演示主题，不退出进程。
 * <p>已齐备则跳过。补种或强制覆盖仍用 {@code scripts/bootstrap} 脚本。
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
   * 需要时导入；失败只记日志，不拖垮启动。
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
      if (!seedService.needsAutoSeed()) {
        log.info("内置种子基线已齐，跳过自动种子");
        return;
      }
      log.info("检测到空库或演示主题基线未齐，开始导入内置提示词包、三条主工作流、演示主题和信源沉淀池");
      seedService.importConfigured(false);
      if (seedService.isSeedBaselineComplete()) {
        log.info("空库自动种子完成");
      } else {
        log.error(
            "自动种子后演示主题或工作流基线仍未齐，应用继续启动。可稍后执行补种脚本 scripts/bootstrap");
      }
    } catch (Exception exception) {
      log.error(
          "空库自动种子未完成，应用继续启动。可稍后执行补种脚本 scripts/bootstrap。原因：{}",
          exception.getMessage(),
          exception);
    }
  }
}
