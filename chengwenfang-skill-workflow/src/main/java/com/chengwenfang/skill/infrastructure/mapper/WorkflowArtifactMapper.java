package com.chengwenfang.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.skill.domain.entity.WorkflowArtifactEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流节点产物表 MyBatis Mapper。 */
@Mapper
public interface WorkflowArtifactMapper extends BaseMapper<WorkflowArtifactEntity> {}
