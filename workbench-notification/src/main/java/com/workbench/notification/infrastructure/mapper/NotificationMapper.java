package com.workbench.notification.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.notification.domain.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;

/** 通知表 MyBatis Mapper。 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {}
