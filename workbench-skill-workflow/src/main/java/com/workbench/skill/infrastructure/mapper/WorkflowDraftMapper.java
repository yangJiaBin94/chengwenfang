package com.workbench.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.skill.domain.entity.WorkflowDraftEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流草稿表 MyBatis Mapper。 */
@Mapper
public interface WorkflowDraftMapper extends BaseMapper<WorkflowDraftEntity> {}
