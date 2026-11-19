package com.chengwenfang.knowledge.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.knowledge.domain.entity.KnowledgeMemoryEntity;
import org.apache.ibatis.annotations.Mapper;

/** 知识库长期记忆表。 */
@Mapper
public interface KnowledgeMemoryMapper extends BaseMapper<KnowledgeMemoryEntity> {}
