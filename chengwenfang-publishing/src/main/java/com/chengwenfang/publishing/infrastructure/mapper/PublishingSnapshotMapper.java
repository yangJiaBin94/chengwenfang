package com.chengwenfang.publishing.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.publishing.domain.entity.PublishingSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;

/** 发布快照表 MyBatis Mapper。 */
@Mapper
public interface PublishingSnapshotMapper extends BaseMapper<PublishingSnapshotEntity> {}
