package com.chengwenfang.knowledge.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.knowledge.domain.entity.KnowledgeAnswerFeedbackEntity;
import org.apache.ibatis.annotations.Mapper;

/** 答案反馈 Mapper。 */
@Mapper
public interface KnowledgeAnswerFeedbackMapper extends BaseMapper<KnowledgeAnswerFeedbackEntity> {}
