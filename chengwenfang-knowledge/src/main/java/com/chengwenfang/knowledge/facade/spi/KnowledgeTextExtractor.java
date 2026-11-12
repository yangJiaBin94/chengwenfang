package com.chengwenfang.knowledge.facade.spi;

import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;

/**
 * 知识库文本抽取器：将来源草稿归一化为可切块纯文本。
 */
public interface KnowledgeTextExtractor {

  /**
   * 是否支持该介质种类。
   *
   * @param mediaKind 介质种类
   * @return 支持时 true
   */
  boolean supports(KnowledgeMediaKind mediaKind);

  /**
   * 从草稿抽取归一化正文。
   *
   * @param draft 来源草稿
   * @return 归一化文本
   */
  NormalizedText extract(KnowledgeIngestDraft draft);
}
