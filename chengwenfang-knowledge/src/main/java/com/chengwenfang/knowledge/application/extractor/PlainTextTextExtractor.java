package com.chengwenfang.knowledge.application.extractor;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeTextExtractor;
import org.springframework.stereotype.Component;

/** 纯文本正文抽取：直通草稿 rawText。 */
@Component
public class PlainTextTextExtractor implements KnowledgeTextExtractor {

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeMediaKind mediaKind) {
    return KnowledgeMediaKind.PLAIN_TEXT == mediaKind;
  }

  /** {@inheritDoc} */
  @Override
  public NormalizedText extract(KnowledgeIngestDraft draft) {
    String title = draft == null ? "" : StrUtil.blankToDefault(draft.getTitle(), "");
    String text = draft == null ? "" : StrUtil.nullToEmpty(draft.getRawText());
    return NormalizedText.builder()
        .title(title)
        .text(text.trim())
        .chunkProfile(KnowledgeChunkProfile.PLAIN)
        .build();
  }
}
