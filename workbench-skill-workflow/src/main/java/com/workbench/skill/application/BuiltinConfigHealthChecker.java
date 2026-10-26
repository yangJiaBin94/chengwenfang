package com.workbench.skill.application;

import com.workbench.skill.facade.BuiltinConfigFacade;
import com.workbench.skill.facade.builtin.BuiltinConfigInspector;
import com.workbench.skill.facade.dto.view.BuiltinInspectView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时只读检查内置 Skill 与工作流；缺项打日志，不写库、不阻断启动。
 */
@Slf4j
@Component
@Order(80)
@Profile("!bootstrap-seed")
@RequiredArgsConstructor
public class BuiltinConfigHealthChecker implements ApplicationRunner {
  /** 内置配置体检门面。 */
  private final BuiltinConfigFacade builtinConfigFacade;

  /**
   * 读取库内配置并输出体检结果。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    BuiltinInspectView view = builtinConfigFacade.inspect();
    if (view.isReady()) {
      log.info("基础配置体检通过 skills={} workflows=3", view.getExpectedSkillCount());
      return;
    }
    if (view.getIssues() != null) {
      for (String issue : view.getIssues()) {
        log.error("基础配置缺失：{}", issue);
      }
    }
    log.error(
        "基础配置未就绪，生成能力可能不可用。请执行：{}",
        BuiltinConfigInspector.BOOTSTRAP_HINT);
  }
}
