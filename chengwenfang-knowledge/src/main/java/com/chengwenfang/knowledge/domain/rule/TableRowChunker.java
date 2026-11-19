package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkPiece;
import com.chengwenfang.knowledge.domain.model.KnowledgeChunkRequest;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import com.chengwenfang.knowledge.facade.spi.KnowledgeChunker;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 表格行切块：空行分隔的每一行单独成块；{@code ## sheet} 行更新 headingPath；禁止滑窗横切行。
 */
@Component
public class TableRowChunker implements KnowledgeChunker {

  private static final Pattern SHEET_HEADING = Pattern.compile("^##\\s+(.+?)\\s*$");

  @Override
  public boolean supports(KnowledgeChunkProfile chunkProfile) {
    return KnowledgeChunkProfile.TABLE_ROWS == chunkProfile;
  }

  @Override
  public List<KnowledgeChunkPiece> chunk(KnowledgeChunkRequest request) {
    String title = request == null ? "" : StrUtil.nullToEmpty(request.getTitle());
    String body = request == null ? "" : StrUtil.nullToEmpty(request.getText());
    if (StrUtil.isBlank(body)) {
      return List.of(piece(title, "", "", 0, 0, 0));
    }
    String[] blocks = body.split("\\n\\s*\\n");
    List<KnowledgeChunkPiece> pieces = new ArrayList<>();
    String currentSheet = "";
    int cursor = 0;
    for (String block : blocks) {
      int found = body.indexOf(block, cursor);
      if (found < 0) {
        found = cursor;
      }
      String content = StrUtil.trim(block);
      cursor = found + block.length();
      if (StrUtil.isBlank(content)) {
        continue;
      }
      Matcher sheetMatcher = SHEET_HEADING.matcher(content);
      // 解析器写出的「## Sheet」单行：无中英文冒号/分号才当表名，避免吞掉数据行
      if (sheetMatcher.matches()
          && !content.contains(":")
          && !content.contains("：")
          && !content.contains("；")
          && !content.contains(";")) {
        currentSheet = StrUtil.trim(sheetMatcher.group(1));
        continue;
      }
      if (pieces.size() >= KnowledgeChunkConstants.MAX_TABLE_ROWS) {
        throw new BusinessException(
            ErrorCodes.KNOWLEDGE_TABLE_TOO_LARGE,
            "表格行数超过上限 " + KnowledgeChunkConstants.MAX_TABLE_ROWS,
            400);
      }
      int start = found + Math.max(0, block.indexOf(content));
      int end = start + content.length();
      pieces.add(piece(title, currentSheet, content, pieces.size(), start, end));
    }
    if (pieces.isEmpty()) {
      String trimmed = body.trim();
      int start = body.indexOf(trimmed);
      if (start < 0) {
        start = 0;
      }
      return List.of(piece(title, "", trimmed, 0, start, start + trimmed.length()));
    }
    return pieces;
  }

  private static KnowledgeChunkPiece piece(
      String title,
      String sheet,
      String content,
      int index,
      int start,
      int end) {
    String safeContent = StrUtil.nullToEmpty(content);
    String path = StrUtil.nullToEmpty(sheet);
    return KnowledgeChunkPiece.builder()
        .chunkIndex(index)
        .headingPath(path)
        .content(safeContent)
        .tokenEstimate(safeContent.replaceAll("\\s+", "").length())
        .embeddingText(ChunkEmbeddingTexts.build(title, path, safeContent))
        .charSpanStart(start)
        .charSpanEnd(end)
        .build();
  }
}
