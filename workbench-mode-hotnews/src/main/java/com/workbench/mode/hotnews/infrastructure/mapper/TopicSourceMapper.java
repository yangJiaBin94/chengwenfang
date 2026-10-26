package com.workbench.mode.hotnews.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.mode.hotnews.domain.entity.TopicSourceEntity;
import org.apache.ibatis.annotations.Mapper;

/** 选题信源关联表 Mapper。 */
@Mapper
public interface TopicSourceMapper extends BaseMapper<TopicSourceEntity> {}
