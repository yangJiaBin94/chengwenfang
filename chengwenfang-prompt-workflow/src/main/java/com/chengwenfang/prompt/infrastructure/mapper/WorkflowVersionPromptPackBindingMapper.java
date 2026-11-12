package com.chengwenfang.prompt.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.prompt.domain.entity.WorkflowVersionPromptPackBindingEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流版本与 提示词包绑定快照表 MyBatis Mapper。 */
@Mapper
public interface WorkflowVersionPromptPackBindingMapper extends BaseMapper<WorkflowVersionPromptPackBindingEntity> {}
