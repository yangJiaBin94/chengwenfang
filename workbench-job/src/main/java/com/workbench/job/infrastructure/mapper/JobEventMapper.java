package com.workbench.job.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.job.domain.entity.JobEventEntity;
import org.apache.ibatis.annotations.Mapper;

/** job_events 表 Mapper。 */
@Mapper
public interface JobEventMapper extends BaseMapper<JobEventEntity> {}
