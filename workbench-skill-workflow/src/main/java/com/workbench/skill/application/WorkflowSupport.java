package com.workbench.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.IdsUtil;
import com.workbench.skill.domain.entity.WorkflowAlertEntity;
import com.workbench.skill.domain.entity.WorkflowDefinitionEntity;
import com.workbench.skill.domain.entity.WorkflowDraftEntity;
import com.workbench.skill.domain.entity.WorkflowNodeRunEntity;
import com.workbench.skill.domain.entity.WorkflowRunEntity;
import com.workbench.skill.domain.entity.WorkflowVersionEntity;
import com.workbench.skill.domain.entity.WorkflowVersionSkillBindingEntity;
import com.workbench.skill.facade.dto.view.WorkflowAlertView;
import com.workbench.skill.facade.dto.view.WorkflowDefinitionDataView;
import com.workbench.skill.facade.dto.view.WorkflowDraftView;
import com.workbench.skill.facade.dto.view.WorkflowNodeRunView;
import com.workbench.skill.facade.dto.view.WorkflowRunView;
import com.workbench.skill.facade.dto.view.WorkflowSummaryView;
import com.workbench.skill.facade.dto.view.WorkflowValidationIssueView;
import com.workbench.skill.facade.dto.view.WorkflowValidationView;
import com.workbench.skill.facade.dto.view.WorkflowVersionView;
import com.workbench.skill.facade.enums.WorkflowFeatureBinding;
import com.workbench.skill.infrastructure.mapper.WorkflowDefinitionMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowDraftMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowVersionSkillBindingMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 工作流协作服务共享门面：实体加载与草稿组装；视图/校验/指标委派至拆分类。
 * <p>由各 Workflow*Service 注入使用，不对外暴露。
 */
@Component
@RequiredArgsConstructor
public class WorkflowSupport {

  /** 工作流定义表。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 草稿表。 */
  private final WorkflowDraftMapper draftMapper;
  /** 版本 Skill 绑定表。 */
  private final WorkflowVersionSkillBindingMapper bindingMapper;
  /** 视图组装。 */
  private final WorkflowViewAssembler viewAssembler;
  /** 图校验。 */
  private final WorkflowGraphValidator graphValidator;

  public WorkflowDefinitionEntity require(String id) {
    WorkflowDefinitionEntity def = definitionMapper.selectById(id);
    // 统一 404，供各入口复用
    if (def == null) {
      throw new BusinessException(ErrorCodes.WORKFLOW_NOT_FOUND, "工作流不存在：" + id, 404);
    }
    return def;
  }

  /**
   * 按工作流 id 查找草稿（每定义通常至多一行）。
   *
   * @param workflowId 工作流定义主键
   * @return 草稿实体，或 null
   */
  public WorkflowDraftEntity findDraft(String workflowId) {
    // LIMIT 1：约定每工作流最多一条草稿
    return draftMapper.selectOne(new LambdaQueryWrapper<WorkflowDraftEntity>()
        .eq(WorkflowDraftEntity::getWorkflowId, workflowId)
        .last("LIMIT 1"));
  }

  /**
   * 组装尚未持久化的草稿实体（含空校验 JSON）。
   *
   * @param workflowId    所属定义主键
   * @param definition    图定义 Map
   * @param revision      草稿修订号
   * @param baseVersionId 基线版本 id；可为 null
   * @param now           创建/更新时间戳
   * @return 未 insert 的草稿实体
   */
  public WorkflowDraftEntity newDraftEntity(
      String workflowId,
      Map<String, Object> definition,
      int revision,
      String baseVersionId,
      LocalDateTime now) {
    // 仅组装实体，由调用方决定 insert 还是 update
    WorkflowDraftEntity draft = new WorkflowDraftEntity();
    draft.setId(IdsUtil.newId());
    draft.setWorkflowId(workflowId);
    draft.setBaseVersionId(baseVersionId);
    draft.setRevision(revision);
    draft.setSchemaVersion(WorkflowRunStateSummaryAssembler.schemaVersionOf(definition));
    draft.setDefinitionJson(viewAssembler.writeJson(definition));
    draft.setValidationJson(viewAssembler.writeJson(WorkflowGraphValidator.okValidation()));
    draft.setUpdatedBy("local");
    draft.setCreatedAt(now);
    draft.setUpdatedAt(now);
    return draft;
  }

  /**
   * 发布时从快照 nodes[].skillBindings 写入版本 Skill 绑定行。
   *
   * @param versionId 新版本主键
   * @param snapshot  发布时的图定义 Map
   */
  @SuppressWarnings("unchecked")
  public void syncSkillBindings(String versionId, Map<String, Object> snapshot) {
    Object nodesRaw = snapshot.get("nodes");
    if (!(nodesRaw instanceof List<?> nodes)) {
      return;
    }
    for (Object nodeObj : nodes) {
      if (!(nodeObj instanceof Map<?, ?> nodeMap)) {
        continue;
      }
      Map<String, Object> node = (Map<String, Object>) nodeMap;
      String nodeKey = String.valueOf(node.getOrDefault("key", ""));
      Object bindingsRaw = node.get("skillBindings");
      if (!(bindingsRaw instanceof List<?> bindings) || nodeKey.isBlank()) {
        continue;
      }
      for (Object bindingObj : bindings) {
        if (!(bindingObj instanceof Map<?, ?> bindingMap)) {
          continue;
        }
        Map<String, Object> binding = (Map<String, Object>) bindingMap;
        Object skillId = binding.get("skillId");
        if (skillId == null || String.valueOf(skillId).isBlank()) {
          continue;
        }
        WorkflowVersionSkillBindingEntity row = new WorkflowVersionSkillBindingEntity();
        row.setId(IdsUtil.newId());
        row.setVersionId(versionId);
        row.setNodeKey(nodeKey);
        row.setSkillId(String.valueOf(skillId));
        row.setSkillVersion(binding.get("skillVersion") instanceof Number number ? number.intValue() : null);
        // 未指定优先级时默认 100
        row.setPriority(binding.get("priority") instanceof Number number ? number.intValue() : 100);
        row.setContentHash(binding.get("contentHash") == null ? null : String.valueOf(binding.get("contentHash")));
        row.setSnapshotJson(viewAssembler.writeJson(binding));
        bindingMapper.insert(row);
      }
    }
  }


  /**
   * @param entity 节点运行实体
   * @return 节点运行视图
   */
  public WorkflowNodeRunView toNodeRunView(WorkflowNodeRunEntity entity) {
    return viewAssembler.toNodeRunView(entity);
  }

  /**
   * @param value 待哈希对象
   * @return SHA-256 十六进制；失败为空串
   */
  public String hashJson(Object value) {
    return viewAssembler.hashJson(value);
  }

  /**
   * @param run 运行实体
   * @return 运行视图
   */
  public WorkflowRunView toRunView(WorkflowRunEntity run) {
    return viewAssembler.toRunView(run);
  }

  /**
   * @param alert 告警实体
   * @return 告警视图
   */
  public WorkflowAlertView toAlertView(WorkflowAlertEntity alert) {
    return viewAssembler.toAlertView(alert);
  }

  /**
   * @param json JSON 文本
   * @return Map；永不 null
   */
  public Map<String, Object> readJsonMap(String json) {
    return viewAssembler.readJsonMap(json);
  }

  /**
   * @param def 工作流定义
   */
  public void assertDefinitionRunnable(WorkflowDefinitionEntity def) {
    graphValidator.assertDefinitionRunnable(def);
  }

  /**
   * @param def     工作流定义
   * @param binding 功能绑定
   */
  public void assertDefinitionRunnable(WorkflowDefinitionEntity def, WorkflowFeatureBinding binding) {
    graphValidator.assertDefinitionRunnable(def, binding);
  }

  /**
   * @param def 定义实体
   * @return 摘要视图
   */
  public WorkflowSummaryView toSummary(WorkflowDefinitionEntity def) {
    return viewAssembler.toSummary(def);
  }

  /**
   * @param draft 草稿实体
   * @param def   所属定义
   * @return 草稿视图
   */
  public WorkflowDraftView toDraftView(WorkflowDraftEntity draft, WorkflowDefinitionEntity def) {
    return viewAssembler.toDraftView(draft, def);
  }

  /**
   * @param version 版本实体
   * @return 版本视图
   */
  public WorkflowVersionView toVersionView(WorkflowVersionEntity version) {
    return viewAssembler.toVersionView(version);
  }

  /**
   * @param definition 图定义 Map
   * @return 校验结果 Map
   */
  public Map<String, Object> validateDefinition(Map<String, Object> definition) {
    return graphValidator.validateDefinition(definition);
  }

  /**
   * @param node 节点定义 Map
   * @return 提示词
   */
  public String extractNodePrompt(Map<String, Object> node) {
    return graphValidator.extractNodePrompt(node);
  }

  /**
   * @param code    错误码
   * @param message 说明
   * @param nodeKey 节点 key
   * @return 错误 Map
   */
  public static Map<String, Object> error(String code, String message, String nodeKey) {
    return WorkflowGraphValidator.error(code, message, nodeKey);
  }

  /**
   * @param def 工作流定义实体
   * @return 空图定义 Map
   */
  public static Map<String, Object> emptyDefinition(WorkflowDefinitionEntity def) {
    return WorkflowGraphValidator.emptyDefinition(def);
  }

  /**
   * @param wf 种子 Map
   * @return 空图定义 Map
   */
  public static Map<String, Object> emptyDefinition(Map<String, Object> wf) {
    return WorkflowGraphValidator.emptyDefinition(wf);
  }

  /**
   * @return 默认通过校验 Map
   */
  public static Map<String, Object> okValidation() {
    return WorkflowGraphValidator.okValidation();
  }

  /**
   * @param json 定义 JSON
   * @param def  所属定义
   * @return 定义 Map
   */
  public Map<String, Object> readDefinition(String json, WorkflowDefinitionEntity def) {
    return viewAssembler.readDefinition(json, def);
  }

  /**
   * @param json 校验 JSON
   * @return 校验 Map
   */
  public Map<String, Object> readValidation(String json) {
    return viewAssembler.readValidation(json);
  }

  /**
   * @param map 定义 Map
   * @return 结构化定义
   */
  public WorkflowDefinitionDataView toDefinitionData(Map<String, Object> map) {
    return viewAssembler.toDefinitionData(map);
  }

  /**
   * @param data 结构化定义
   * @return 定义 Map
   */
  public Map<String, Object> toDefinitionMap(WorkflowDefinitionDataView data) {
    return viewAssembler.toDefinitionMap(data);
  }

  /**
   * @param map 校验结果 Map
   * @return 校验视图
   */
  public WorkflowValidationView toValidationView(Map<String, Object> map) {
    return viewAssembler.toValidationView(map);
  }

  /**
   * @param raw 历史/现行校验条目
   * @return 规范化列表
   */
  public List<WorkflowValidationIssueView> normalizeValidationIssues(Object raw) {
    return viewAssembler.normalizeValidationIssues(raw);
  }

  /**
   * @param code 遗留错误码
   * @return 校验问题视图
   */
  public static WorkflowValidationIssueView issueFromLegacyCode(String code) {
    return WorkflowViewAssembler.issueFromLegacyCode(code);
  }

  /**
   * @param value 任意对象
   * @return JSON 文本
   */
  public String writeJson(Object value) {
    return viewAssembler.writeJson(value);
  }

  /**
   * @param json JSON 文本
   * @return Map 或 null
   */
  public Map<String, Object> readMap(String json) {
    return viewAssembler.readMap(json);
  }
}
