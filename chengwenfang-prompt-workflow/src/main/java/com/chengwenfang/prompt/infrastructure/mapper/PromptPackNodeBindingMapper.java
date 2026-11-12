package com.chengwenfang.prompt.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.prompt.domain.entity.PromptPackNodeBindingEntity;
import org.apache.ibatis.annotations.Mapper;

/** 提示词包 与工作流节点绑定表 MyBatis Mapper。 */
@Mapper
public interface PromptPackNodeBindingMapper extends BaseMapper<PromptPackNodeBindingEntity> {}
