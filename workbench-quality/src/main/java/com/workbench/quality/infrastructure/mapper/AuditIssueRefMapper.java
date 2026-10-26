package com.workbench.quality.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workbench.quality.domain.entity.AuditIssueRefEntity;
import org.apache.ibatis.annotations.Mapper;

/** {@code audit_issue_refs} Mapper。 */
@Mapper
public interface AuditIssueRefMapper extends BaseMapper<AuditIssueRefEntity> {}
