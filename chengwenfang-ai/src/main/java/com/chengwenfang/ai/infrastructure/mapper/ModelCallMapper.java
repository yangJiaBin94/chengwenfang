package com.chengwenfang.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.ai.domain.entity.ModelCallEntity;
import org.apache.ibatis.annotations.Mapper;

/** model_calls 表 Mapper。 */
@Mapper
public interface ModelCallMapper extends BaseMapper<ModelCallEntity> {}
