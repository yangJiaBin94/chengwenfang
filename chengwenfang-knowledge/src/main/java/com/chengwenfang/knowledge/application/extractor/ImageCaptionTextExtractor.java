package com.chengwenfang.knowledge.application.extractor;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeTextExtractor;
import org.springframework.stereotype.Component;

/**
 * 图片抽取：II-3 首版仅接受登记时提供的 caption/OCR 文本（rawText），不做自动 OCR。
 */
@Component
public class ImageCaptionTextExtractor implements KnowledgeTextExtractor {

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeMediaKind mediaKind) {
    return KnowledgeMediaKind.IMAGE == mediaKind;
  }

  /** {@inheritDoc} */
  @Override
  public NormalizedText extract(KnowledgeIngestDraft draft) {
    String title = draft == null ? "" : StrUtil.blankToDefault(draft.getTitle(), "");
    String text = draft == null ? "" : StrUtil.nullToEmpty(draft.getRawText()).trim();
    if (StrUtil.isBlank(text)) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_EXTRACT_EMPTY,
          "图片需提供标注或 OCR 文本后方可入库（自动 OCR 尚未开放）",
          400);
    }
    return NormalizedText.builder()
        .title(title)
        .text(text)
        .chunkProfile(KnowledgeChunkProfile.PLAIN)
        .build();
  }
}
