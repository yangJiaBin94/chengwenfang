package com.workbench.app.adapter.worker;

import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.skill.domain.runtime.GraphTextNodeHandler;
import com.workbench.skill.domain.runtime.WorkflowNodeHandlerRegistry;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.constant.WorkflowStructuralNodeKeys;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.spi.WorkflowNodeHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 正式路径图文本节点：优先经注册表路由到模式处理器；未命中则应用 Skill 绑定后调用通用文本 AI。
 */
@Slf4j
@Component
public class FormalGraphTextNodeHandler implements GraphTextNodeHandler {
  /** Skill 提示词组合。 */
  private final SkillFacade skills;
  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** 节点处理器注册表。 */
  private final WorkflowNodeHandlerRegistry nodeHandlerRegistry;

  /**
   * @param skills              Skill 门面
   * @param ai                  文本 AI
   * @param nodeHandlerRegistry 节点处理器注册表
   */
  public FormalGraphTextNodeHandler(
      SkillFacade skills,
      AiTextFacade ai,
      WorkflowNodeHandlerRegistry nodeHandlerRegistry) {
    this.skills = skills;
    this.ai = ai;
    this.nodeHandlerRegistry = nodeHandlerRegistry;
  }

  /**
   * 按注册表路由或走通用 LLM 回退，将输出写回 draft/wechat 等状态键。
   *
   * @param typeKey        节点类型键（如 draft_opening、wechat_conversion）
   * @param nodeKey        节点实例键
   * @param nodeName       展示名
   * @param skillBindings  节点 Skill 绑定原始 Map 列表
   * @param state          图运行状态（含 courseId/draft 等）
   * @param config         节点配置；当前未强依赖
   * @return 写回图状态的增量 Map
   * @throws IllegalStateException AI 失败或空输出
   */
  @Override
  public Map<String, Object> execute(
      String typeKey,
      String nodeKey,
      String nodeName,
      List<Map<String, Object>> skillBindings,
      Map<String, Object> state,
      Map<String, Object> config) {
    log.info("图文本节点开始 typeKey={} nodeKey={} name={}", typeKey, nodeKey, nodeName);
    // 注册表按 order 命中模式处理器（自定义 / 课程 / 雷达 / 热点）
    Optional<WorkflowNodeHandler> registered = nodeHandlerRegistry.findHandler(typeKey);
    if (registered.isPresent()) {
      return registered.get().execute(typeKey, nodeKey, nodeName, skillBindings, state, config);
    }
    // 未注册类型：通用 markdown 文本回退
    return executeGenericText(typeKey, nodeKey, nodeName, skillBindings, state);
  }

  /**
   * 通用 LLM 文本节点：组合 Skill 提示词并调用模型。
   *
   * @param typeKey       节点类型键
   * @param nodeKey       节点实例键
   * @param nodeName      展示名
   * @param skillBindings Skill 绑定
   * @param state         图状态
   * @return 状态增量
   */
  private Map<String, Object> executeGenericText(
      String typeKey,
      String nodeKey,
      String nodeName,
      List<Map<String, Object>> skillBindings,
      Map<String, Object> state) {
    String skillPrompt = skills.composePromptFromBindings(toPromptBindings(skillBindings));
    StringBuilder prompt = new StringBuilder();
    if (skillPrompt != null && !skillPrompt.isBlank()) {
      prompt.append(skillPrompt.trim()).append("\n\n");
    }
    prompt.append("节点：").append(nodeKey).append(" (").append(typeKey).append(") ").append(nodeName).append('\n');
    if (state != null && state.get("courseId") != null) {
      prompt.append("课程ID：").append(state.get("courseId")).append('\n');
    }
    if (state != null && state.get("draft") != null) {
      prompt.append("当前原稿：\n").append(state.get("draft")).append('\n');
    }
    prompt.append("请输出本节点 Markdown 内容。");

    String text;
    try {
      var result = ai.completeText(new TextCompleteRequest(prompt.toString(), typeKey));
      text = result.getText() == null ? "" : result.getText();
    } catch (RuntimeException exception) {
      throw new IllegalStateException("图文本节点 AI 调用失败：" + exception.getMessage(), exception);
    }
    if (text.isBlank()) {
      throw new IllegalStateException("图文本节点输出为空 typeKey=" + typeKey);
    }

    Map<String, Object> outputs = new LinkedHashMap<>();
    outputs.put(typeKey, text);
    // 草稿类节点：追加到累计 draft
    if (typeKey.startsWith("draft_") || WorkflowStructuralNodeKeys.LLM_TEXT.equals(typeKey)) {
      String draft = state == null ? "" : String.valueOf(state.getOrDefault("draft", ""));
      outputs.put("draft", (draft + "\n" + text).trim());
    }
    // 公众号节点：确保含封面 token
    if (WorkflowStructuralNodeKeys.WECHAT_CONVERSION.equals(typeKey)
        || typeKey.contains("wechat")
        || typeKey.contains("generate_wechat")) {
      String wechat = text;
      if (!wechat.contains("[[COURSE_COVER]]")) {
        wechat = wechat + "\n[[COURSE_COVER]]\n";
      }
      outputs.put("wechat", wechat);
    }
    return outputs;
  }

  /**
   * 将原始绑定 Map 转为 {@link SkillPromptBindingView} 列表。
   *
   * @param skillBindings 节点绑定；可为 null
   * @return 绑定列表
   */
  private static List<SkillPromptBindingView> toPromptBindings(List<Map<String, Object>> skillBindings) {
    if (skillBindings == null || skillBindings.isEmpty()) {
      return List.of();
    }
    List<SkillPromptBindingView> out = new ArrayList<>();
    for (Map<String, Object> binding : skillBindings) {
      if (binding == null) {
        continue;
      }
      Object priority = binding.get("priority");
      out.add(SkillPromptBindingView.builder()
          .skillId(binding.get("skillId") == null ? null : String.valueOf(binding.get("skillId")))
          .priority(priority instanceof Number number ? number.intValue() : 100)
          .enabled(binding.get("enabled") == null || Boolean.TRUE.equals(binding.get("enabled")))
          .build());
    }
    return out;
  }
}
