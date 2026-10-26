package com.chengwenfang.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.skill.domain.entity.WorkflowNodeRunEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流节点运行表 MyBatis Mapper。 */
@Mapper
public interface WorkflowNodeRunMapper extends BaseMapper<WorkflowNodeRunEntity> {}
