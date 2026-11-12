package com.chengwenfang.app.bootstrap;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.prompt.facade.PromptPackFacade;
import com.chengwenfang.prompt.facade.WorkflowFacade;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 一次性同步内置工作流节点集后退出（profile={@code sync-builtin-workflows}）。
 * <p>用于已有库缺新节点时补图，不覆盖提示词包正文。
 */
@Slf4j
@Component
@Order(20)
@Profile("sync-builtin-workflows")
@RequiredArgsConstructor
public class BuiltinWorkflowSyncOnceRunner implements ApplicationRunner {
  /** 工作流门面。 */
  private final WorkflowFacade workflows;
  /** 提示词包门面。 */
  private final PromptPackFacade promptPacks;
  /** Spring 上下文。 */
  private final ConfigurableApplicationContext context;

  /**
   * 同步三套内置图并退出。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    int code = 0;
    try {
      Map<String, String> idsByName = promptPackIdsByName();
      workflows.ensureCourseContentBuiltin(idsByName);
      workflows.ensureHotNewsArticleBuiltin(idsByName);
      workflows.ensureHotNewsRadarCollectBuiltin(idsByName);
      log.info("内置工作流节点集同步完成");
    } catch (Exception exception) {
      log.error("内置工作流同步失败：{}", exception.getMessage(), exception);
      code = 1;
    }
    int exitCode = code;
    int springCode = SpringApplication.exit(context, () -> exitCode);
    System.exit(springCode);
  }

  /**
   * 按提示词包名称建立 id 索引。
   *
   * @return 名 → id
   */
  private Map<String, String> promptPackIdsByName() {
    List<PromptPackView> all = promptPacks.listPromptPacks();
    Map<String, String> ids = new LinkedHashMap<>();
    if (all == null) {
      return ids;
    }
    for (PromptPackView promptPack : all) {
      if (promptPack == null || StrUtil.isBlank(promptPack.getName()) || StrUtil.isBlank(promptPack.getId())) {
        continue;
      }
      ids.putIfAbsent(promptPack.getName().trim(), promptPack.getId());
    }
    return ids;
  }
}
