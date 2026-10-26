package com.workbench.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.skill.domain.entity.SkillEntity;
import org.apache.ibatis.annotations.Mapper;

/** Skill 定义表 MyBatis Mapper。 */
@Mapper
public interface SkillMapper extends BaseMapper<SkillEntity> {}
