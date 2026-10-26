package com.chengwenfang.mode.hotnews.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.mode.hotnews.domain.entity.TopicSourceEntity;
import org.apache.ibatis.annotations.Mapper;

/** 选题信源关联表 Mapper。 */
@Mapper
public interface TopicSourceMapper extends BaseMapper<TopicSourceEntity> {}
