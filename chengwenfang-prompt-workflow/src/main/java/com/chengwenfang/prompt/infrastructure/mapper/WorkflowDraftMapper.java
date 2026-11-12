package com.chengwenfang.prompt.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.prompt.domain.entity.WorkflowDraftEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流草稿表 MyBatis Mapper。 */
@Mapper
public interface WorkflowDraftMapper extends BaseMapper<WorkflowDraftEntity> {}
