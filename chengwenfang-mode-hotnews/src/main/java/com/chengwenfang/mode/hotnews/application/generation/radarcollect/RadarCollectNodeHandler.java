package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.List;
import java.util.Map;

/**
 * 雷达采集单节点策略。
 */
public interface RadarCollectNodeHandler {
  /**
   * 本处理器支持的节点类型。
   *
   * @return 节点枚举
   */
  HotNewsRadarCollectNodeType supports();

  /**
   * 执行节点。
   *
   * @param session        Job 会话
   * @param lightState     GraphRuntime 轻量状态（可读写）
   * @param skillBindings  节点 Skill 绑定
   * @param nodeKey        节点实例 key
   * @param nodeName       节点展示名
   */
  void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName);
}
