package com.workbench.skill.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.IdsUtil;
import com.workbench.skill.domain.entity.WorkflowDefinitionEntity;
import com.workbench.skill.domain.entity.WorkflowDraftEntity;
import com.workbench.skill.domain.entity.WorkflowVersionEntity;
import com.workbench.skill.facade.constant.WorkflowOwnerKeys;
import com.workbench.skill.facade.constant.WorkflowValidationMapKeys;
import com.workbench.skill.facade.dto.request.WorkflowPublishRequest;
import com.workbench.skill.facade.dto.view.WorkflowVersionDiffView;
import com.workbench.skill.facade.dto.view.WorkflowVersionView;
import com.workbench.skill.facade.enums.WorkflowDefinitionStatus;
import com.workbench.skill.infrastructure.mapper.WorkflowDefinitionMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowVersionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作流版本：发布快照、版本列表与版本差异。
 * <p>由 {@link WorkflowDefinitionService} 薄委托；发布时同步更新定义的 publishedVersionId / status。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowVersionService {

  /** 已发布版本表。 */
  private final WorkflowVersionMapper versionMapper;
  /** 定义表（发布后写回 publishedVersionId）。 */
  private final WorkflowDefinitionMapper definitionMapper;
  /** 共享编解码与视图映射。 */
  private final WorkflowSupport support;

  /**
   * 发布草稿为新版本：校验通过后写版本快照、同步 Skill 绑定。启用开关保持不变。
   *
   * @param id   工作流定义主键
   * @param body 发布请求（版本号、发布说明）；可为空
   * @return 新版本视图（含 publishedVersionId）
   * @throws BusinessException 定义不存在或图校验失败
   */
  @Transactional
  public WorkflowVersionView publish(String id, WorkflowPublishRequest body) {
    WorkflowDefinitionEntity def = support.require(id);
    WorkflowPublishRequest input = body == null ? new WorkflowPublishRequest() : body;
    WorkflowDraftEntity draft = support.findDraft(id);
    // 取草稿快照并校验；失败则拒绝发布
    Map<String, Object> snapshot = draft == null
        ? WorkflowSupport.emptyDefinition(def)
        : support.readDefinition(draft.getDefinitionJson(), def);
    Map<String, Object> validation = support.validateDefinition(snapshot);
    if (!Boolean.TRUE.equals(validation.get(WorkflowValidationMapKeys.VALID))) {
      throw new BusinessException(ErrorCodes.WORKFLOW_INVALID, "工作流校验失败", 400);
    }
    log.info("工作流发布开始 id={} key={}", id, def.getWorkflowKey());
    // 未指定版本号时用「已有版本数+1」
    long versionCount = versionMapper.selectCount(new LambdaQueryWrapper<WorkflowVersionEntity>()
        .eq(WorkflowVersionEntity::getWorkflowId, id));
    String versionLabel = StrUtil.blankToDefault(
        input.getVersion(), String.valueOf(versionCount + 1));
    LocalDateTime now = LocalDateTime.now();
    // 写入新版本 + skill 绑定；发布版本与启用开关分开，不把 status 改成 published
    WorkflowVersionEntity version = new WorkflowVersionEntity();
    version.setId(IdsUtil.newId());
    version.setWorkflowId(id);
    version.setVersion(versionLabel);
    version.setRevision(draft == null || draft.getRevision() == null ? 1 : draft.getRevision());
    version.setSchemaVersion(WorkflowRunStateSummaryAssembler.schemaVersionOf(snapshot));
    version.setSnapshotJson(support.writeJson(snapshot));
    version.setValidationJson(support.writeJson(validation));
    version.setReleaseNotes(StrUtil.nullToEmpty(input.getReleaseNotes()));
    version.setPublishedBy(WorkflowOwnerKeys.LOCAL);
    version.setCreatedAt(now);
    versionMapper.insert(version);
    // 从快照 nodes[].skillBindings 抽出绑定行，供运行时解析
    support.syncSkillBindings(version.getId(), snapshot);

    def.setPublishedVersionId(version.getId());
    def.setStatus(WorkflowDefinitionStatus.afterPublish(
        WorkflowDefinitionStatus.from(def.getStatus())).getValue());
    def.setUpdatedAt(now);
    definitionMapper.updateById(def);

    WorkflowVersionView published = support.toVersionView(version);
    published.setPublishedVersionId(version.getId());
    return published;
  }

  /**
   * 列出某工作流全部已发布版本，按创建时间升序。
   *
   * @param id 工作流定义主键
   * @return 版本视图列表
   * @throws BusinessException 定义不存在
   */
  public List<WorkflowVersionView> listVersions(String id) {
    support.require(id);
    // 按发布时间升序，便于版本时间线展示
    return versionMapper.selectList(new LambdaQueryWrapper<WorkflowVersionEntity>()
            .eq(WorkflowVersionEntity::getWorkflowId, id)
            .orderByAsc(WorkflowVersionEntity::getCreatedAt))
        .stream()
        .map(support::toVersionView)
        .toList();
  }

  /**
   * 两版本差异（占位：当前仅回显 from/to，changes 为空）。
   *
   * @param id   工作流定义主键
   * @param from 起始版本标识
   * @param to   目标版本标识
   * @return 版本差异视图
   * @throws BusinessException 定义不存在
   */
  public WorkflowVersionDiffView versionDiff(String id, String from, String to) {
    support.require(id);
    // 差异算法尚未实现，先保证接口契约
    return WorkflowVersionDiffView.builder()
        .from(from == null ? "" : from)
        .to(to == null ? "" : to)
        .changes(List.of())
        .build();
  }
}
