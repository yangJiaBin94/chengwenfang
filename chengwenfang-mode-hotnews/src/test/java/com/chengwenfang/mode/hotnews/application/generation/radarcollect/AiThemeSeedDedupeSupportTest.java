package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import static org.assertj.core.api.Assertions.assertThat;

import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AiThemeSeedAiResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link AiThemeSeedDedupeSupport} 标题去重自检。 */
class AiThemeSeedDedupeSupportTest {

  @Test
  void takeUnique_dropsInBatchDuplicatesAndReserved() {
    List<AiThemeSeedAiResult> items = List.of(
        seed("选题甲", "速览甲"),
        seed("选题甲", "速览甲重复"),
        seed("选题乙", "速览乙"),
        seed("选题丙", "速览丙"));
    Set<String> reserved = new LinkedHashSet<>();
    reserved.add(AiThemeSeedDedupeSupport.titleKey("选题乙"));

    List<AiThemeSeedAiResult> unique =
        AiThemeSeedDedupeSupport.takeUnique(items, reserved, 10);

    assertThat(unique).extracting(AiThemeSeedAiResult::getTitle)
        .containsExactly("选题甲", "选题丙");
  }

  @Test
  void takeUnique_respectsLimit() {
    List<AiThemeSeedAiResult> items = List.of(
        seed("A", "速览A"),
        seed("B", "速览B"),
        seed("C", "速览C"));
    assertThat(AiThemeSeedDedupeSupport.takeUnique(items, Set.of(), 2))
        .extracting(AiThemeSeedAiResult::getTitle)
        .containsExactly("A", "B");
  }

  @Test
  void maxRefillRetries_isTwo() {
    assertThat(AiThemeSeedDedupeSupport.MAX_REFILL_RETRIES).isEqualTo(2);
  }

  private static AiThemeSeedAiResult seed(String title, String summary) {
    return AiThemeSeedAiResult.builder().title(title).summary(summary).build();
  }
}
