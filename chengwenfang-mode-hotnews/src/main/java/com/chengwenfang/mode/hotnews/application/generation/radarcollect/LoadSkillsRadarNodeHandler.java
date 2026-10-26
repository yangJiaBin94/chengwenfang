package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 加载并校验采集 Skill 快照。 */
@Component
@RequiredArgsConstructor
public class LoadSkillsRadarNodeHandler implements RadarCollectNodeHandler {
  /** AI / Skill 支持。 */
  private final RadarCollectAiSupport aiSupport;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.LOAD_SKILLS;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    if (CollUtil.isEmpty(skillBindings)) {
      throw new BusinessException("radar_skill_required", "雷达采集 Skill 绑定为空", 422);
    }
    String prompt = aiSupport.composeAll(skillBindings);
    if (StrUtil.isBlank(prompt)) {
      throw new BusinessException("radar_skill_required", "雷达采集 Skill 内容为空", 422);
    }
    int enabled = 0;
    for (SkillPromptBindingView binding : skillBindings) {
      if (binding != null && !Boolean.FALSE.equals(binding.getEnabled())) {
        enabled++;
      }
    }
    String summary = "已加载采集 Skill " + enabled + " 个，摘要长度 " + prompt.length();
    session.setSkillSnapshotSummary(summary);
    lightState.put(RadarCollectGraphStateKeys.SKILL_SNAPSHOT_SUMMARY, summary);
  }
}
