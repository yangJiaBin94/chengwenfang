package com.workbench.content.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.content.domain.entity.ContentThemeEntity;
import org.apache.ibatis.annotations.Mapper;

/** themes 表 Mapper。 */
@Mapper
public interface ContentThemeMapper extends BaseMapper<ContentThemeEntity> {}
