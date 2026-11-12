package com.chengwenfang.knowledge.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/** knowledge_chat_messages 表 Mapper。 */
@Mapper
public interface KnowledgeChatMessageMapper extends BaseMapper<KnowledgeChatMessageEntity> {}
