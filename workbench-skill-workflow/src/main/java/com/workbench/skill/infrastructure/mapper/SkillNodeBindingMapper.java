package com.workbench.skill.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.skill.domain.entity.SkillNodeBindingEntity;
import org.apache.ibatis.annotations.Mapper;

/** Skill 与工作流节点绑定表 MyBatis Mapper。 */
@Mapper
public interface SkillNodeBindingMapper extends BaseMapper<SkillNodeBindingEntity> {}
