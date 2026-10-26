package com.workbench.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.skill.domain.entity.WorkflowRunEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流运行表 MyBatis Mapper。 */
@Mapper
public interface WorkflowRunMapper extends BaseMapper<WorkflowRunEntity> {}
