package com.workbench.skill.application;

import cn.hutool.core.util.StrUtil;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.skill.domain.entity.WorkflowDefinitionEntity;
import com.workbench.skill.domain.entity.WorkflowDraftEntity;
import com.workbench.skill.domain.entity.WorkflowVersionEntity;
import com.workbench.skill.facade.dto.request.WorkflowDraftSaveRequest;
import com.workbench.skill.facade.dto.view.WorkflowDiffView;
import com.workbench.skill.facade.dto.view.WorkflowDraftView;
import com.workbench.skill.facade.dto.view.WorkflowValidationView;
import com.workbench.skill.infrastructure.mapper.WorkflowDefinitionMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowDraftMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowVersionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作流草稿：读写、校验、重置、差异与从版本派生。
 * <p>由 {@link WorkflowDefinitionService} 薄委托；定义存在性经 {@link WorkflowSupport}。
 */
@Service
@RequiredArgsConstructor
public class WorkflowDraftService {

  /** 草稿表。 */
  private final WorkflowDraftMapper draftMapper;
  /** 定义表（草稿变更时刷新 updatedAt）。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 版本表（从版本建草稿）。 */
  private final WorkflowVersionMapper versionMapper;
  /** 共享编解码与视图映射。 */
  private final WorkflowSupport support;

  /**
   * 为新建定义插入 revision=1 的空草稿。
   *
   * @param definition 已落库的定义实体
   * @param now        时间戳
   */
  @Transactional
  public void insertInitialEmptyDraft(WorkflowDefinitionEntity definition, LocalDateTime now) {
    draftMapper.insert(support.newDraftEntity(
        definition.getId(), WorkflowSupport.emptyDefinition(definition), 1, null, now));
  }

  /**
   * 获取工作流当前草稿。
   *
   * @param id 工作流定义主键
   * @return 草稿视图
   * @throws BusinessException 定义或草稿不存在
   */
  public WorkflowDraftView getDraft(String id) {
    support.require(id);
    WorkflowDraftEntity draft = support.findDraft(id);
    if (draft == null) {
      throw new BusinessException(ErrorCodes.WORKFLOW_DRAFT_NOT_FOUND, "工作流草稿不存在：" + id, 404);
    }
    // 再次 require 保证定义仍在（与 draft 一并转视图）
    return support.toDraftView(draft, support.require(id));
  }

  /**
   * 保存草稿定义：支持 revision 乐观锁；缺省定义时保留原 JSON。
   *
   * @param id   工作流定义主键
   * @param body 保存请求；可含 definition / definitionJson / revision
   * @return 保存后的草稿视图（revision 已自增）
   * @throws BusinessException 定义不存在或 revision 冲突（409）
   */
  @Transactional
  public WorkflowDraftView saveDraft(String id, WorkflowDraftSaveRequest body) {
    WorkflowDefinitionEntity def = support.require(id);
    WorkflowDraftSaveRequest input = body == null ? new WorkflowDraftSaveRequest() : body;
    WorkflowDraftEntity draft = support.findDraft(id);
    LocalDateTime now = LocalDateTime.now();
    // 尚无草稿行时先插入空壳再写
    if (draft == null) {
      draft = support.newDraftEntity(id, WorkflowSupport.emptyDefinition(def), 1, null, now);
      draftMapper.insert(draft);
    }
    int currentRevision = draft.getRevision() == null ? 1 : draft.getRevision();
    // 客户端带 revision 时做乐观锁校验，避免覆盖他人保存
    if (input.getRevision() != null) {
      if (input.getRevision() != currentRevision) {
        throw new BusinessException(
            ErrorCodes.WORKFLOW_REVISION_CONFLICT,
            "工作流草稿版本冲突：期望 " + currentRevision + "，实际 " + input.getRevision(),
            409);
      }
    }
    // 优先结构化 definition，其次 JSON 字符串，最后沿用库中内容
    Map<String, Object> definition;
    if (input.getDefinition() != null) {
      definition = support.toDefinitionMap(input.getDefinition());
    } else if (StrUtil.isNotBlank(input.getDefinitionJson())) {
      definition = support.readDefinition(input.getDefinitionJson(), def);
    } else {
      definition = support.readDefinition(draft.getDefinitionJson(), def);
    }
    // 每次保存顺带重算校验结果，写回 validationJson
    Map<String, Object> validation = support.validateDefinition(definition);
    draft.setRevision(currentRevision + 1);
    draft.setSchemaVersion(WorkflowRunStateSummaryAssembler.schemaVersionOf(definition));
    draft.setDefinitionJson(support.writeJson(definition));
    draft.setValidationJson(support.writeJson(validation));
    draft.setUpdatedAt(now);
    draftMapper.updateById(draft);
    // 定义 updatedAt 跟随草稿变更，便于列表排序
    def.setUpdatedAt(now);
    definitionMapper.updateById(def);
    return support.toDraftView(draft, def);
  }

  /**
   * 校验草稿图：请求带 definition 则校验该图，否则校验库中草稿/空定义。
   *
   * @param id   工作流定义主键
   * @param body 可选校验输入
   * @return 校验视图（valid/errors/warnings）
   * @throws BusinessException 定义不存在
   */
  public WorkflowValidationView validateDraft(String id, WorkflowDraftSaveRequest body) {
    WorkflowDefinitionEntity def = support.require(id);
    WorkflowDraftSaveRequest input = body == null ? new WorkflowDraftSaveRequest() : body;
    Map<String, Object> definition;
    if (input.getDefinition() != null) {
      // 前端未保存前的试校验
      definition = support.toDefinitionMap(input.getDefinition());
    } else {
      WorkflowDraftEntity draft = support.findDraft(id);
      definition = draft == null
          ? WorkflowSupport.emptyDefinition(def)
          : support.readDefinition(draft.getDefinitionJson(), def);
    }
    return support.toValidationView(support.validateDefinition(definition));
  }

  /**
   * 重置草稿为空图；若 body 已带定义/revision/JSON 则转调 {@link #saveDraft}。
   *
   * @param id   工作流定义主键
   * @param body 可选；带内容时等价保存
   * @return 重置或保存后的草稿
   * @throws BusinessException 定义不存在
   */
  @Transactional
  public WorkflowDraftView resetDraft(String id, WorkflowDraftSaveRequest body) {
    support.require(id);
    // 带实质内容时走保存路径，避免丢用户提交的图
    if (body != null && (body.getDefinition() != null || body.getRevision() != null
        || StrUtil.isNotBlank(body.getDefinitionJson()))) {
      return saveDraft(id, body);
    }
    WorkflowDefinitionEntity def = support.require(id);
    WorkflowDraftEntity draft = support.findDraft(id);
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> definition = WorkflowSupport.emptyDefinition(def);
    Map<String, Object> validation = WorkflowSupport.okValidation();
    if (draft == null) {
      draft = support.newDraftEntity(id, definition, 1, null, now);
      draftMapper.insert(draft);
    } else {
      // 清空图、revision 回 1、断开 baseVersion
      draft.setRevision(1);
      draft.setBaseVersionId(null);
      draft.setSchemaVersion(WorkflowRunStateSummaryAssembler.schemaVersionOf(definition));
      draft.setDefinitionJson(support.writeJson(definition));
      draft.setValidationJson(support.writeJson(validation));
      draft.setUpdatedAt(now);
      draftMapper.updateById(draft);
    }
    def.setUpdatedAt(now);
    definitionMapper.updateById(def);
    return support.toDraftView(draft, def);
  }

  /**
   * 草稿相对已发布版本的差异（占位：当前返回空 changes）。
   *
   * @param id 工作流定义主键
   * @return 差异视图
   * @throws BusinessException 定义不存在
   */
  public WorkflowDiffView draftDiff(String id) {
    support.require(id);
    // 差异算法尚未实现，先返回空列表保持接口稳定
    return WorkflowDiffView.builder().changes(List.of()).build();
  }

  /**
   * 从历史版本创建或覆盖当前草稿（baseVersionId 指向源版本）。
   *
   * @param workflowId 工作流定义主键
   * @param versionId  源版本主键
   * @return 基于该版本快照的草稿视图
   * @throws BusinessException 定义或版本不存在，或版本不属于该工作流
   */
  @Transactional
  public WorkflowDraftView createDraftFromVersion(String workflowId, String versionId) {
    WorkflowDefinitionEntity def = support.require(workflowId);
    WorkflowVersionEntity version = versionMapper.selectById(versionId);
    if (version == null || !workflowId.equals(version.getWorkflowId())) {
      throw new BusinessException(ErrorCodes.WORKFLOW_VERSION_NOT_FOUND, "版本不存在：" + versionId, 404);
    }
    LocalDateTime now = LocalDateTime.now();
    Map<String, Object> snapshot = support.readDefinition(version.getSnapshotJson(), def);
    // 草稿 revision 在版本 revision 基础上 +1，便于区分派生
    int nextRevision = (version.getRevision() == null ? 1 : version.getRevision()) + 1;
    WorkflowDraftEntity draft = support.findDraft(workflowId);
    if (draft == null) {
      draft = support.newDraftEntity(workflowId, snapshot, nextRevision, versionId, now);
      draftMapper.insert(draft);
    } else {
      draft.setBaseVersionId(versionId);
      draft.setRevision(nextRevision);
      draft.setSchemaVersion(WorkflowRunStateSummaryAssembler.schemaVersionOf(snapshot));
      draft.setDefinitionJson(support.writeJson(snapshot));
      draft.setValidationJson(support.writeJson(WorkflowSupport.okValidation()));
      draft.setUpdatedAt(now);
      draftMapper.updateById(draft);
    }
    def.setUpdatedAt(now);
    definitionMapper.updateById(def);
    return support.toDraftView(draft, def);
  }
}
