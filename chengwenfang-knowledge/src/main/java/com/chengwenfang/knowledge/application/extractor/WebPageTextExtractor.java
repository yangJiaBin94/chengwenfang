package com.chengwenfang.knowledge.application.extractor;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeTextExtractor;
import org.springframework.stereotype.Component;

/** 网页正文抽取：直通连接器已拉取的 rawText。 */
@Component
public class WebPageTextExtractor implements KnowledgeTextExtractor {

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeMediaKind mediaKind) {
    return KnowledgeMediaKind.WEB_PAGE == mediaKind;
  }

  /** {@inheritDoc} */
  @Override
  public NormalizedText extract(KnowledgeIngestDraft draft) {
    String title = draft == null ? "" : StrUtil.blankToDefault(draft.getTitle(), "");
    String text = draft == null ? "" : StrUtil.nullToEmpty(draft.getRawText()).trim();
    KnowledgeChunkProfile profile = text.lines().anyMatch(line -> line.matches("^#{1,6}\\s+\\S.*"))
        ? KnowledgeChunkProfile.MARKDOWN_STRUCTURED
        : KnowledgeChunkProfile.PLAIN;
    return NormalizedText.builder().title(title).text(text).chunkProfile(profile).build();
  }
}
