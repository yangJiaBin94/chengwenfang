package com.workbench.skill.application;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.IdsUtil;
import com.workbench.common.PageQuery;
import com.workbench.common.PageResult;
import com.workbench.skill.domain.WorkflowNodeCatalogUtil;
import com.workbench.skill.domain.entity.SkillEntity;
import com.workbench.skill.domain.entity.SkillNodeBindingEntity;
import com.workbench.skill.domain.entity.WorkflowDefinitionEntity;
import com.workbench.skill.domain.entity.WorkflowVersionSkillBindingEntity;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.constant.HotNewsWorkflowKeys;
import com.workbench.skill.facade.dto.view.SkillApplicationView;
import com.workbench.skill.facade.dto.view.SkillNodeBindingView;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.dto.request.SkillSaveRequest;
import com.workbench.skill.facade.dto.view.SkillView;
import com.workbench.skill.facade.dto.view.SkillWorkflowNodeView;
import com.workbench.skill.infrastructure.mapper.ContentModeLookupMapper;
import com.workbench.skill.infrastructure.mapper.SkillMapper;
import com.workbench.skill.infrastructure.mapper.SkillNodeBindingMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowDefinitionMapper;
import com.workbench.skill.infrastructure.mapper.WorkflowVersionSkillBindingMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SkillFacade} 实现：Skill CRUD、节点目录与提示词组合。
 * <p>节点绑定存 skill_default_bindings；提示词组合只读当前 skills 表指令，不读历史版本表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillFacadeImpl implements SkillFacade {
  /** Skill 主表读写。 */
  private final SkillMapper skillMapper;
  /** Skill 与工作流节点的绑定表读写。 */
  private final SkillNodeBindingMapper bindingMapper;
  /** 已发布版本上的 Skill 绑定（应用位置来源）。 */
  private final WorkflowVersionSkillBindingMapper versionSkillBindingMapper;
  /** 工作流定义（筛选正式启用且已发布）。 */
  private final WorkflowDefinitionMapper workflowDefinitionMapper;
  /** 教学模式名称只读查询。 */
  private final ContentModeLookupMapper contentModeLookupMapper;
  /** 实体到对外 View 的映射。 */
  private final SkillViewMapper viewMapper;

  /**
   * 列出全部未删除的 Skill，按 priority 升序。
   *
   * @return Skill 视图列表（含各自节点绑定）
   */
  @Override
  public List<SkillView> listSkills() {
    // 仅查未软删记录，按优先级排序后转为对外视图
    return skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
            .isNull(SkillEntity::getDeletedAt)
            .orderByAsc(SkillEntity::getPriority))
        .stream()
        .map(this::toView)
        .toList();
  }

  /**
   * 分页列出未删除的 Skill：关键字匹配名称/简介/system/task 指令，按 priority 升序。
   *
   * @param query 分页与关键字；null 时按默认页码页大小
   * @return 当前页视图与筛选总数；页码超出时夹到最后一页
   */
  @Override
  public PageResult<SkillView> pageSkills(PageQuery query) {
    PageQuery normalized = query == null ? new PageQuery().normalized() : query.normalized();
    String keyword = StrUtil.trim(normalized.getKeyword());

    // 列表筛选条件（影响 total / items）；count 与 page 分拆 wrapper，避免 last 污染
    LambdaQueryWrapper<SkillEntity> filterQuery = new LambdaQueryWrapper<SkillEntity>()
        .isNull(SkillEntity::getDeletedAt);
    if (StrUtil.isNotBlank(keyword)) {
      filterQuery.and(wrapper -> wrapper
          .like(SkillEntity::getName, keyword)
          .or()
          .like(SkillEntity::getDescription, keyword)
          .or()
          .like(SkillEntity::getSystemInstructions, keyword)
          .or()
          .like(SkillEntity::getTaskInstructions, keyword));
    }

    long total = skillMapper.selectCount(filterQuery);
    List<SkillView> items = List.of();
    if (total > 0) {
      int offset = normalized.offset();
      // 页码超出时夹到最后一页，避免空页
      if (offset >= total) {
        int lastPage = (int) Math.max(1, (total + normalized.getPageSize() - 1) / normalized.getPageSize());
        normalized = PageQuery.builder()
            .page(lastPage)
            .pageSize(normalized.getPageSize())
            .keyword(normalized.getKeyword())
            .build()
            .normalized();
        offset = normalized.offset();
      }
      LambdaQueryWrapper<SkillEntity> pageQuery = filterQuery
          .orderByAsc(SkillEntity::getPriority)
          .last("LIMIT " + normalized.getPageSize() + " OFFSET " + offset);
      items = skillMapper.selectList(pageQuery).stream().map(this::toView).toList();
    }
    return PageResult.of(normalized, items, total);
  }

  /**
   * 按名称/场景幂等写入 Skill：请求带已存在 id 则更新，否则创建。
   *
   * @param body 保存请求；null 按空对象处理；{@code id} 有对应行时走更新
   * @return 创建或更新后的 Skill 视图
   */
  @Override
  @Transactional
  public SkillView upsertSkill(SkillSaveRequest body) {
    // 空请求按空对象，避免 NPE
    SkillSaveRequest input = body == null ? new SkillSaveRequest() : body;
    String id = input.getId();
    // 已有主键则走更新路径，实现幂等 upsert
    if (id != null && skillMapper.selectById(id) != null) {
      return updateSkillById(id, input);
    }
    return createSkill(input);
  }

  /**
   * 创建 Skill，并全量替换其节点绑定。
   *
   * @param body 创建请求；null 按空对象；空白 {@code id} 时自动生成
   * @return 新建 Skill 的视图（含绑定）
   */
  @Override
  @Transactional
  public SkillView createSkill(SkillSaveRequest body) {
    SkillSaveRequest input = body == null ? new SkillSaveRequest() : body;
    LocalDateTime now = LocalDateTime.now();
    // 组装实体：可指定 id，否则生成；创建时 deletedAt 置空
    SkillEntity entity = SkillEntity.builder()
        .id(StrUtil.isBlank(input.getId()) ? IdsUtil.newId() : input.getId())
        .createdAt(now)
        .updatedAt(now)
        .deletedAt(null)
        .build();
    // creating=true：缺省字段写入默认值
    applySkillFields(entity, input, true);
    skillMapper.insert(entity);
    // 创建时无论绑定是否为空都做一次全量替换（空则清空）
    replaceBindings(entity.getId(), input.getNodeBindings(), now);
    log.info("创建技能 id={}", entity.getId());
    return toView(entity);
  }

  /**
   * 按 ID 更新未删除的 Skill；仅当请求显式带 nodeBindings 时才替换绑定。
   *
   * @param id   Skill 主键
   * @param body 更新请求；null 按空对象；字段为 null 表示该字段不改（创建标志为 false）
   * @return 更新后的 Skill 视图
   * @throws BusinessException 技能不存在或已软删时抛出
   */
  @Override
  @Transactional
  public SkillView updateSkillById(String id, SkillSaveRequest body) {
    // 必须存在且未软删
    SkillEntity entity = requireActive(id);
    SkillSaveRequest input = body == null ? new SkillSaveRequest() : body;
    // creating=false：仅合并请求中非 null 字段
    applySkillFields(entity, input, false);
    entity.setUpdatedAt(LocalDateTime.now());
    skillMapper.updateById(entity);
    // 绑定可选：未传则保留原绑定
    if (input.getNodeBindings() != null) {
      replaceBindings(id, input.getNodeBindings(), LocalDateTime.now());
    }
    return toView(entity);
  }

  /**
   * 软删除 Skill（写 deletedAt，不物理删绑定）。
   *
   * @param id Skill 主键
   * @throws BusinessException 技能不存在或已软删时抛出
   */
  @Override
  @Transactional
  public void deleteSkill(String id) {
    SkillEntity entity = requireActive(id);
    // 软删：保留行以便审计与历史引用
    entity.setDeletedAt(LocalDateTime.now());
    entity.setUpdatedAt(LocalDateTime.now());
    skillMapper.updateById(entity);
    log.info("软删除技能 id={}", id);
  }

  /**
   * 列出可绑定的工作流节点目录：从已启用绑定去重，若无数据则回退内置默认节点。
   *
   * @return 节点目录视图列表（workflowType + nodeKey）
   */
  @Override
  public List<SkillWorkflowNodeView> listWorkflowNodes() {
    // 读启用中的绑定，作为节点目录来源
    List<SkillNodeBindingEntity> bindings = bindingMapper.selectList(new LambdaQueryWrapper<SkillNodeBindingEntity>()
        .eq(SkillNodeBindingEntity::getEnabled, true)
        .orderByAsc(SkillNodeBindingEntity::getWorkflowType)
        .orderByAsc(SkillNodeBindingEntity::getNodeKey));
    // LinkedHashSet 保序去重：同一 workflowType::nodeKey 只出一次
    Set<String> seen = new LinkedHashSet<>();
    List<SkillWorkflowNodeView> out = new ArrayList<>();
    for (SkillNodeBindingEntity binding : bindings) {
      String key = binding.getWorkflowType() + "::" + binding.getNodeKey();
      if (!seen.add(key)) {
        continue;
      }
      out.add(viewMapper.workflowNodeView(binding.getWorkflowType(), binding.getNodeKey()));
    }
    // 库中尚无绑定时，给出课程/热点场景的默认节点，便于前端先选
    if (out.isEmpty()) {
      out.add(viewMapper.workflowNodeView("course_content_generation", "outline"));
      out.add(viewMapper.workflowNodeView("course_content_generation", "draft"));
      out.add(viewMapper.workflowNodeView("course_content_generation", "wechat"));
      out.add(viewMapper.workflowNodeView(HotNewsWorkflowKeys.SCENE, "draft"));
    }
    return out;
  }

  /**
   * 解析某工作流类型下节点的默认提示词：拼接所有启用 Skill 的 system/task 指令（可按 scene 过滤）。
   *
   * @param workflowType 工作流类型/场景码，用于与 Skill.scene 匹配；Skill 无 scene 则视为通用
   * @param nodeKey      节点键，仅在无指令可拼时写入回退文案
   * @return 拼接后的提示词；无任何指令时返回含 node/workflow 的占位串
   */
  @Override
  public String resolveNodePrompt(String workflowType, String nodeKey) {
    // 启用且未删的 Skill，按 priority 升序参与拼接
    List<SkillEntity> skills = skillMapper.selectList(new LambdaQueryWrapper<SkillEntity>()
        .isNull(SkillEntity::getDeletedAt)
        .eq(SkillEntity::getEnabled, true)
        .orderByAsc(SkillEntity::getPriority));
    StringBuilder sb = new StringBuilder();
    for (SkillEntity skill : skills) {
      // Skill 声明了 scene 时，必须与当前 workflowType 一致才纳入
      if (StrUtil.isNotBlank(skill.getScene()) && workflowType != null && !workflowType.equals(skill.getScene())) {
        continue;
      }
      if (StrUtil.isNotBlank(skill.getSystemInstructions())) {
        sb.append(skill.getSystemInstructions()).append('\n');
      }
      if (StrUtil.isNotBlank(skill.getTaskInstructions())) {
        sb.append(skill.getTaskInstructions()).append('\n');
      }
    }
    // 无可用指令时给调试友好的占位，避免空串
    return sb.isEmpty() ? "Node=" + nodeKey + " workflow=" + workflowType : sb.toString();
  }

  /**
   * 按节点 skillBindings（skillId + priority）组合 system/task 提示词。
   * <p>读取当前 skills 表指令（库内不做 skill 版本表）。
   *
   * @param bindings 节点上的 Skill 绑定列表；空或 null 返回空串；按 priority 升序，同 skillId 只取一次
   * @return 拼接并 trim 后的提示词全文
   */
  @Override
  public String composePromptFromBindings(List<SkillPromptBindingView> bindings) {
    if (ObjectUtil.isEmpty(bindings)) {
      return "";
    }
    // 按 priority 升序，保证低数字优先拼在前面
    List<SkillPromptBindingView> ordered = new ArrayList<>(bindings);
    ordered.sort((left, right) -> Integer.compare(bindingPriority(left), bindingPriority(right)));
    StringBuilder sb = new StringBuilder();
    // 同一 skillId 去重，避免重复指令
    Set<String> seen = new LinkedHashSet<>();
    for (SkillPromptBindingView binding : ordered) {
      // 显式 enabled=false 的绑定跳过
      if (binding == null || Boolean.FALSE.equals(binding.getEnabled())) {
        continue;
      }
      String skillId = binding.getSkillId() == null ? "" : binding.getSkillId();
      if (StrUtil.isBlank(skillId) || !seen.add(skillId)) {
        continue;
      }
      // 只使用当前表中启用且未删的 Skill
      SkillEntity skill = skillMapper.selectById(skillId);
      if (skill == null || skill.getDeletedAt() != null || Boolean.FALSE.equals(skill.getEnabled())) {
        continue;
      }
      if (StrUtil.isNotBlank(skill.getSystemInstructions())) {
        sb.append(skill.getSystemInstructions().trim()).append('\n');
      }
      if (StrUtil.isNotBlank(skill.getTaskInstructions())) {
        sb.append(skill.getTaskInstructions().trim()).append('\n');
      }
    }
    return sb.toString().trim();
  }

  /**
   * 读取绑定优先级；未设时默认 100（与实体默认一致）。
   *
   * @param binding 提示词绑定；调用方保证非 null
   * @return 用于排序的 priority 数值
   */
  private static int bindingPriority(SkillPromptBindingView binding) {
    return binding.getPriority() == null ? 100 : binding.getPriority();
  }

  /**
   * 将保存请求中的字段合并到实体。
   * <p>{@code creating=true} 时对缺省字段写入默认值；{@code false} 时仅覆盖请求中非 null 字段。
   *
   * @param entity   目标 Skill 实体（会被就地修改）
   * @param input    请求体字段来源
   * @param creating 是否处于创建路径（决定默认值策略）
   */
  private void applySkillFields(SkillEntity entity, SkillSaveRequest input, boolean creating) {
    // 名称：创建或缺省时兜底 unnamed
    if (creating || input.getName() != null || entity.getName() == null) {
      entity.setName(StrUtil.blankToDefault(input.getName(), "unnamed"));
    }
    if (creating || input.getDescription() != null) {
      entity.setDescription(input.getDescription());
    }
    if (creating || input.getScene() != null) {
      entity.setScene(input.getScene());
    }
    // scope 默认 scene
    if (creating || input.getScope() != null) {
      entity.setScope(ObjectUtil.defaultIfNull(input.getScope(), "scene"));
    } else if (entity.getScope() == null) {
      entity.setScope("scene");
    }
    // skillType 默认 instruction
    if (creating || input.getSkillType() != null) {
      entity.setSkillType(ObjectUtil.defaultIfNull(input.getSkillType(), "instruction"));
    } else if (entity.getSkillType() == null) {
      entity.setSkillType("instruction");
    }
    // locked 默认 false
    if (creating || input.getLocked() != null) {
      entity.setLocked(Boolean.TRUE.equals(input.getLocked()));
    } else if (entity.getLocked() == null) {
      entity.setLocked(false);
    }
    if (creating || input.getSystemInstructions() != null) {
      entity.setSystemInstructions(input.getSystemInstructions());
    }
    if (creating || input.getTaskInstructions() != null) {
      entity.setTaskInstructions(input.getTaskInstructions());
    }
    // priority 默认 100
    if (creating || input.getPriority() != null || entity.getPriority() == null) {
      entity.setPriority(input.getPriority() == null ? 100 : input.getPriority());
    }
    // enabled 默认 true（null 也视为启用）
    if (creating || input.getEnabled() != null || entity.getEnabled() == null) {
      entity.setEnabled(input.getEnabled() == null || Boolean.TRUE.equals(input.getEnabled()));
    }
  }

  /**
   * 全量替换某 Skill 的节点绑定：先删后插。
   *
   * @param skillId 所属 Skill 主键
   * @param list    新绑定列表；null 表示只清空不插入
   * @param now     写入 createdAt/updatedAt 的时间戳
   */
  private void replaceBindings(String skillId, List<SkillNodeBindingView> list, LocalDateTime now) {
    // 先删该 skill 下全部旧绑定，保证全量替换语义
    bindingMapper.delete(new LambdaQueryWrapper<SkillNodeBindingEntity>()
        .eq(SkillNodeBindingEntity::getSkillId, skillId));
    if (list == null) {
      return;
    }
    for (SkillNodeBindingView binding : list) {
      if (binding == null) {
        continue;
      }
      // 缺省 workflowType/nodeKey/priority/enabled 与产品默认一致
      SkillNodeBindingEntity entity = SkillNodeBindingEntity.builder()
          .id(StrUtil.isBlank(binding.getId()) ? IdsUtil.newId() : binding.getId())
          .skillId(skillId)
          .workflowType(StrUtil.blankToDefault(binding.getWorkflowType(), "course_content_generation"))
          .nodeKey(StrUtil.blankToDefault(binding.getNodeKey(), "draft"))
          .priority(binding.getPriority() == null ? 100 : binding.getPriority())
          .enabled(binding.getEnabled() == null || Boolean.TRUE.equals(binding.getEnabled()))
          .createdAt(now)
          .updatedAt(now)
          .build();
      bindingMapper.insert(entity);
    }
  }

  /**
   * 将 Skill 实体连同其节点绑定转为对外视图。
   *
   * @param skill Skill 实体
   * @return 含按 priority 排序绑定的 {@link SkillView}
   */
  private SkillView toView(SkillEntity skill) {
    // 绑定按 priority 升序，与列表展示一致
    List<SkillNodeBindingEntity> bindings = bindingMapper.selectList(new LambdaQueryWrapper<SkillNodeBindingEntity>()
        .eq(SkillNodeBindingEntity::getSkillId, skill.getId())
        .orderByAsc(SkillNodeBindingEntity::getPriority));
    return viewMapper.toView(skill, bindings, applicationsFor(skill.getId()));
  }

  /**
   * 对齐原版：仅统计正式启用、已发布、非 custom-* 工作流上的节点绑定。
   *
   * @param skillId Skill 主键
   * @return 应用位置列表
   */
  private List<SkillApplicationView> applicationsFor(String skillId) {
    if (skillId == null || skillId.isBlank()) {
      return List.of();
    }
    List<WorkflowDefinitionEntity> workflows = workflowDefinitionMapper.selectList(
        new LambdaQueryWrapper<WorkflowDefinitionEntity>()
            .eq(WorkflowDefinitionEntity::getStatus, "active")
            .isNotNull(WorkflowDefinitionEntity::getPublishedVersionId)
            .ne(WorkflowDefinitionEntity::getPublishedVersionId, "")
            .orderByAsc(WorkflowDefinitionEntity::getName));
    Map<String, WorkflowDefinitionEntity> byPublishedVersion = new HashMap<>();
    for (WorkflowDefinitionEntity workflow : workflows) {
      if (workflow.getWorkflowKey() != null && workflow.getWorkflowKey().startsWith("custom-")) {
        continue;
      }
      if (workflow.getPublishedVersionId() != null && !workflow.getPublishedVersionId().isBlank()) {
        byPublishedVersion.put(workflow.getPublishedVersionId(), workflow);
      }
    }
    if (byPublishedVersion.isEmpty()) {
      return List.of();
    }
    List<WorkflowVersionSkillBindingEntity> bindings = versionSkillBindingMapper.selectList(
        new LambdaQueryWrapper<WorkflowVersionSkillBindingEntity>()
            .eq(WorkflowVersionSkillBindingEntity::getSkillId, skillId)
            .in(WorkflowVersionSkillBindingEntity::getVersionId, byPublishedVersion.keySet())
            .orderByAsc(WorkflowVersionSkillBindingEntity::getNodeKey));
    if (bindings.isEmpty()) {
      return List.of();
    }
    Map<String, String> modeNames = loadModeNames();
    List<SkillApplicationView> out = new ArrayList<>();
    for (WorkflowVersionSkillBindingEntity binding : bindings) {
      WorkflowDefinitionEntity workflow = byPublishedVersion.get(binding.getVersionId());
      if (workflow == null) {
        continue;
      }
      String modeId = workflow.getModeId() == null ? "" : workflow.getModeId();
      String modeName = modeNames.getOrDefault(modeId, fallbackModeName(workflow));
      out.add(SkillApplicationView.builder()
          .modeId(modeId)
          .modeName(modeName)
          .workflowId(workflow.getId())
          .workflowName(workflow.getName() == null ? workflow.getWorkflowKey() : workflow.getName())
          .nodeKey(binding.getNodeKey())
          .nodeName(WorkflowNodeCatalogUtil.nodeDisplayName(binding.getNodeKey()))
          .bindingState("published")
          .build());
    }
    out.sort((left, right) -> {
      int byWorkflow = StrUtil.nullToEmpty(left.getWorkflowName())
          .compareTo(StrUtil.nullToEmpty(right.getWorkflowName()));
      if (byWorkflow != 0) {
        return byWorkflow;
      }
      return StrUtil.nullToEmpty(left.getNodeKey()).compareTo(StrUtil.nullToEmpty(right.getNodeKey()));
    });
    return out;
  }

  private Map<String, String> loadModeNames() {
    Map<String, String> names = new HashMap<>();
    try {
      for (ContentModeLookupMapper.ModeNameRow row : contentModeLookupMapper.listModeNames()) {
        if (row != null && row.getId() != null) {
          names.put(row.getId(), row.getName() == null ? row.getId() : row.getName());
        }
      }
    } catch (Exception ignored) {
      // content_modes 尚未迁移时退化为 scene 推断
    }
    return names;
  }

  private static String fallbackModeName(WorkflowDefinitionEntity workflow) {
    String scene = workflow.getScene() == null ? "" : workflow.getScene();
    if (scene.contains("hot_news") || scene.contains("hot-news")) {
      return "热点资讯模式";
    }
    if (scene.contains("course")) {
      return "课程体系教学模式";
    }
    return StrUtil.blankToDefault(workflow.getScene(), "未命名模式");
  }

  /**
   * 加载未软删的 Skill；不存在则抛 404 业务异常。
   *
   * @param id Skill 主键
   * @return 活跃 Skill 实体
   * @throws BusinessException 记录不存在或已软删
   */
  private SkillEntity requireActive(String id) {
    SkillEntity entity = skillMapper.selectById(id);
    if (entity == null || entity.getDeletedAt() != null) {
      throw new BusinessException("skill_not_found", "技能不存在", 404);
    }
    return entity;
  }
}
