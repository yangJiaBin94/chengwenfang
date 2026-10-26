package com.chengwenfang.file.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.file.domain.entity.StoredFileEntity;
import org.apache.ibatis.annotations.Mapper;

/** stored_files 表 Mapper。 */
@Mapper
public interface StoredFileMapper extends BaseMapper<StoredFileEntity> {}
