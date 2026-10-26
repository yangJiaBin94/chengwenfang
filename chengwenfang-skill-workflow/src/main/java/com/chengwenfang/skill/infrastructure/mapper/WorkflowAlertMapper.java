package com.chengwenfang.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.skill.domain.entity.WorkflowAlertEntity;
import org.apache.ibatis.annotations.Mapper;

/** 工作流告警表 MyBatis Mapper。 */
@Mapper
public interface WorkflowAlertMapper extends BaseMapper<WorkflowAlertEntity> {}
