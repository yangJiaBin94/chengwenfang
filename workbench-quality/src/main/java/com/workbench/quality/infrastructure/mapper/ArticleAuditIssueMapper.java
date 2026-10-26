package com.workbench.quality.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.quality.domain.entity.ArticleAuditIssueEntity;
import org.apache.ibatis.annotations.Mapper;

/** {@code audit_issues} Mapper。 */
@Mapper
public interface ArticleAuditIssueMapper extends BaseMapper<ArticleAuditIssueEntity> {}
