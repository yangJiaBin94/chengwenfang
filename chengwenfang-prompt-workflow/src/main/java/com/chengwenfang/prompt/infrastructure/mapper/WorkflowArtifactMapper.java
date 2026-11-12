package com.chengwenfang.prompt.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.prompt.domain.entity.WorkflowArtifactEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流节点产物表 MyBatis Mapper。 */
@Mapper
public interface WorkflowArtifactMapper extends BaseMapper<WorkflowArtifactEntity> {}
