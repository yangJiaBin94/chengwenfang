package com.chengwenfang.knowledge.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** knowledge_chunks 表 Mapper。 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {

  /**
   * FULLTEXT + 版本 + 可选来源/介质（CSV 空串表示不限；种类值不含逗号）。
   *
   * @param query           查询词
   * @param indexVersion    索引版本
   * @param sourceKindsCsv  来源 CSV；空不限
   * @param mediaKindsCsv   介质 CSV；空不限
   * @param limit           上限
   * @return 切块
   */
  @Select("""
      SELECT c.* FROM knowledge_chunks c
      INNER JOIN knowledge_documents d ON d.id = c.document_id
      WHERE c.index_version = #{indexVersion}
        AND MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
        AND (#{ownerUserId} = '' OR d.owner_user_id = #{ownerUserId})
        AND (#{sourceKindsCsv} = '' OR FIND_IN_SET(d.source_kind, #{sourceKindsCsv}))
        AND (#{mediaKindsCsv} = '' OR FIND_IN_SET(d.media_kind, #{mediaKindsCsv}))
      LIMIT #{limit}
      """)
  List<KnowledgeChunkEntity> searchFullText(
      @Param("query") String query,
      @Param("indexVersion") String indexVersion,
      @Param("ownerUserId") String ownerUserId,
      @Param("sourceKindsCsv") String sourceKindsCsv,
      @Param("mediaKindsCsv") String mediaKindsCsv,
      @Param("limit") int limit);

  /**
   * LIKE 回退 + 版本 + 可选来源/介质 CSV。
   *
   * @param query           查询词
   * @param indexVersion    索引版本
   * @param sourceKindsCsv  来源 CSV；空不限
   * @param mediaKindsCsv   介质 CSV；空不限
   * @param limit           上限
   * @return 切块
   */
  @Select("""
      SELECT c.* FROM knowledge_chunks c
      INNER JOIN knowledge_documents d ON d.id = c.document_id
      WHERE c.index_version = #{indexVersion}
        AND c.content LIKE CONCAT('%', #{query}, '%')
        AND (#{ownerUserId} = '' OR d.owner_user_id = #{ownerUserId})
        AND (#{sourceKindsCsv} = '' OR FIND_IN_SET(d.source_kind, #{sourceKindsCsv}))
        AND (#{mediaKindsCsv} = '' OR FIND_IN_SET(d.media_kind, #{mediaKindsCsv}))
      LIMIT #{limit}
      """)
  List<KnowledgeChunkEntity> searchKeyword(
      @Param("query") String query,
      @Param("indexVersion") String indexVersion,
      @Param("ownerUserId") String ownerUserId,
      @Param("sourceKindsCsv") String sourceKindsCsv,
      @Param("mediaKindsCsv") String mediaKindsCsv,
      @Param("limit") int limit);

  /**
   * 指定索引版本 + 所属用户下出现过切块的文档 ID（去重）。
   *
   * @param indexVersion 索引版本
   * @param ownerUserId  所属用户
   * @return 文档 ID 列表
   */
  @Select("""
      SELECT DISTINCT c.document_id
      FROM knowledge_chunks c
      INNER JOIN knowledge_documents d ON d.id = c.document_id
      WHERE c.index_version = #{indexVersion}
        AND d.owner_user_id = #{ownerUserId}
        AND c.document_id IS NOT NULL
        AND c.document_id != ''
      """)
  List<String> selectDistinctDocumentIdsByIndexVersionAndOwner(
      @Param("indexVersion") String indexVersion, @Param("ownerUserId") String ownerUserId);

  /**
   * 统计指定版本 + 所属用户下的切块数。
   *
   * @param indexVersion 索引版本
   * @param ownerUserId  所属用户
   * @return 切块数
   */
  @Select("""
      SELECT COUNT(1)
      FROM knowledge_chunks c
      INNER JOIN knowledge_documents d ON d.id = c.document_id
      WHERE c.index_version = #{indexVersion}
        AND d.owner_user_id = #{ownerUserId}
      """)
  Long countByIndexVersionAndOwner(
      @Param("indexVersion") String indexVersion, @Param("ownerUserId") String ownerUserId);

  /**
   * 删除指定版本 + 所属用户下的切块（蓝绿切流后 GC）。
   *
   * @param indexVersion 索引版本
   * @param ownerUserId  所属用户
   * @return 删除行数
   */
  @Delete("""
      DELETE c FROM knowledge_chunks c
      INNER JOIN knowledge_documents d ON d.id = c.document_id
      WHERE c.index_version = #{indexVersion}
        AND d.owner_user_id = #{ownerUserId}
      """)
  int deleteByIndexVersionAndOwner(
      @Param("indexVersion") String indexVersion, @Param("ownerUserId") String ownerUserId);

  /**
   * 所属文档已不存在的孤儿切块。
   *
   * @return 孤儿切块列表
   */
  @Select("""
      SELECT c.* FROM knowledge_chunks c
      LEFT JOIN knowledge_documents d ON d.id = c.document_id
      WHERE d.id IS NULL
      """)
  List<KnowledgeChunkEntity> selectOrphanChunks();
}
