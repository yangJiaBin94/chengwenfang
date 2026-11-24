package com.chengwenfang.app.bootstrap;

import com.chengwenfang.prompt.facade.BuiltinConfigFacade;
import com.chengwenfang.prompt.facade.dto.view.BuiltinInspectView;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组装系统就绪视图：内置种子体检加上 Qdrant、密钥与热点加装。
 * <p>只依赖门面与 SPI，不穿透各域 application。
 */
@Component
@RequiredArgsConstructor
public class BuiltinReadyQuery {
  /** 内置配置体检门面。 */
  private final BuiltinConfigFacade builtinConfigFacade;
  /** 运行时依赖探测。 */
  private final SystemReadinessProbe readinessProbe;

  /**
   * 体检并映射为 app 层视图。
   *
   * @return 类型化就绪视图
   */
  public BuiltinReadyView inspect() {
    BuiltinInspectView source = builtinConfigFacade.inspect();
    List<SystemReadinessItem> checks = readinessProbe.collect(source);
    List<String> issues = new ArrayList<>();
    if (source.getIssues() != null) {
      issues.addAll(source.getIssues());
    }
    for (SystemReadinessItem check : checks) {
      if (check.isRequired()
          && check.getStatus() != SystemReadinessStatus.PASS
          && !SystemReadinessKeys.isBuiltinSeed(check.getKey())) {
        issues.add(check.getLabel() + "：" + check.getDetail());
      }
    }
    boolean requiredPass = checks.stream()
        .filter(SystemReadinessItem::isRequired)
        .allMatch(check -> check.getStatus() == SystemReadinessStatus.PASS);
    // 门面体检还覆盖「数量够但指令空」等 checks 没单独编码的问题
    boolean ready = source.isReady() && requiredPass;
    return BuiltinReadyView.builder()
        .ready(ready)
        .issues(issues)
        .bootstrapHint(source.getBootstrapHint())
        .promptPackCount(source.getPromptPackCount())
        .expectedPromptPackCount(source.getExpectedPromptPackCount())
        .workflowReady(source.isWorkflowReady())
        .checks(checks)
        .build();
  }
}
