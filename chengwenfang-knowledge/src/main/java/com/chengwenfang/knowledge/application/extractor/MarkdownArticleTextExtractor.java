package com.chengwenfang.knowledge.application.extractor;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeTextExtractor;
import org.springframework.stereotype.Component;

/** Markdown 文章正文抽取：直通草稿中的 rawText。 */
@Component
public class MarkdownArticleTextExtractor implements KnowledgeTextExtractor {

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeMediaKind mediaKind) {
    return KnowledgeMediaKind.ARTICLE_MARKDOWN == mediaKind;
  }

  /** {@inheritDoc} */
  @Override
  public NormalizedText extract(KnowledgeIngestDraft draft) {
    String title = draft == null ? "" : StrUtil.blankToDefault(draft.getTitle(), "");
    String text = draft == null ? "" : StrUtil.nullToEmpty(draft.getRawText());
    return NormalizedText.builder()
        .title(title)
        .text(text.trim())
        .build();
  }
}
