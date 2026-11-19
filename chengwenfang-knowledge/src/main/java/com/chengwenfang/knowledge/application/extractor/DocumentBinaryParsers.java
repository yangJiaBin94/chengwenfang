package com.chengwenfang.knowledge.application.extractor;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReadConfig;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

/**
 * 二进制文档解析：docx / pdf / pptx / xlsx / csv → 归一化文本与切块形态。
 */
final class DocumentBinaryParsers {

  private static final Pattern ATX_HEADING = Pattern.compile("(?m)^#{1,6}\\s+\\S");
  private static final DataFormatter DATA_FORMATTER = new DataFormatter(Locale.ROOT);

  private DocumentBinaryParsers() {}

  /**
   * 解析结果。
   *
   * @param text         归一化正文
   * @param chunkProfile 切块形态
   */
  record ParsedDocument(String text, KnowledgeChunkProfile chunkProfile) {}

  /**
   * 按扩展名解析字节。
   *
   * @param extension 小写扩展名
   * @param bytes     文件字节
   * @return 解析结果；无法解析时 text 为空
   */
  static ParsedDocument parse(String extension, byte[] bytes) throws IOException {
    if (bytes == null || bytes.length == 0) {
      return new ParsedDocument("", KnowledgeChunkProfile.PLAIN);
    }
    return switch (StrUtil.nullToEmpty(extension).toLowerCase(Locale.ROOT)) {
      case "docx" -> parseDocx(bytes);
      case "pdf" -> parsePdf(bytes);
      case "pptx" -> parsePptx(bytes);
      case "xlsx" -> parseXlsx(bytes);
      case "csv" -> parseCsv(bytes);
      default -> new ParsedDocument("", KnowledgeChunkProfile.PLAIN);
    };
  }

  private static ParsedDocument parseDocx(byte[] bytes) throws IOException {
    StringBuilder markdown = new StringBuilder();
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      XWPFStyles styles = document.getStyles();
      for (XWPFParagraph paragraph : document.getParagraphs()) {
        String text = StrUtil.trim(paragraph.getText());
        if (StrUtil.isBlank(text)) {
          continue;
        }
        int headingLevel = headingLevel(paragraph.getStyle(), styles);
        if (headingLevel > 0) {
          markdown.append("#".repeat(headingLevel)).append(' ').append(text).append("\n\n");
        } else {
          markdown.append(text).append("\n\n");
        }
      }
      for (XWPFTable table : document.getTables()) {
        appendMarkdownTable(markdown, table);
      }
    }
    String text = markdown.toString().trim();
    return new ParsedDocument(text, KnowledgeChunkProfile.MARKDOWN_STRUCTURED);
  }

  private static int headingLevel(String styleId, XWPFStyles styles) {
    if (StrUtil.isBlank(styleId)) {
      return 0;
    }
    String normalized = styleId.toLowerCase(Locale.ROOT);
    if (normalized.startsWith("heading")) {
      String digits = normalized.replaceAll("\\D+", "");
      if (StrUtil.isNotBlank(digits)) {
        try {
          int level = Integer.parseInt(digits);
          return Math.min(6, Math.max(1, level));
        } catch (NumberFormatException ignored) {
          return 1;
        }
      }
      return 1;
    }
    if (styles != null) {
      XWPFStyle style = styles.getStyle(styleId);
      if (style != null && style.getName() != null) {
        return headingLevel(style.getName().replace(" ", ""), null);
      }
    }
    return 0;
  }

  private static void appendMarkdownTable(StringBuilder markdown, XWPFTable table) {
    List<List<String>> rows = new ArrayList<>();
    for (XWPFTableRow row : table.getRows()) {
      List<String> cells = new ArrayList<>();
      for (XWPFTableCell cell : row.getTableCells()) {
        cells.add(StrUtil.nullToEmpty(cell.getText()).replace('|', '/').trim());
      }
      if (!cells.isEmpty()) {
        rows.add(cells);
      }
    }
    if (rows.isEmpty()) {
      return;
    }
    int width = rows.stream().mapToInt(List::size).max().orElse(0);
    if (width <= 0) {
      return;
    }
    markdown.append('\n');
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      List<String> row = rows.get(rowIndex);
      markdown.append('|');
      for (int column = 0; column < width; column++) {
        String cell = column < row.size() ? row.get(column) : "";
        markdown.append(' ').append(cell).append(" |");
      }
      markdown.append('\n');
      if (rowIndex == 0) {
        markdown.append('|');
        for (int column = 0; column < width; column++) {
          markdown.append(" --- |");
        }
        markdown.append('\n');
      }
    }
    markdown.append('\n');
  }

  private static ParsedDocument parsePdf(byte[] bytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      String raw = StrUtil.nullToEmpty(stripper.getText(document));
      String cleaned = cleanPdfText(raw);
      KnowledgeChunkProfile profile = ATX_HEADING.matcher(cleaned).find()
          ? KnowledgeChunkProfile.MARKDOWN_STRUCTURED
          : KnowledgeChunkProfile.PLAIN;
      return new ParsedDocument(cleaned, profile);
    }
  }

  /**
   * 合并断行并去掉明显重复的短页眉页脚行。
   *
   * @param raw PDF 抽文本
   * @return 清洗后文本
   */
  static String cleanPdfText(String raw) {
    if (StrUtil.isBlank(raw)) {
      return "";
    }
    String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n");
    Map<String, Integer> shortLineCounts = new LinkedHashMap<>();
    for (String line : lines) {
      String trimmed = StrUtil.trim(line);
      if (trimmed.length() > 0 && trimmed.length() <= 40) {
        shortLineCounts.merge(trimmed, 1, Integer::sum);
      }
    }
    StringBuilder cleaned = new StringBuilder();
    StringBuilder paragraph = new StringBuilder();
    for (String line : lines) {
      String trimmed = StrUtil.trim(line);
      if (StrUtil.isBlank(trimmed)) {
        flushParagraph(cleaned, paragraph);
        continue;
      }
      if (trimmed.length() <= 40 && shortLineCounts.getOrDefault(trimmed, 0) >= 3) {
        continue;
      }
      if (!paragraph.isEmpty()) {
        char last = paragraph.charAt(paragraph.length() - 1);
        if (Character.isLetterOrDigit(last) && Character.isLetterOrDigit(trimmed.charAt(0))) {
          paragraph.append(trimmed);
        } else {
          paragraph.append(' ').append(trimmed);
        }
      } else {
        paragraph.append(trimmed);
      }
    }
    flushParagraph(cleaned, paragraph);
    return cleaned.toString().trim();
  }

  private static void flushParagraph(StringBuilder cleaned, StringBuilder paragraph) {
    if (paragraph.isEmpty()) {
      return;
    }
    if (!cleaned.isEmpty()) {
      cleaned.append("\n\n");
    }
    cleaned.append(paragraph);
    paragraph.setLength(0);
  }

  private static ParsedDocument parsePptx(byte[] bytes) throws IOException {
    StringBuilder markdown = new StringBuilder();
    try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
      int slideNumber = 1;
      for (XSLFSlide slide : show.getSlides()) {
        StringBuilder slideBody = new StringBuilder();
        for (XSLFShape shape : slide.getShapes()) {
          if (shape instanceof XSLFTextShape textShape) {
            String text = StrUtil.trim(textShape.getText());
            if (StrUtil.isNotBlank(text)) {
              slideBody.append(text).append("\n\n");
            }
          }
        }
        String notes = readSlideNotes(slide);
        if (StrUtil.isNotBlank(notes)) {
          slideBody.append("备注:\n").append(notes).append("\n\n");
        }
        String body = slideBody.toString().trim();
        if (StrUtil.isBlank(body)) {
          // 空页不写入，避免无内容块
          slideNumber++;
          continue;
        }
        markdown.append("## 第 ").append(slideNumber).append(" 页\n\n");
        markdown.append(body).append("\n\n");
        slideNumber++;
      }
    }
    return new ParsedDocument(markdown.toString().trim(), KnowledgeChunkProfile.SLIDE);
  }

  /**
   * 读取幻灯备注；不可用时返回空串。
   *
   * @param slide 幻灯页
   * @return 备注正文
   */
  private static String readSlideNotes(XSLFSlide slide) {
    try {
      XSLFNotes notes = slide.getNotes();
      if (notes == null) {
        return "";
      }
      StringBuilder notesText = new StringBuilder();
      for (XSLFShape shape : notes.getShapes()) {
        if (shape instanceof XSLFTextShape textShape) {
          String text = StrUtil.trim(textShape.getText());
          if (StrUtil.isNotBlank(text)) {
            if (!notesText.isEmpty()) {
              notesText.append('\n');
            }
            notesText.append(text);
          }
        }
      }
      return notesText.toString().trim();
    } catch (RuntimeException ignored) {
      return "";
    }
  }

  private static ParsedDocument parseXlsx(byte[] bytes) throws IOException {
    StringBuilder rowsText = new StringBuilder();
    int rowCount = 0;
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      for (Sheet sheet : workbook) {
        String sheetName = StrUtil.blankToDefault(sheet.getSheetName(), "Sheet");
        List<String> headers = null;
        boolean sheetHeaderWritten = false;
        for (Row row : sheet) {
          if (row == null) {
            continue;
          }
          List<String> values = new ArrayList<>();
          short lastCell = row.getLastCellNum();
          for (int index = 0; index < lastCell; index++) {
            Cell cell = row.getCell(index);
            values.add(cell == null ? "" : StrUtil.trim(DATA_FORMATTER.formatCellValue(cell)));
          }
          if (values.stream().allMatch(StrUtil::isBlank)) {
            continue;
          }
          // 契约：每个 sheet 首个非空行视为表头（不入库）；无表头的表需自补一行列名
          if (headers == null) {
            headers = values;
            continue;
          }
          String rowLine = formatTableRow(headers, values);
          if (StrUtil.isBlank(rowLine)) {
            continue;
          }
          rowCount++;
          assertTableRowLimit(rowCount);
          if (!sheetHeaderWritten) {
            if (!rowsText.isEmpty()) {
              rowsText.append("\n\n");
            }
            rowsText.append("## ").append(sheetName);
            sheetHeaderWritten = true;
          }
          rowsText.append("\n\n").append(rowLine);
        }
      }
    }
    return new ParsedDocument(rowsText.toString().trim(), KnowledgeChunkProfile.TABLE_ROWS);
  }

  private static ParsedDocument parseCsv(byte[] bytes) throws IOException {
    String content = decodeCsvBytes(bytes);
    CsvReadConfig config = CsvReadConfig.defaultConfig();
    config.setContainsHeader(false);
    CsvData data;
    try (CsvReader reader = CsvUtil.getReader(config)) {
      data = reader.read(new StringReader(content));
    }
    List<CsvRow> rows = data == null ? List.of() : data.getRows();
    if (rows.isEmpty()) {
      return new ParsedDocument("", KnowledgeChunkProfile.TABLE_ROWS);
    }
    List<String> headers = stripBomCells(rows.get(0).getRawList());
    StringBuilder rowsText = new StringBuilder();
    rowsText.append("## ").append(KnowledgeChunkConstants.CSV_SHEET_NAME);
    int rowCount = 0;
    for (int lineIndex = 1; lineIndex < rows.size(); lineIndex++) {
      List<String> values = rows.get(lineIndex).getRawList();
      String rowLine = formatTableRow(headers, values);
      if (StrUtil.isBlank(rowLine)) {
        continue;
      }
      rowCount++;
      assertTableRowLimit(rowCount);
      rowsText.append("\n\n").append(rowLine);
    }
    return new ParsedDocument(rowsText.toString().trim(), KnowledgeChunkProfile.TABLE_ROWS);
  }

  /**
   * CSV 字节解码：识别 UTF-8 BOM；严格 UTF-8 失败则回退 GB18030（覆盖常见 Windows 导出）。
   *
   * @param bytes 原始字节
   * @return 文本
   */
  static String decodeCsvBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    int offset = 0;
    if (bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xEF
        && (bytes[1] & 0xFF) == 0xBB
        && (bytes[2] & 0xFF) == 0xBF) {
      offset = 3;
    }
    byte[] payload = offset == 0 ? bytes : java.util.Arrays.copyOfRange(bytes, offset, bytes.length);
    if (offset > 0) {
      return new String(payload, StandardCharsets.UTF_8);
    }
    CharsetDecoder utf8Decoder = StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return utf8Decoder.decode(ByteBuffer.wrap(payload)).toString();
    } catch (CharacterCodingException ignored) {
      return new String(payload, Charset.forName("GB18030"));
    }
  }

  private static List<String> stripBomCells(List<String> cells) {
    if (cells == null || cells.isEmpty()) {
      return cells == null ? List.of() : cells;
    }
    List<String> cleaned = new ArrayList<>(cells.size());
    for (int index = 0; index < cells.size(); index++) {
      String cell = StrUtil.nullToEmpty(cells.get(index));
      if (index == 0 && !cell.isEmpty() && cell.charAt(0) == '\uFEFF') {
        cell = cell.substring(1);
      }
      cleaned.add(cell);
    }
    return cleaned;
  }

  /**
   * 将表头与单元格格式化为「列:值」行。
   *
   * @param headers 表头
   * @param values  单元格
   * @return 行文本；全空则空串
   */
  private static String formatTableRow(List<String> headers, List<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    List<String> pairs = new ArrayList<>();
    for (int index = 0; index < values.size(); index++) {
      String header =
          headers != null && index < headers.size()
              ? headers.get(index)
              : ("列" + (index + 1));
      String value = StrUtil.trim(values.get(index));
      if (StrUtil.isBlank(value)) {
        continue;
      }
      pairs.add(StrUtil.blankToDefault(header, "列" + (index + 1)) + ":" + value);
    }
    return pairs.isEmpty() ? "" : String.join("；", pairs);
  }

  private static void assertTableRowLimit(int rowCount) {
    if (rowCount > KnowledgeChunkConstants.MAX_TABLE_ROWS) {
      throw new BusinessException(
          ErrorCodes.KNOWLEDGE_TABLE_TOO_LARGE,
          "表格行数超过上限 " + KnowledgeChunkConstants.MAX_TABLE_ROWS,
          400);
    }
  }
}
