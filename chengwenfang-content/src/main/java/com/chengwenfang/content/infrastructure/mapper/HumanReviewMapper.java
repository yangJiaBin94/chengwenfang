package com.chengwenfang.content.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.content.domain.entity.HumanReviewEntity;
import org.apache.ibatis.annotations.Mapper;

/** 人工审核表 Mapper。 */
@Mapper
public interface HumanReviewMapper extends BaseMapper<HumanReviewEntity> {}
