package com.chengwenfang.app.bootstrap;

import com.chengwenfang.prompt.facade.BuiltinConfigFacade;
import com.chengwenfang.prompt.facade.dto.view.BuiltinInspectView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 只读查询内置提示词包 / 工作流是否就绪，供 HTTP 系统面板使用。
 * <p>仅依赖 {@link BuiltinConfigFacade}，不穿透 prompt.application。
 */
@Component
@RequiredArgsConstructor
public class BuiltinReadyQuery {
  /** 内置配置体检门面。 */
  private final BuiltinConfigFacade builtinConfigFacade;

  /**
   * 委托门面体检并映射为 app 层视图。
   *
   * @return 类型化就绪视图
   */
  public BuiltinReadyView inspect() {
    BuiltinInspectView source = builtinConfigFacade.inspect();
    return BuiltinReadyView.builder()
        .ready(source.isReady())
        .issues(source.getIssues() == null ? List.of() : source.getIssues())
        .bootstrapHint(source.getBootstrapHint())
        .promptPackCount(source.getPromptPackCount())
        .expectedPromptPackCount(source.getExpectedPromptPackCount())
        .workflowReady(source.isWorkflowReady())
        .build();
  }
}
