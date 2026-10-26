package com.workbench.mode.hotnews.application.generation;

import cn.hutool.core.util.StrUtil;
import com.workbench.mode.hotnews.application.generation.radarcollect.RadarCollectGraphStateKeys;
import com.workbench.mode.hotnews.application.generation.radarcollect.RadarCollectNodeHandler;
import com.workbench.mode.hotnews.application.generation.radarcollect.RadarCollectSessionStore;
import com.workbench.skill.facade.constant.WorkflowNodeHandlerOrders;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.HotNewsRadarCollectNodeType;
import com.workbench.skill.facade.spi.WorkflowNodeHandler;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 雷达采集工作流节点调度器。
 */
@Slf4j
@Component
public class HotNewsRadarCollectGraphExecutor implements WorkflowNodeHandler {
  private final Map<HotNewsRadarCollectNodeType, RadarCollectNodeHandler> handlersByType;
  private final RadarCollectSessionStore sessionStore;

  /**
   * @param handlers     全部雷达节点处理器
   * @param sessionStore Job 会话
   */
  public HotNewsRadarCollectGraphExecutor(
      List<RadarCollectNodeHandler> handlers, RadarCollectSessionStore sessionStore) {
    Map<HotNewsRadarCollectNodeType, RadarCollectNodeHandler> indexed =
        new EnumMap<>(HotNewsRadarCollectNodeType.class);
    for (RadarCollectNodeHandler handler : handlers) {
      indexed.put(handler.supports(), handler);
    }
    this.handlersByType = Map.copyOf(indexed);
    this.sessionStore = sessionStore;
  }

  /**
   * @param typeKey 节点类型键
   * @return 是否为雷达采集节点（radar_ 前缀）
   */
  @Override
  public boolean supports(String typeKey) {
    return HotNewsRadarCollectNodeType.isRadarTypeKey(typeKey);
  }

  /**
   * @return 雷达采集节点路由优先级
   */
  @Override
  public int order() {
    return WorkflowNodeHandlerOrders.RADAR_COLLECT;
  }

  /**
   * 执行单个雷达节点。
   *
   * @param typeKey       节点类型
   * @param nodeKey       实例 key
   * @param nodeName      展示名
   * @param skillBindings 原始绑定
   * @param state         图状态
   * @param config        配置
   * @return 状态增量
   */
  @Override
  public Map<String, Object> execute(
      String typeKey,
      String nodeKey,
      String nodeName,
      List<Map<String, Object>> skillBindings,
      Map<String, Object> state,
      Map<String, Object> config) {
    HotNewsRadarCollectNodeType nodeType = HotNewsRadarCollectNodeType.from(typeKey)
        .orElseThrow(() -> new IllegalArgumentException("未知雷达采集节点：" + typeKey));
    RadarCollectNodeHandler handler = handlersByType.get(nodeType);
    if (handler == null) {
      throw new IllegalStateException("雷达采集节点无处理器：" + typeKey);
    }
    Map<String, Object> lightState = state == null ? new LinkedHashMap<>() : new LinkedHashMap<>(state);
    String jobId = asString(lightState.get(RadarCollectGraphStateKeys.JOB_ID));
    if (StrUtil.isBlank(jobId)) {
      throw new IllegalStateException("雷达采集缺少 jobId");
    }
    RadarCollectSessionStore.Session session = sessionStore.getOrCreate(jobId);
    log.info("雷达图节点分发 typeKey={} nodeKey={} jobId={}", typeKey, nodeKey, jobId);
    handler.execute(session, lightState, toPromptBindings(skillBindings), nodeKey, nodeName);
    lightState.put(RadarCollectGraphStateKeys.RADAR_STEP, nodeType.getValue());
    if (nodeType == HotNewsRadarCollectNodeType.FINALIZE) {
      sessionStore.clear(jobId);
    }
    // 租约回调不可 JSON 序列化进 node_run output
    Map<String, Object> output = new LinkedHashMap<>(lightState);
    output.remove(RadarCollectGraphStateKeys.LEASE_RENEWAL);
    return output;
  }

  /**
   * 将节点 Skill 绑定原始 Map 转为提示词绑定视图。
   *
   * @param skillBindings 原始绑定列表；可空
   * @return 绑定视图列表
   */
  private static List<SkillPromptBindingView> toPromptBindings(List<Map<String, Object>> skillBindings) {
    if (skillBindings == null || skillBindings.isEmpty()) {
      return List.of();
    }
    List<SkillPromptBindingView> result = new ArrayList<>();
    for (Map<String, Object> binding : skillBindings) {
      if (binding == null) {
        continue;
      }
      Object priority = binding.get("priority");
      result.add(SkillPromptBindingView.builder()
          .skillId(binding.get("skillId") == null ? null : String.valueOf(binding.get("skillId")))
          .priority(priority instanceof Number number ? number.intValue() : 100)
          .enabled(binding.get("enabled") == null || Boolean.TRUE.equals(binding.get("enabled")))
          .build());
    }
    return result;
  }

  /**
   * 将任意值转为字符串；null 保持 null。
   *
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
