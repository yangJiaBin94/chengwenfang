package com.workbench.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.skill.domain.entity.WorkflowVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流版本表 MyBatis Mapper。 */
@Mapper
public interface WorkflowVersionMapper extends BaseMapper<WorkflowVersionEntity> {}
