package com.chengwenfang.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.skill.domain.entity.WorkflowDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流定义表 MyBatis Mapper。 */
@Mapper
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinitionEntity> {}
