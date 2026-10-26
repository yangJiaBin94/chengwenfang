package com.workbench.mode.hotnews.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.mode.hotnews.domain.entity.TopicClusterEntity;
import org.apache.ibatis.annotations.Mapper;

/** 选题聚类表 Mapper。 */
@Mapper
public interface TopicClusterMapper extends BaseMapper<TopicClusterEntity> {}
