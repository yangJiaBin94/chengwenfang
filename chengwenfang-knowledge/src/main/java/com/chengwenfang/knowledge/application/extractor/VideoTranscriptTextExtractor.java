package com.chengwenfang.knowledge.application.extractor;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeTextExtractor;
import org.springframework.stereotype.Component;

/** 视频转写文本抽取。 */
@Component
public class VideoTranscriptTextExtractor implements KnowledgeTextExtractor {

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeMediaKind mediaKind) {
    return KnowledgeMediaKind.VIDEO == mediaKind;
  }

  /** {@inheritDoc} */
  @Override
  public NormalizedText extract(KnowledgeIngestDraft draft) {
    String title = draft == null ? "" : StrUtil.blankToDefault(draft.getTitle(), "");
    String text = draft == null ? "" : StrUtil.nullToEmpty(draft.getRawText()).trim();
    return NormalizedText.builder()
        .title(title)
        .text(text)
        .chunkProfile(KnowledgeChunkProfile.PLAIN)
        .build();
  }
}
