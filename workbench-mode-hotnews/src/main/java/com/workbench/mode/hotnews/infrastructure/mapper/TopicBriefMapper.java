package com.workbench.mode.hotnews.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.mode.hotnews.domain.entity.TopicBriefEntity;
import org.apache.ibatis.annotations.Mapper;

/** 选题简报表 Mapper。 */
@Mapper
public interface TopicBriefMapper extends BaseMapper<TopicBriefEntity> {}
