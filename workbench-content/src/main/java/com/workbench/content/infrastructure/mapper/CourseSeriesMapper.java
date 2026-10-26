package com.workbench.content.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.content.domain.entity.CourseSeriesEntity;
import org.apache.ibatis.annotations.Mapper;

/** 课程系列表 Mapper。 */
@Mapper
public interface CourseSeriesMapper extends BaseMapper<CourseSeriesEntity> {}
