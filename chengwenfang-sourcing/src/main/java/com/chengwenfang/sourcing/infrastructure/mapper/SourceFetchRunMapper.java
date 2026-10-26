package com.chengwenfang.sourcing.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.sourcing.domain.entity.SourceFetchRunEntity;
import org.apache.ibatis.annotations.Mapper;

/** 信源抓取运行表 Mapper。 */
@Mapper
public interface SourceFetchRunMapper extends BaseMapper<SourceFetchRunEntity> {}
