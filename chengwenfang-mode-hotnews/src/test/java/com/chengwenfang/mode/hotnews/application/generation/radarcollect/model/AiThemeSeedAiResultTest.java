package com.chengwenfang.mode.hotnews.application.generation.radarcollect.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.application.generation.radarcollect.RadarCollectAiSupport;
import com.chengwenfang.common.LlmJsonSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AI 主题种子 JSON 解析。 */
class AiThemeSeedAiResultTest {

  @Test
  void parseObject_readsTitleSummarySignals() {
    String json = """
        {"title":"测试选题","summary":"速览正文不少于要求","signals":["角度A","角度B"],"angle":"从读者痛点切入"}
        """;
    AiThemeSeedAiResult result = LlmJsonSupport.parseObject(json, AiThemeSeedAiResult.class);
    assertThat(result).isNotNull();
    assertThat(result.getTitle()).isEqualTo("测试选题");
    assertThat(result.getSummary()).contains("速览");
    assertThat(result.getSignals()).containsExactly("角度A", "角度B");
    assertThat(result.getAngle()).isEqualTo("从读者痛点切入");
  }

  @Test
  void parseObject_rejectsBlankPayloadAsNullFields() {
    AiThemeSeedAiResult result = LlmJsonSupport.parseObject(
        "{\"title\":\"\",\"summary\":\"\"}", AiThemeSeedAiResult.class);
    assertThat(result).isNotNull();
    assertThat(result.getTitle()).isBlank();
    assertThat(result.getSummary()).isBlank();
  }

  @Test
  void parseAiThemeSeedItems_readsBatchItems() {
    String json = """
        {"items":[
          {"title":"选题甲","summary":"速览甲内容足够长","signals":["信号1"],"angle":"切口甲"},
          {"title":"选题乙","summary":"速览乙内容足够长","signals":["信号2"],"angle":"切口乙"}
        ]}
        """;
    List<AiThemeSeedAiResult> items = RadarCollectAiSupport.parseAiThemeSeedItems(json);
    assertThat(items).hasSize(2);
    assertThat(items.get(0).getTitle()).isEqualTo("选题甲");
    assertThat(items.get(1).getTitle()).isEqualTo("选题乙");
  }

  @Test
  void parseAiThemeSeedItems_fallsBackToSingleObject() {
    String json = """
        {"title":"单条选题","summary":"单条速览正文","signals":[],"angle":"切口"}
        """;
    List<AiThemeSeedAiResult> items = RadarCollectAiSupport.parseAiThemeSeedItems(json);
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getTitle()).isEqualTo("单条选题");
  }
}
