package com.chengwenfang.ai.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.ai.domain.entity.AiProviderConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/** ai_provider_configs 表 Mapper。 */
@Mapper
public interface AiProviderConfigMapper extends BaseMapper<AiProviderConfigEntity> {}
