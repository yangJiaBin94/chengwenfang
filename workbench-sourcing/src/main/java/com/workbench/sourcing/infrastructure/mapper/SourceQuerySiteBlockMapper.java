package com.workbench.sourcing.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.sourcing.domain.entity.SourceQuerySiteBlockEntity;
import org.apache.ibatis.annotations.Mapper;

/** 搜索词 × 网站拉黑表 Mapper。 */
@Mapper
public interface SourceQuerySiteBlockMapper extends BaseMapper<SourceQuerySiteBlockEntity> {}
