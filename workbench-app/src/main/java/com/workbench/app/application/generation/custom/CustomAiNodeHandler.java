package com.workbench.app.application.generation.custom;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.constant.ModelCallScenes;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.mode.hotnews.application.generation.hotnews.HotNewsGraphStateKeys;
import com.workbench.mode.hotnews.facade.HotNewsFacade;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsBriefView;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.constant.CustomAiNodeActions;
import com.workbench.skill.facade.constant.WorkflowNodeHandlerOrders;
import com.workbench.skill.facade.constant.WorkflowStructuralNodeKeys;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.spi.WorkflowNodeHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自定义 AI 节点：按节点提示词调用模型，仅允许白名单结构化动作写回原稿/公众号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAiNodeHandler implements WorkflowNodeHandler {
  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** Skill 组合。 */
  private final SkillFacade skills;
  /** 动作 JSON 解析。 */
  private final CustomAiActionPlanParser actionPlanParser;
  /** 热点门面（按需加载简报正文）。 */
  private final HotNewsFacade hotNews;

  /**
   * @param typeKey 节点类型键
   * @return 是否为自定义 AI 节点
   */
  @Override
  public boolean supports(String typeKey) {
    return WorkflowStructuralNodeKeys.isCustomAi(typeKey);
  }

  /**
   * @return 自定义 AI 节点路由优先级
   */
  @Override
  public int order() {
    return WorkflowNodeHandlerOrders.CUSTOM_AI;
  }

  /**
   * 执行自定义节点。
   *
   * @param typeKey       节点类型键（本处理器固定为 custom_ai）
   * @param nodeKey       节点实例键
   * @param nodeName      展示名
   * @param skillBindings 节点 Skill 绑定
   * @param state         图状态
   * @param config        节点配置（须含 prompt）
   * @return 写回图状态的增量
   */
  @Override
  public Map<String, Object> execute(
      String typeKey,
      String nodeKey,
      String nodeName,
      List<Map<String, Object>> skillBindings,
      Map<String, Object> state,
      Map<String, Object> config) {
    if (!supports(typeKey)) {
      throw new IllegalArgumentException("非自定义 AI 节点：" + typeKey);
    }
    String prompt = config == null ? "" : StrUtil.trim(StrUtil.nullToEmpty(String.valueOf(config.getOrDefault("prompt", ""))));
    if (StrUtil.isBlank(prompt)) {
      throw new IllegalStateException("自定义节点提示词为空 nodeKey=" + nodeKey);
    }
    List<SkillPromptBindingView> bindings = toPromptBindings(skillBindings);
    Set<String> boundSkillIds = bindings.stream()
        .map(SkillPromptBindingView::getSkillId)
        .filter(StrUtil::isNotBlank)
        .collect(Collectors.toSet());
    String skillPrompt = skills.composePromptFromBindings(bindings);
    ContextBundle context = buildContext(state);

    String systemPrompt = """
        你是文章生产工作流中的自定义节点执行器。
        你只能输出一个 JSON 对象，格式严格为：
        {"actions":[{"op":"...","content":"...","skillId":"..."}]}
        op 仅允许：read_skill、read_skeleton、read_brief、read_references、read_draft、read_wechat、write_draft、write_wechat。
        禁止输出其它字段、禁止解释、禁止 Markdown（除非包在 JSON 字符串 content 内）。
        只能操作文章流程：读取已配置 Skill、骨架、简报、资料库、原稿、公众号；修改原稿或公众号。
        若任务超出上述范围，返回 {"actions":[]} 且不要伪造写操作。
        """.stripIndent();

    StringBuilder userPrompt = new StringBuilder();
    userPrompt.append("节点：").append(nodeName).append(" (").append(nodeKey).append(")\n\n");
    userPrompt.append("节点提示词：\n").append(prompt).append("\n\n");
    if (StrUtil.isNotBlank(skillPrompt)) {
      userPrompt.append("已配置 Skill：\n").append(skillPrompt.trim()).append("\n\n");
    }
    userPrompt.append("当前上下文可用性：\n");
    userPrompt.append("- skillIds=").append(boundSkillIds).append('\n');
    userPrompt.append("- skeleton=").append(context.skeletonAvailable ? "可用" : "不可用").append('\n');
    userPrompt.append("- brief=").append(context.briefAvailable ? "可用" : "不可用").append('\n');
    userPrompt.append("- references=").append(context.referencesAvailable ? "可用" : "不可用").append('\n');
    userPrompt.append("- draft=").append(context.draftAvailable ? "可用" : "不可用").append('\n');
    userPrompt.append("- wechat=").append(context.wechatAvailable ? "可用" : "不可用").append('\n');
    if (context.skeletonAvailable) {
      userPrompt.append("\n【骨架】\n").append(context.skeletonText).append('\n');
    }
    if (context.briefAvailable) {
      userPrompt.append("\n【简报】\n").append(context.briefText).append('\n');
    }
    if (context.referencesAvailable) {
      userPrompt.append("\n【资料库线索】\n").append(context.referencesText).append('\n');
    }
    if (context.draftAvailable) {
      userPrompt.append("\n【当前原稿】\n").append(context.draftText).append('\n');
    }
    if (context.wechatAvailable) {
      userPrompt.append("\n【当前公众号文章】\n").append(context.wechatText).append('\n');
    }
    userPrompt.append("\n请输出 actions JSON。");

    log.info("自定义节点调用 AI nodeKey={}", nodeKey);
    var result = ai.completeText(TextCompleteRequest.builder()
        .scene(ModelCallScenes.ADHOC)
        .systemPrompt(systemPrompt)
        .prompt(userPrompt.toString())
        .timeoutSeconds(resolveTimeoutSeconds(config))
        .build());
    String raw = result == null ? "" : StrUtil.nullToEmpty(result.getText());
    List<CustomAiParsedAction> actions = actionPlanParser.parse(raw);
    return applyActions(actions, boundSkillIds, context);
  }

  /**
   * 从节点 config 读取超时秒数。
   *
   * @param config 节点配置
   * @return 超时秒；缺省 180
   */
  private static int resolveTimeoutSeconds(Map<String, Object> config) {
    if (config == null || config.get("timeoutSeconds") == null) {
      return 180;
    }
    try {
      int value = Integer.parseInt(String.valueOf(config.get("timeoutSeconds")));
      return Math.max(10, Math.min(900, value));
    } catch (Exception ignored) {
      return 180;
    }
  }

  /**
   * 校验并应用白名单动作。
   *
   * @param actions       解析后的动作
   * @param boundSkillIds 节点已绑定 Skill Id
   * @param context       上下文可用性
   * @return 状态增量
   */
  Map<String, Object> applyActions(
      List<CustomAiParsedAction> actions,
      Set<String> boundSkillIds,
      ContextBundle context) {
    Map<String, Object> delta = new LinkedHashMap<>();
    for (CustomAiParsedAction action : actions) {
      String op = action.getOp();
      if (CustomAiNodeActions.READ_SKILL.equals(op)) {
        if (StrUtil.isBlank(action.getSkillId()) || !boundSkillIds.contains(action.getSkillId())) {
          throw new IllegalStateException("read_skill 只能读取本节点已配置的 Skill，skillId=" + action.getSkillId());
        }
        continue;
      }
      if (CustomAiNodeActions.READ_SKELETON.equals(op)) {
        if (!context.skeletonAvailable) {
          throw new IllegalStateException("read_skeleton 失败：当前无骨架");
        }
        continue;
      }
      if (CustomAiNodeActions.READ_BRIEF.equals(op)) {
        if (!context.briefAvailable) {
          throw new IllegalStateException("read_brief 失败：当前无简报");
        }
        continue;
      }
      if (CustomAiNodeActions.READ_REFERENCES.equals(op)) {
        if (!context.referencesAvailable) {
          throw new IllegalStateException("read_references 失败：当前无资料库线索");
        }
        continue;
      }
      if (CustomAiNodeActions.READ_DRAFT.equals(op)) {
        if (!context.draftAvailable) {
          throw new IllegalStateException("read_draft 失败：当前尚未生成原稿");
        }
        continue;
      }
      if (CustomAiNodeActions.READ_WECHAT.equals(op)) {
        if (!context.wechatAvailable) {
          throw new IllegalStateException("read_wechat 失败：当前尚未生成公众号文章");
        }
        continue;
      }
      if (CustomAiNodeActions.WRITE_DRAFT.equals(op)) {
        if (!context.draftAvailable) {
          throw new IllegalStateException("write_draft 失败：当前阶段尚未生成原稿，不可修改");
        }
        if (StrUtil.isBlank(action.getContent())) {
          throw new IllegalStateException("write_draft 失败：content 为空");
        }
        delta.put(HotNewsGraphStateKeys.DRAFT, action.getContent().trim());
        continue;
      }
      if (CustomAiNodeActions.WRITE_WECHAT.equals(op)) {
        if (!context.wechatAvailable) {
          throw new IllegalStateException("write_wechat 失败：当前阶段尚未生成公众号文章，不可修改");
        }
        if (StrUtil.isBlank(action.getContent())) {
          throw new IllegalStateException("write_wechat 失败：content 为空");
        }
        delta.put(HotNewsGraphStateKeys.WECHAT, action.getContent().trim());
        continue;
      }
      throw new IllegalStateException("未处理的动作 op=" + op);
    }
    return delta;
  }

  /**
   * 从状态与热点门面组装可读上下文。
   *
   * @param state 图状态
   * @return 上下文包
   */
  private ContextBundle buildContext(Map<String, Object> state) {
    Map<String, Object> safe = state == null ? Map.of() : state;
    String draft = StrUtil.trim(StrUtil.nullToEmpty(stringVal(safe.get(HotNewsGraphStateKeys.DRAFT))));
    String wechat = StrUtil.trim(StrUtil.nullToEmpty(stringVal(safe.get(HotNewsGraphStateKeys.WECHAT))));
    String skeletonJson = StrUtil.trim(StrUtil.nullToEmpty(stringVal(safe.get(HotNewsGraphStateKeys.SKELETON_JSON))));
    if (StrUtil.isBlank(skeletonJson) && safe.get(HotNewsGraphStateKeys.SKELETON) != null) {
      skeletonJson = JSONUtil.toJsonStr(safe.get(HotNewsGraphStateKeys.SKELETON));
    }
    String referencesJson = StrUtil.trim(StrUtil.nullToEmpty(stringVal(safe.get(HotNewsGraphStateKeys.REFERENCES_JSON))));
    if (StrUtil.isBlank(referencesJson)) {
      referencesJson = StrUtil.trim(StrUtil.nullToEmpty(stringVal(safe.get(HotNewsGraphStateKeys.FACTS_JSON))));
    }
    String briefText = resolveBriefText(safe);
    return new ContextBundle(
        StrUtil.isNotBlank(skeletonJson),
        skeletonJson,
        StrUtil.isNotBlank(briefText),
        briefText,
        StrUtil.isNotBlank(referencesJson) && !"[]".equals(referencesJson),
        referencesJson,
        StrUtil.isNotBlank(draft),
        draft,
        StrUtil.isNotBlank(wechat),
        wechat);
  }

  /**
   * 按 briefId/topicId 解析简报文本；失败则空串。
   *
   * @param state 图状态
   * @return 简报摘要文本
   */
  private String resolveBriefText(Map<String, Object> state) {
    String briefId = StrUtil.trim(StrUtil.nullToEmpty(stringVal(state.get(HotNewsGraphStateKeys.BRIEF_ID))));
    String topicId = StrUtil.trim(StrUtil.nullToEmpty(stringVal(state.get(HotNewsGraphStateKeys.TOPIC_ID))));
    String topicSummary = StrUtil.trim(StrUtil.nullToEmpty(stringVal(state.get(HotNewsGraphStateKeys.TOPIC_SUMMARY))));
    if (StrUtil.isBlank(briefId) && StrUtil.isBlank(topicSummary)) {
      return "";
    }
    if (StrUtil.isNotBlank(topicId) && StrUtil.isNotBlank(briefId)) {
      try {
        List<HotNewsBriefView> briefs = hotNews.listBriefs(topicId);
        if (CollUtil.isNotEmpty(briefs)) {
          for (HotNewsBriefView brief : briefs) {
            if (briefId.equals(brief.getId())) {
              return formatBrief(brief);
            }
          }
        }
      } catch (Exception ignored) {
        // 简报拉取失败时退回选题摘要
      }
    }
    return topicSummary;
  }

  /**
   * 格式化简报视图为提示词段落。
   *
   * @param brief 简报
   * @return 文本
   */
  private String formatBrief(HotNewsBriefView brief) {
    StringBuilder text = new StringBuilder();
    text.append("标题：").append(StrUtil.nullToEmpty(brief.getTitle())).append('\n');
    text.append("受众：").append(StrUtil.nullToEmpty(brief.getTargetAudience())).append('\n');
    text.append("角度：").append(StrUtil.nullToEmpty(brief.getAngle())).append('\n');
    if (brief.getSummary() != null) {
      text.append("摘要：").append(JSONUtil.toJsonStr(brief.getSummary())).append('\n');
    }
    return text.toString().trim();
  }

  /**
   * 绑定 Map 转视图。
   *
   * @param skillBindings 原始绑定
   * @return 视图列表
   */
  private List<SkillPromptBindingView> toPromptBindings(List<Map<String, Object>> skillBindings) {
    List<SkillPromptBindingView> out = new ArrayList<>();
    if (skillBindings == null) {
      return out;
    }
    for (Map<String, Object> binding : skillBindings) {
      if (binding == null) {
        continue;
      }
      out.add(SkillPromptBindingView.builder()
          .skillId(stringVal(binding.get("skillId")))
          .priority(binding.get("priority") instanceof Number number ? number.intValue() : 10)
          .enabled(!(binding.get("enabled") instanceof Boolean enabled) || enabled)
          .build());
    }
    return out;
  }

  /**
   * 对象转字符串。
   *
   * @param value 任意值
   * @return 字符串；null 为空串
   */
  private static String stringVal(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /**
   * 上下文可用性与正文包。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  static final class ContextBundle {
    /** 是否有骨架。 */
    private final boolean skeletonAvailable;
    /** 骨架文本。 */
    private final String skeletonText;
    /** 是否有简报。 */
    private final boolean briefAvailable;
    /** 简报文本。 */
    private final String briefText;
    /** 是否有资料。 */
    private final boolean referencesAvailable;
    /** 资料文本。 */
    private final String referencesText;
    /** 是否有原稿。 */
    private final boolean draftAvailable;
    /** 原稿。 */
    private final String draftText;
    /** 是否有公众号。 */
    private final boolean wechatAvailable;
    /** 公众号。 */
    private final String wechatText;
  }

}
