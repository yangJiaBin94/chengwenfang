package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.prompt.facade.dto.view.PromptPackPromptBindingView;
import com.chengwenfang.prompt.facade.enums.HotNewsRadarCollectNodeType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 加载并校验采集 提示词包 快照。 */
@Component
@RequiredArgsConstructor
public class LoadPromptPacksRadarNodeHandler implements RadarCollectNodeHandler {
  /** AI / 提示词包 支持。 */
  private final RadarCollectAiSupport aiSupport;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.LOAD_PROMPT_PACKS;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<PromptPackPromptBindingView> promptPackBindings,
      String nodeKey,
      String nodeName) {
    if (CollUtil.isEmpty(promptPackBindings)) {
      throw new BusinessException("radar_prompt_pack_required", "雷达采集 提示词包绑定为空", 422);
    }
    String prompt = aiSupport.composeAll(promptPackBindings);
    if (StrUtil.isBlank(prompt)) {
      throw new BusinessException("radar_prompt_pack_required", "雷达采集 提示词包 内容为空", 422);
    }
    int enabled = 0;
    for (PromptPackPromptBindingView binding : promptPackBindings) {
      if (binding != null && !Boolean.FALSE.equals(binding.getEnabled())) {
        enabled++;
      }
    }
    String summary = "已加载采集提示词包 " + enabled + " 个，摘要长度 " + prompt.length();
    session.setPromptPackSnapshotSummary(summary);
    lightState.put(RadarCollectGraphStateKeys.PROMPT_PACK_SNAPSHOT_SUMMARY, summary);
  }
}
