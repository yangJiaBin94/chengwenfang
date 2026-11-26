package com.chengwenfang.knowledge.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.domain.model.KnowledgeSourceKindCountRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 知识文档表访问。 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {

  /**
   * 按来源类型聚合文档数。
   *
   * @param ownerUserId 所属用户；空串表示不过滤（管理员或鉴权关闭）
   * @return 聚合行
   */
  @Select("""
      SELECT source_kind AS sourceKind, COUNT(*) AS cnt
      FROM knowledge_documents
      WHERE (#{ownerUserId} = '' OR owner_user_id = #{ownerUserId})
      GROUP BY source_kind
      """)
  List<KnowledgeSourceKindCountRow> countGroupBySourceKind(@Param("ownerUserId") String ownerUserId);
}
