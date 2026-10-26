package com.workbench.skill.infrastructure.mapper;

import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 只读查询 content_modes 名称，供 Skill 应用位置展示（不对齐 content 模块依赖）。 */
@Mapper
public interface ContentModeLookupMapper {
  @Select("SELECT id, name FROM content_modes")
  List<ModeNameRow> listModeNames();

  /** id / name 投影。 */
  @Data
  class ModeNameRow {
    private String id;
    private String name;
  }
}
