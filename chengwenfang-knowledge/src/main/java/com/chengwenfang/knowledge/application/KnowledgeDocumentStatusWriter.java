package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.entity.KnowledgeDocumentEntity;
import com.chengwenfang.knowledge.facade.enums.KnowledgeDocumentStatus;
import com.chengwenfang.knowledge.infrastructure.mapper.KnowledgeDocumentMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档状态独立落库（失败标记不受外层事务回滚影响）。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentStatusWriter {

  /** 文档 Mapper。 */
  private final KnowledgeDocumentMapper documentMapper;

  /**
   * 在新事务中标记文档失败。
   *
   * @param documentId 文档 ID
   * @param message    失败原因
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(String documentId, String message) {
    if (StrUtil.isBlank(documentId)) {
      return;
    }
    KnowledgeDocumentEntity document = documentMapper.selectById(documentId);
    if (document == null) {
      return;
    }
    document.setStatus(KnowledgeDocumentStatus.FAILED.getValue());
    document.setErrorMessage(
        message != null && message.length() > 1000 ? message.substring(0, 1000) : message);
    document.setUpdatedAt(LocalDateTime.now());
    documentMapper.updateById(document);
  }
}
