package com.chengwenfang.skill.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.skill.domain.entity.WorkflowDefinitionEntity;
import com.chengwenfang.skill.facade.constant.WorkflowStructuralNodeKeys;
import com.chengwenfang.skill.facade.constant.WorkflowValidationMapKeys;
import com.chengwenfang.skill.facade.enums.WorkflowDefinitionStatus;
import com.chengwenfang.skill.facade.enums.WorkflowFeatureBinding;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 工作流图定义校验与可运行性断言。
 */
@Component
public class WorkflowGraphValidator {

  /**
   * 断言定义可被业务启动（已挂发布版本且 status 为 active）。
   *
   * @param def 工作流定义
   */
  public void assertDefinitionRunnable(WorkflowDefinitionEntity def) {
    assertDefinitionRunnable(def, WorkflowFeatureBinding.fromScene(def == null ? null : def.getScene()));
  }

  /**
   * 断言定义可被业务启动；失败时用功能绑定文案提示。
   *
   * @param def     工作流定义
   * @param binding 功能绑定；可为 null
   */
  public void assertDefinitionRunnable(WorkflowDefinitionEntity def, WorkflowFeatureBinding binding) {
    if (def == null) {
      throw new BusinessException(ErrorCodes.WORKFLOW_NOT_FOUND, "工作流不存在", 404);
    }
    if (StrUtil.isBlank(def.getPublishedVersionId())
        || !WorkflowDefinitionStatus.from(def.getStatus()).isRunnable()) {
      String message = binding != null
          ? binding.unavailableMessage()
          : "「" + StrUtil.blankToDefault(def.getName(), "工作流")
              + "」已停用或未发布，请先在工作流中心启用。";
      throw new BusinessException(ErrorCodes.WORKFLOW_UNAVAILABLE, message, 409);
    }
  }

  /**
   * 校验图定义：边的 source/target 必须落在已声明的 node key 集合内。
   *
   * @param definition 图定义 Map（含 nodes / edges）
   * @return 校验结果 Map：valid、errors、warnings、checkedAt、definitionHash
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> validateDefinition(Map<String, Object> definition) {
    List<Map<String, Object>> errors = new ArrayList<>();
    Object nodesRaw = definition.get("nodes");
    Object edgesRaw = definition.get("edges");
    Set<String> nodeKeys = new HashSet<>();
    // 先收集合法节点 key，并校验自定义节点提示词
    if (nodesRaw instanceof List<?> nodes) {
      for (Object nodeObj : nodes) {
        if (nodeObj instanceof Map<?, ?> nodeMap) {
          Map<String, Object> node = (Map<String, Object>) nodeMap;
          Object key = node.get("key");
          if (key != null && !String.valueOf(key).isBlank()) {
            nodeKeys.add(String.valueOf(key));
          }
          String typeKey = node.get("typeKey") == null ? "" : String.valueOf(node.get("typeKey"));
          if (WorkflowStructuralNodeKeys.isCustomAi(typeKey)) {
            String prompt = extractNodePrompt(node);
            if (StrUtil.isBlank(prompt)) {
              String nodeKey = key == null ? typeKey : String.valueOf(key);
              errors.add(error(
                  "custom_ai_prompt_required",
                  "自定义节点必须填写提示词：" + nodeKey,
                  nodeKey));
            }
          }
        }
      }
    }
    // 再检查边两端是否都在节点集中
    if (edgesRaw instanceof List<?> edges) {
      for (Object edgeObj : edges) {
        if (!(edgeObj instanceof Map<?, ?> edgeMap)) {
          continue;
        }
        Map<String, Object> edge = (Map<String, Object>) edgeMap;
        String source = edge.get("source") == null ? null : String.valueOf(edge.get("source"));
        String target = edge.get("target") == null ? null : String.valueOf(edge.get("target"));
        if (target != null && !target.isBlank() && !nodeKeys.contains(target)) {
          errors.add(error("missing_target", "Edge target does not exist: " + target, target));
        }
        if (source != null && !source.isBlank() && !nodeKeys.contains(source)) {
          errors.add(error("missing_source", "Edge source does not exist: " + source, source));
        }
      }
    }
    Map<String, Object> validation = new LinkedHashMap<>();
    validation.put(WorkflowValidationMapKeys.VALID, errors.isEmpty());
    validation.put(WorkflowValidationMapKeys.ERRORS, errors);
    validation.put(WorkflowValidationMapKeys.WARNINGS, List.of());
    validation.put(WorkflowValidationMapKeys.CHECKED_AT, LocalDateTime.now().toString());
    validation.put(WorkflowValidationMapKeys.DEFINITION_HASH, "");
    return validation;
  }

  /**
   * 从节点 Map 的 config.prompt 读取自定义节点提示词。
   *
   * @param node 节点定义 Map
   * @return 提示词；缺失则为空串
   */
  public String extractNodePrompt(Map<String, Object> node) {
    Object configRaw = node.get("config");
    if (!(configRaw instanceof Map<?, ?> configMap)) {
      return "";
    }
    Object prompt = ((Map<?, ?>) configMap).get("prompt");
    return prompt == null ? "" : String.valueOf(prompt);
  }

  /**
   * 构造单条校验错误条目。
   *
   * @param code    错误码
   * @param message 人类可读说明
   * @param nodeKey 相关节点 key（边校验时可能是缺失的端点）
   * @return 错误 Map
   */
  public static Map<String, Object> error(String code, String message, String nodeKey) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("code", code);
    e.put("message", message);
    e.put("nodeKey", nodeKey);
    return e;
  }

  /**
   * 用定义实体字段生成空图种子，再委托 {@link #emptyDefinition(Map)}。
   *
   * @param def 工作流定义实体
   * @return 含空 nodes/edges 的定义 Map
   */
  public static Map<String, Object> emptyDefinition(WorkflowDefinitionEntity def) {
    Map<String, Object> seed = new LinkedHashMap<>();
    seed.put("name", def.getName() == null ? "" : def.getName());
    seed.put("description", def.getDescription() == null ? "" : def.getDescription());
    seed.put("scene", def.getScene() == null ? "course_content_generation" : def.getScene());
    seed.put("modeId", def.getModeId() == null ? "" : def.getModeId());
    return emptyDefinition(seed);
  }

  /**
   * 由元数据 Map 生成 schemaVersion=1 的空图定义。
   *
   * @param wf 含 name/description/scene/modeId 等键的种子 Map
   * @return 空 nodes/edges 的定义 Map
   */
  public static Map<String, Object> emptyDefinition(Map<String, Object> wf) {
    Map<String, Object> def = new LinkedHashMap<>();
    def.put("schemaVersion", "1");
    def.put("name", wf.getOrDefault("name", ""));
    def.put("description", wf.getOrDefault("description", ""));
    def.put("scene", wf.getOrDefault("scene", "course_content_generation"));
    def.put("modeId", wf.getOrDefault("modeId", ""));
    def.put("nodes", List.of());
    def.put("edges", List.of());
    return def;
  }

  /**
   * 构造“校验通过”的默认 validation Map。
   *
   * @return valid=true 且 errors/warnings 为空的结果
   */
  public static Map<String, Object> okValidation() {
    Map<String, Object> validation = new LinkedHashMap<>();
    validation.put(WorkflowValidationMapKeys.VALID, true);
    validation.put(WorkflowValidationMapKeys.ERRORS, List.of());
    validation.put(WorkflowValidationMapKeys.WARNINGS, List.of());
    validation.put(WorkflowValidationMapKeys.CHECKED_AT, LocalDateTime.now().toString());
    validation.put(WorkflowValidationMapKeys.DEFINITION_HASH, "");
    return validation;
  }


}
