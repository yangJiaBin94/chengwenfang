package com.workbench.content.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.content.domain.entity.ArticleLayoutEntity;
import org.apache.ibatis.annotations.Mapper;

/** 文章排版表 Mapper。 */
@Mapper
public interface ArticleLayoutMapper extends BaseMapper<ArticleLayoutEntity> {}
