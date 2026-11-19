package com.chengwenfang.knowledge.application.extractor;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.FileView;
import com.chengwenfang.knowledge.application.ingest.KnowledgeUploadMediaResolver;
import com.chengwenfang.knowledge.domain.model.KnowledgeIngestDraft;
import com.chengwenfang.knowledge.domain.model.NormalizedText;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.spi.KnowledgeTextExtractor;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用文档抽取：docx/pdf/pptx/xlsx/csv；有 origin 二进制时优先重解析，避免二次索引丢失切块形态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTextExtractor implements KnowledgeTextExtractor {

  /** 需从二进制重解析以保留切块形态的扩展名。 */
  private static final Set<String> BINARY_EXTENSIONS = Set.of(
      "docx", "pdf", "pptx", "xlsx", "csv");

  /** 文件门面。 */
  private final FileFacade fileFacade;

  /** {@inheritDoc} */
  @Override
  public boolean supports(KnowledgeMediaKind mediaKind) {
    return KnowledgeMediaKind.DOCUMENT == mediaKind;
  }

  /** {@inheritDoc} */
  @Override
  public NormalizedText extract(KnowledgeIngestDraft draft) {
    String title = draft == null ? "" : StrUtil.blankToDefault(draft.getTitle(), "");
    String originFileId = draft == null ? null : draft.getOriginFileId();
    if (StrUtil.isNotBlank(originFileId)) {
      FileView meta = fileFacade.get(originFileId);
      String extension = KnowledgeUploadMediaResolver.extensionOf(
          StrUtil.blankToDefault(meta.getFileName(), title));
      if (BINARY_EXTENSIONS.contains(extension)) {
        return extractBinary(title, originFileId, extension);
      }
    }
    if (draft != null && StrUtil.isNotBlank(draft.getRawText())) {
      String text = draft.getRawText().trim();
      KnowledgeChunkProfile profile = textContainsAtx(text)
          ? KnowledgeChunkProfile.MARKDOWN_STRUCTURED
          : KnowledgeChunkProfile.PLAIN;
      return NormalizedText.builder().title(title).text(text).chunkProfile(profile).build();
    }
    throw new BusinessException(ErrorCodes.KNOWLEDGE_EXTRACT_EMPTY, "文档缺少可解析正文或原始文件", 400);
  }

  private NormalizedText extractBinary(String title, String originFileId, String extension) {
    byte[] bytes = fileFacade.getBytes(originFileId);
    try {
      DocumentBinaryParsers.ParsedDocument parsed = DocumentBinaryParsers.parse(extension, bytes);
      String text = StrUtil.nullToEmpty(parsed.text()).trim();
      if (StrUtil.isBlank(text)) {
        String message = "pdf".equals(extension)
            ? "扫描件或空 PDF 暂不支持"
            : "未能从文档中抽取可用正文";
        throw new BusinessException(ErrorCodes.KNOWLEDGE_EXTRACT_EMPTY, message, 400);
      }
      return NormalizedText.builder()
          .title(title)
          .text(text)
          .chunkProfile(parsed.chunkProfile())
          .build();
    } catch (BusinessException businessException) {
      throw businessException;
    } catch (IOException exception) {
      log.warn("文档解析失败 fileId={} ext={} err={}", originFileId, extension, exception.getMessage());
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_EXTRACT_EMPTY,
          "文档解析失败：" + StrUtil.blankToDefault(exception.getMessage(), "未知错误"),
          400);
    }
  }

  private static boolean textContainsAtx(String text) {
    return text != null && text.lines().anyMatch(line -> line.matches("^#{1,6}\\s+\\S.*"));
  }
}
