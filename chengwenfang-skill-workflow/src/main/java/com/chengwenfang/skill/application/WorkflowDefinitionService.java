package com.chengwenfang.skill.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.skill.domain.WorkflowNodeCatalogUtil;
import com.chengwenfang.skill.domain.entity.WorkflowDefinitionEntity;
import com.chengwenfang.skill.domain.entity.WorkflowDraftEntity;
import com.chengwenfang.skill.facade.constant.CourseWorkflowKeys;
import com.chengwenfang.skill.facade.constant.WorkflowOwnerKeys;
import com.chengwenfang.skill.facade.dto.request.WorkflowCreateRequest;
import com.chengwenfang.skill.facade.dto.request.WorkflowDraftSaveRequest;
import com.chengwenfang.skill.facade.dto.request.WorkflowPublishRequest;
import com.chengwenfang.skill.facade.dto.request.WorkflowUpdateRequest;
import com.chengwenfang.skill.facade.dto.view.WorkflowDefinitionDataView;
import com.chengwenfang.skill.facade.dto.view.WorkflowDetailView;
import com.chengwenfang.skill.facade.dto.view.WorkflowDiffView;
import com.chengwenfang.skill.facade.dto.view.WorkflowDraftView;
import com.chengwenfang.skill.facade.dto.view.WorkflowNodeTypeView;
import com.chengwenfang.skill.facade.dto.view.WorkflowSummaryView;
import com.chengwenfang.skill.facade.dto.view.WorkflowValidationView;
import com.chengwenfang.skill.facade.dto.view.WorkflowVersionDiffView;
import com.chengwenfang.skill.facade.dto.view.WorkflowVersionView;
import com.chengwenfang.skill.facade.enums.WorkflowDefinitionStatus;
import com.chengwenfang.skill.infrastructure.mapper.WorkflowDefinitionMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作流定义：列表/详情/创建/更新与节点类型目录。
 * <p>草稿与版本能力分别委托 {@link WorkflowDraftService}、{@link WorkflowVersionService}。
 * <p>由 {@link WorkflowFacadeImpl} 薄委托，不对外暴露。
 */
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionService {

  /** 工作流定义表。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 共享编解码与视图映射。 */
  private final WorkflowSupport support;
  /** 详情页嵌入的运行指标。 */
  private final WorkflowObservabilityService observability;
  /** 草稿读写与校验。 */
  private final WorkflowDraftService drafts;
  /** 发布与版本。 */
  private final WorkflowVersionService versions;

  /**
   * 列出全部工作流定义摘要，按更新时间倒序。
   *
   * @return 摘要视图列表
   */
  public List<WorkflowSummaryView> listWorkflows() {
    // 最新更新的定义排前，转为对外摘要
    return definitionMapper.selectList(new LambdaQueryWrapper<WorkflowDefinitionEntity>()
            .orderByDesc(WorkflowDefinitionEntity::getUpdatedAt))
        .stream()
        .map(support::toSummary)
        .toList();
  }

  /**
   * 列出已注册的节点类型目录（供编辑器选用）。
   *
   * @return 节点类型视图列表
   */
  public List<WorkflowNodeTypeView> listNodeTypes() {
    // 来自领域目录静态注册，非库表
    return WorkflowNodeCatalogUtil.registeredNodeTypes();
  }

  /**
   * 获取工作流详情：摘要字段 + 当前草稿定义/校验 + 运行指标汇总。
   *
   * @param id 工作流定义主键
   * @return 详情视图
   * @throws BusinessException 定义不存在
   */
  public WorkflowDetailView getWorkflow(String id) {
    WorkflowDefinitionEntity def = support.require(id);
    WorkflowSummaryView summary = support.toSummary(def);
    WorkflowDraftEntity draft = support.findDraft(id);
    // 无草稿时用空定义壳，避免前端 null
    WorkflowDefinitionDataView definition = draft == null
        ? support.toDefinitionData(WorkflowSupport.emptyDefinition(def))
        : support.toDefinitionData(support.readDefinition(draft.getDefinitionJson(), def));
    WorkflowValidationView validation = draft == null
        ? support.toValidationView(WorkflowSupport.okValidation())
        : support.toValidationView(support.readValidation(draft.getValidationJson()));
    // 指标随详情一并返回，减少前端二次请求
    return WorkflowDetailView.builder()
        .id(summary.getId())
        .key(summary.getKey())
        .name(summary.getName())
        .description(summary.getDescription())
        .scene(summary.getScene())
        .status(summary.getStatus())
        .owner(summary.getOwner())
        .publishedVersionId(summary.getPublishedVersionId())
        .publishedVersion(summary.getPublishedVersion())
        .draftRevision(summary.getDraftRevision())
        .updatedAt(summary.getUpdatedAt())
        .definition(definition)
        .validation(validation)
        .metrics(observability.metricsSummary(id))
        .build();
  }

  /**
   * 创建工作流定义，并插入 revision=1 的空草稿。
   *
   * @param body 创建请求；null 按空对象；缺省 key/name/scene 有兜底
   * @return 新建定义的摘要视图
   */
  @Transactional
  public WorkflowSummaryView createWorkflow(WorkflowCreateRequest body) {
    WorkflowCreateRequest input = body == null ? new WorkflowCreateRequest() : body;
    LocalDateTime now = LocalDateTime.now();
    // 组装定义行：本地 owner、初始 draft 状态、尚未发布
    WorkflowDefinitionEntity def = new WorkflowDefinitionEntity();
    def.setId(IdsUtil.newId());
    def.setWorkflowKey(StrUtil.isBlank(input.getKey()) ? "wf-" + IdsUtil.newId() : input.getKey());
    def.setName(StrUtil.isBlank(input.getName()) ? "Untitled workflow" : input.getName());
    def.setDescription(StrUtil.nullToEmpty(input.getDescription()));
    def.setScene(StrUtil.blankToDefault(input.getScene(), CourseWorkflowKeys.SCENE));
    def.setModeId(input.getModeId());
    def.setStatus(WorkflowDefinitionStatus.DRAFT.getValue());
    def.setOwner(WorkflowOwnerKeys.LOCAL);
    def.setPublishedVersionId(null);
    def.setCreatedAt(now);
    def.setUpdatedAt(now);
    definitionMapper.insert(def);

    // 同步创建空图草稿，便于立刻进入编辑
    drafts.insertInitialEmptyDraft(def, now);
    return support.toSummary(def);
  }

  /**
   * 部分更新工作流元数据（名称、描述、状态）；未传字段保持原值。
   *
   * @param id   工作流定义主键
   * @param body 更新请求；null 按空对象（相当于只刷新 updatedAt）
   * @return 更新后的摘要
   * @throws BusinessException 定义不存在
   */
  @Transactional
  public WorkflowSummaryView updateWorkflow(String id, WorkflowUpdateRequest body) {
    WorkflowDefinitionEntity def = support.require(id);
    WorkflowUpdateRequest input = body == null ? new WorkflowUpdateRequest() : body;
    // 仅合并非 null 字段
    if (input.getName() != null) {
      def.setName(input.getName());
    }
    if (input.getDescription() != null) {
      def.setDescription(input.getDescription());
    }
    if (input.getStatus() != null) {
      def.setStatus(input.getStatus());
    }
    def.setUpdatedAt(LocalDateTime.now());
    definitionMapper.updateById(def);
    return support.toSummary(def);
  }

  /**
   * 获取工作流当前草稿。
   *
   * @param id 工作流定义主键
   * @return 草稿视图
   */
  public WorkflowDraftView getDraft(String id) {
    return drafts.getDraft(id);
  }

  /**
   * 保存草稿定义。
   *
   * @param id   工作流定义主键
   * @param body 保存请求
   * @return 保存后的草稿视图
   */
  @Transactional
  public WorkflowDraftView saveDraft(String id, WorkflowDraftSaveRequest body) {
    return drafts.saveDraft(id, body);
  }

  /**
   * 校验草稿图。
   *
   * @param id   工作流定义主键
   * @param body 可选校验输入
   * @return 校验视图
   */
  public WorkflowValidationView validateDraft(String id, WorkflowDraftSaveRequest body) {
    return drafts.validateDraft(id, body);
  }

  /**
   * 重置草稿为空图。
   *
   * @param id   工作流定义主键
   * @param body 可选；带内容时等价保存
   * @return 重置或保存后的草稿
   */
  @Transactional
  public WorkflowDraftView resetDraft(String id, WorkflowDraftSaveRequest body) {
    return drafts.resetDraft(id, body);
  }

  /**
   * 草稿相对已发布版本的差异。
   *
   * @param id 工作流定义主键
   * @return 差异视图
   */
  public WorkflowDiffView draftDiff(String id) {
    return drafts.draftDiff(id);
  }

  /**
   * 从历史版本创建或覆盖当前草稿。
   *
   * @param workflowId 工作流定义主键
   * @param versionId  源版本主键
   * @return 基于该版本快照的草稿视图
   */
  @Transactional
  public WorkflowDraftView createDraftFromVersion(String workflowId, String versionId) {
    return drafts.createDraftFromVersion(workflowId, versionId);
  }

  /**
   * 发布草稿为新版本。
   *
   * @param id   工作流定义主键
   * @param body 发布请求
   * @return 新版本视图
   */
  @Transactional
  public WorkflowVersionView publish(String id, WorkflowPublishRequest body) {
    return versions.publish(id, body);
  }

  /**
   * 列出某工作流全部已发布版本。
   *
   * @param id 工作流定义主键
   * @return 版本视图列表
   */
  public List<WorkflowVersionView> listVersions(String id) {
    return versions.listVersions(id);
  }

  /**
   * 两版本差异。
   *
   * @param id   工作流定义主键
   * @param from 起始版本标识
   * @param to   目标版本标识
   * @return 版本差异视图
   */
  public WorkflowVersionDiffView versionDiff(String id, String from, String to) {
    return versions.versionDiff(id, from, to);
  }
}
