package com.chengwenfang.knowledge.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.event.WorkOutputContentChangedEvent;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import com.chengwenfang.knowledge.facade.dto.request.RegisterKnowledgeDocumentRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 主轨正文变更后登记知识库文档并入队索引。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOutputContentChangedListener {

  /** 知识库门面。 */
  private final KnowledgeFacade knowledgeFacade;

  /**
   * 提交后联动知识库登记。
   *
   * @param event 正文变更事件
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWorkOutputContentChanged(WorkOutputContentChangedEvent event) {
    if (event == null || StrUtil.isBlank(event.getOutputId())) {
      return;
    }
    String versionStamp =
        event.getVersionNo() == null ? "" : String.valueOf(event.getVersionNo());
    RegisterKnowledgeDocumentRequest request = RegisterKnowledgeDocumentRequest.builder()
        .sourceKind(KnowledgeSourceKind.WORK_OUTPUT)
        .sourceRefId(event.getOutputId())
        .mediaKind(KnowledgeMediaKind.ARTICLE_MARKDOWN)
        .contentHash(event.getContentHash())
        .sourceVersionStamp(versionStamp)
        .build();
    knowledgeFacade.upsertSourceDocument(request);
    log.info("主轨变更已登记知识库 outputId={} version={}", event.getOutputId(), versionStamp);
  }
}
