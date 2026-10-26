package com.workbench.skill.application;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.workbench.skill.domain.entity.SkillEntity;
import com.workbench.skill.domain.entity.SkillNodeBindingEntity;
import com.workbench.skill.facade.dto.view.SkillApplicationView;
import com.workbench.skill.facade.dto.view.SkillNodeBindingView;
import com.workbench.skill.facade.dto.view.SkillView;
import com.workbench.skill.facade.dto.view.SkillWorkflowNodeView;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/** Skill / Binding 实体与 API 视图转换。 */
@Component
public class SkillViewMapper {

  /**
   * 绑定实体转视图。
   *
   * @param entity 绑定实体
   * @return 绑定视图；入参为空时返回 null
   */
  public SkillNodeBindingView toBindingView(SkillNodeBindingEntity entity) {
    if (entity == null) {
      return null;
    }
    return SkillNodeBindingView.builder()
        .id(entity.getId())
        .workflowType(entity.getWorkflowType())
        .nodeKey(entity.getNodeKey())
        .priority(entity.getPriority())
        .enabled(entity.getEnabled())
        .build();
  }

  /**
   * 实体转 Skill 视图（不含绑定与应用）。
   *
   * @param entity Skill 实体
   * @return Skill 视图；入参为空时返回 null
   */
  public SkillView toViewBase(SkillEntity entity) {
    if (entity == null) {
      return null;
    }
    return SkillView.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(StrUtil.nullToEmpty(entity.getDescription()))
        .scope(ObjectUtil.defaultIfNull(entity.getScope(), "scene"))
        .skillType(ObjectUtil.defaultIfNull(entity.getSkillType(), "instruction"))
        .locked(Boolean.TRUE.equals(entity.getLocked()))
        .scene(StrUtil.nullToEmpty(entity.getScene()))
        .systemInstructions(StrUtil.nullToEmpty(entity.getSystemInstructions()))
        .taskInstructions(StrUtil.nullToEmpty(entity.getTaskInstructions()))
        .priority(entity.getPriority())
        .enabled(entity.getEnabled())
        .updatedAt(dateTimeToString(entity.getUpdatedAt()))
        .build();
  }

  /**
   * 实体转完整 API 视图。
   *
   * @param skill        Skill 实体
   * @param bindings     节点绑定
   * @param applications 应用位置
   * @return 完整视图
   */
  public SkillView toView(
      SkillEntity skill,
      List<SkillNodeBindingEntity> bindings,
      List<SkillApplicationView> applications) {
    SkillView view = toViewBase(skill);
    if (view == null) {
      return null;
    }
    view.setNodeBindings(bindings == null
        ? List.of()
        : bindings.stream().map(this::toBindingView).toList());
    view.setApplications(applications == null ? List.of() : applications);
    return view;
  }

  /**
   * 构造可绑定工作流节点目录项。
   *
   * @param workflowType 工作流类型
   * @param nodeKey      节点 key
   * @return 目录项
   */
  public SkillWorkflowNodeView workflowNodeView(String workflowType, String nodeKey) {
    return SkillWorkflowNodeView.builder()
        .workflowType(workflowType)
        .nodeKey(nodeKey)
        .name(nodeKey)
        .group(workflowType)
        .modelType("image".equals(nodeKey) || StrUtil.contains(nodeKey, "image") ? "image" : "text")
        .build();
  }

  /**
   * LocalDateTime 转 ISO 字符串。
   *
   * @param value 时间
   * @return 字符串；空则 null
   */
  private static String dateTimeToString(LocalDateTime value) {
    return value == null ? null : value.toString();
  }
}
