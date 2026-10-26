package com.workbench.sourcing.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.sourcing.domain.entity.SourceItemEntity;
import org.apache.ibatis.annotations.Mapper;

/** 信源条目表 Mapper。 */
@Mapper
public interface SourceItemMapper extends BaseMapper<SourceItemEntity> {}
