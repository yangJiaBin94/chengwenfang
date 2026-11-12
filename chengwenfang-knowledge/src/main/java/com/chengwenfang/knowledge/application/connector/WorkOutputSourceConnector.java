package com.chengwenfang.knowledge.application.connector;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.dto.view.WorkOutputKnowledgeSnapshot;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeSourceConnector;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 作品内容轨知识来源连接器。 */
@Component
@RequiredArgsConstructor
public class WorkOutputSourceConnector implements KnowledgeSourceConnector {

  private final ContentFacade contentFacade;

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeSourceKind sourceKind) {
    return KnowledgeSourceKind.WORK_OUTPUT == sourceKind;
  }

  /** {@inheritDoc} */
  @Override
  public KnowledgeIngestDraft load(String sourceRefId) {
    if (StrUtil.isBlank(sourceRefId)) {
      return null;
    }
    WorkOutputKnowledgeSnapshot snapshot = contentFacade.loadMasterKnowledgeSnapshot(sourceRefId);
    if (snapshot == null) {
      return null;
    }
    return toDraft(snapshot);
  }

  /** {@inheritDoc} */
  @Override
  public List<String> listForReindex() {
    List<WorkOutputKnowledgeSnapshot> snapshots = contentFacade.listMasterKnowledgeSnapshots();
    List<String> sourceRefIds = new ArrayList<>();
    for (WorkOutputKnowledgeSnapshot snapshot : snapshots) {
      if (snapshot != null && StrUtil.isNotBlank(snapshot.getOutputId())) {
        sourceRefIds.add(snapshot.getOutputId());
      }
    }
    return sourceRefIds;
  }

  /**
   * 将内容快照转为入库草稿。
   *
   * @param snapshot 主轨快照
   * @return 草稿
   */
  private static KnowledgeIngestDraft toDraft(WorkOutputKnowledgeSnapshot snapshot) {
    String versionStamp =
        snapshot.getCurrentVersionNo() == null ? "" : String.valueOf(snapshot.getCurrentVersionNo());
    return KnowledgeIngestDraft.builder()
        .sourceKind(KnowledgeSourceKind.WORK_OUTPUT)
        .sourceRefId(snapshot.getOutputId())
        .mediaKind(KnowledgeMediaKind.ARTICLE_MARKDOWN)
        .title(StrUtil.blankToDefault(snapshot.getTitle(), ""))
        .themeId(snapshot.getThemeId())
        .contentHash(snapshot.getContentHash())
        .sourceVersionStamp(versionStamp)
        .deepLink(snapshot.getDeepLink())
        .rawText(snapshot.getContent())
        .originFileId(null)
        .build();
  }
}
