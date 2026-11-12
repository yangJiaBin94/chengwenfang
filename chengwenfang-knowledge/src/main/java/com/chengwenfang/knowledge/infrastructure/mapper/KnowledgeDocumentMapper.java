package com.chengwenfang.knowledge.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;

/** knowledge_documents 表 Mapper。 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {}
