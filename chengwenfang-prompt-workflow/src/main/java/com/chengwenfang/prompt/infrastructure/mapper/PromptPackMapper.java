package com.chengwenfang.prompt.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.prompt.domain.entity.PromptPackEntity;
import org.apache.ibatis.annotations.Mapper;

/** 提示词包 定义表 MyBatis Mapper。 */
@Mapper
public interface PromptPackMapper extends BaseMapper<PromptPackEntity> {}
