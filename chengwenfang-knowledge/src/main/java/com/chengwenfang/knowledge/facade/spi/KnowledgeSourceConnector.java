package com.chengwenfang.knowledge.facade.spi;

import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import java.util.List;

/**
 * 知识库来源连接器：从业务域拉取待索引草稿。
 */
public interface KnowledgeSourceConnector {

  /**
   * 是否支持该来源种类。
   *
   * @param sourceKind 来源种类
   * @return 支持时 true
   */
  boolean supports(KnowledgeSourceKind sourceKind);

  /**
   * 按来源主键加载最新可索引草稿。
   *
   * @param sourceRefId 来源侧主键
   * @return 入库草稿；缺失或不可索引时由实现返回 null
   */
  KnowledgeIngestDraft load(String sourceRefId);

  /**
   * 列出宜全量重建索引的来源主键列表。
   *
   * @return 来源主键列表
   */
  List<String> listForReindex();
}
