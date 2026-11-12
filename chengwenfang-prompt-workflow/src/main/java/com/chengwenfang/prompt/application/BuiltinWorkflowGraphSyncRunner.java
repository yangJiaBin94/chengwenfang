package com.chengwenfang.prompt.application;

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
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时按规范补齐内置工作流节点集（缺 builtin typeKey 则覆盖发布；可多不可少）。
 * <p>避免仅改代码种子、未 Force 灌库时生产图缺新节点（如成文佐证截屏）。
 */
@Slf4j
@Component
@Order(70)
@Profile("!bootstrap-seed & !sync-builtin-workflows")
@RequiredArgsConstructor
public class BuiltinWorkflowGraphSyncRunner implements ApplicationRunner {
  /** 工作流门面。 */
  private final WorkflowFacade workflows;
  /** 提示词包门面（解析节点绑定 id）。 */
  private final PromptPackFacade promptPacks;

  /**
   * 同步课程 / 热点成文 / 雷达采集内置图。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    Map<String, String> idsByName = promptPackIdsByName();
    try {
      workflows.ensureCourseContentBuiltin(idsByName);
      workflows.ensureHotNewsArticleBuiltin(idsByName);
      workflows.ensureHotNewsRadarCollectBuiltin(idsByName);
      log.info("内置工作流节点集已按规范核对（缺节点则已覆盖发布）");
    } catch (Exception exception) {
      log.error("内置工作流节点同步失败：{}", exception.getMessage(), exception);
    }
  }

  /**
   * 按提示词包名称建立 id 索引（同名取先出现者）。
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
