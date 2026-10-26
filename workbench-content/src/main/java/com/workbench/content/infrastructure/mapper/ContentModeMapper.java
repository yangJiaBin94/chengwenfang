package com.workbench.content.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.content.domain.entity.ContentModeEntity;
import org.apache.ibatis.annotations.Mapper;

/** content_modes 表 Mapper。 */
@Mapper
public interface ContentModeMapper extends BaseMapper<ContentModeEntity> {}
