package com.chengwenfang.knowledge.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengwenfang.knowledge.domain.entity.KnowledgeChunkEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** knowledge_chunks 表 Mapper。 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunkEntity> {

  /**
   * MySQL FULLTEXT 检索切块正文。
   *
   * @param query 查询词
   * @param limit 条数上限
   * @return 命中切块
   */
  @Select("SELECT * FROM knowledge_chunks WHERE MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) LIMIT #{limit}")
  List<KnowledgeChunkEntity> searchFullText(@Param("query") String query, @Param("limit") int limit);

  /**
   * 关键词 LIKE 回退检索（测试库无 FULLTEXT 时使用）。
   *
   * @param query 查询词
   * @param limit 条数上限
   * @return 命中切块
   */
  @Select("SELECT * FROM knowledge_chunks WHERE content LIKE CONCAT('%', #{query}, '%') LIMIT #{limit}")
  List<KnowledgeChunkEntity> searchKeyword(@Param("query") String query, @Param("limit") int limit);
}
