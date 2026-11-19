package com.chengwenfang.knowledge.application.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.knowledge.facade.constant.KnowledgeChunkConstants;
import com.chengwenfang.knowledge.facade.enums.KnowledgeChunkProfile;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** {@link DocumentBinaryParsers} 清洗与 CSV 切块形态。 */
class DocumentBinaryParsersTest {

  @Test
  void cleanPdfTextMergesBrokenLinesAndDropsRepeatedHeaders() {
    String raw = "页眉\n正文第一行断\n行后续\n页眉\n第二段\n页眉\n";
    String cleaned = DocumentBinaryParsers.cleanPdfText(raw);
    assertThat(cleaned).contains("正文第一行断行后续");
    assertThat(cleaned).doesNotContain("页眉");
  }

  @Test
  void parseCsvUsesTableRowsProfileAndSheetMarker() throws Exception {
    String csv = "问题,答案\n什么是RAG,检索增强生成\n";
    DocumentBinaryParsers.ParsedDocument parsed =
        DocumentBinaryParsers.parse("csv", csv.getBytes(StandardCharsets.UTF_8));
    assertThat(parsed.chunkProfile()).isEqualTo(KnowledgeChunkProfile.TABLE_ROWS);
    assertThat(parsed.text()).contains("## " + KnowledgeChunkConstants.CSV_SHEET_NAME);
    assertThat(parsed.text()).contains("问题:什么是RAG");
    assertThat(parsed.text()).contains("答案:检索增强生成");
  }

  @Test
  void parseCsvRespectsQuotedCommas() throws Exception {
    String csv = "问题,答案\n\"你好,世界\",带逗号答案\n";
    DocumentBinaryParsers.ParsedDocument parsed =
        DocumentBinaryParsers.parse("csv", csv.getBytes(StandardCharsets.UTF_8));
    assertThat(parsed.text()).contains("问题:你好,世界");
    assertThat(parsed.text()).contains("答案:带逗号答案");
  }

  @Test
  void decodeCsvBytesStripsUtf8Bom() {
    byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    byte[] body = "问题,答案\nA,B\n".getBytes(StandardCharsets.UTF_8);
    byte[] bytes = new byte[bom.length + body.length];
    System.arraycopy(bom, 0, bytes, 0, bom.length);
    System.arraycopy(body, 0, bytes, bom.length, body.length);
    String decoded = DocumentBinaryParsers.decodeCsvBytes(bytes);
    assertThat(decoded).startsWith("问题");
    assertThat(decoded.charAt(0)).isNotEqualTo('\uFEFF');
  }

  @Test
  void decodeCsvBytesFallsBackToGb18030() {
    byte[] gbk = "姓名,年龄\n张三,18\n".getBytes(java.nio.charset.Charset.forName("GBK"));
    String decoded = DocumentBinaryParsers.decodeCsvBytes(gbk);
    assertThat(decoded).contains("姓名");
    assertThat(decoded).contains("张三");
  }

  @Test
  void parseCsvWithUtf8BomKeepsCleanHeaders() throws Exception {
    byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    byte[] body = "问题,答案\n什么是RAG,检索增强生成\n".getBytes(StandardCharsets.UTF_8);
    byte[] bytes = new byte[bom.length + body.length];
    System.arraycopy(bom, 0, bytes, 0, bom.length);
    System.arraycopy(body, 0, bytes, bom.length, body.length);
    DocumentBinaryParsers.ParsedDocument parsed = DocumentBinaryParsers.parse("csv", bytes);
    assertThat(parsed.text()).contains("问题:什么是RAG");
    assertThat(parsed.text()).doesNotContain("\uFEFF");
  }
}
