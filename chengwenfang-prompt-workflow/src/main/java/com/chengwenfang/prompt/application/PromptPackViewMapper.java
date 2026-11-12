package com.chengwenfang.prompt.application;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.prompt.domain.entity.PromptPackEntity;
import com.chengwenfang.prompt.domain.entity.PromptPackNodeBindingEntity;
import com.chengwenfang.prompt.facade.dto.view.PromptPackApplicationView;
import com.chengwenfang.prompt.facade.dto.view.PromptPackNodeBindingView;
import com.chengwenfang.prompt.facade.dto.view.PromptPackView;
import com.chengwenfang.prompt.facade.dto.view.PromptPackWorkflowNodeView;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/** 提示词包 / Binding 实体与 API 视图转换。 */
@Component
public class PromptPackViewMapper {

  /**
   * 绑定实体转视图。
   *
   * @param entity 绑定实体
   * @return 绑定视图；入参为空时返回 null
   */
  public PromptPackNodeBindingView toBindingView(PromptPackNodeBindingEntity entity) {
    if (entity == null) {
      return null;
    }
    return PromptPackNodeBindingView.builder()
        .id(entity.getId())
        .workflowType(entity.getWorkflowType())
        .nodeKey(entity.getNodeKey())
        .priority(entity.getPriority())
        .enabled(entity.getEnabled())
        .build();
  }

  /**
   * 实体转 提示词包视图（不含绑定与应用）。
   *
   * @param entity 提示词包 实体
   * @return 提示词包视图；入参为空时返回 null
   */
  public PromptPackView toViewBase(PromptPackEntity entity) {
    if (entity == null) {
      return null;
    }
    return PromptPackView.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(StrUtil.nullToEmpty(entity.getDescription()))
        .scope(ObjectUtil.defaultIfNull(entity.getScope(), "scene"))
        .promptPackType(ObjectUtil.defaultIfNull(entity.getPromptPackType(), "instruction"))
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
   * @param promptPack        提示词包 实体
   * @param bindings     节点绑定
   * @param applications 应用位置
   * @return 完整视图
   */
  public PromptPackView toView(
      PromptPackEntity promptPack,
      List<PromptPackNodeBindingEntity> bindings,
      List<PromptPackApplicationView> applications) {
    PromptPackView view = toViewBase(promptPack);
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
  public PromptPackWorkflowNodeView workflowNodeView(String workflowType, String nodeKey) {
    return PromptPackWorkflowNodeView.builder()
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
