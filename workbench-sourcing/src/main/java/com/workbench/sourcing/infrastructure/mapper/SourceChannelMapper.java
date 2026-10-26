package com.workbench.sourcing.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.sourcing.domain.entity.SourceChannelEntity;
import org.apache.ibatis.annotations.Mapper;

/** 信源频道表 Mapper。 */
@Mapper
public interface SourceChannelMapper extends BaseMapper<SourceChannelEntity> {}
