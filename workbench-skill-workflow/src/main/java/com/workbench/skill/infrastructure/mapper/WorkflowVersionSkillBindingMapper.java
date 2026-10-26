package com.workbench.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.skill.domain.entity.WorkflowVersionSkillBindingEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流版本与 Skill 绑定快照表 MyBatis Mapper。 */
@Mapper
public interface WorkflowVersionSkillBindingMapper extends BaseMapper<WorkflowVersionSkillBindingEntity> {}
