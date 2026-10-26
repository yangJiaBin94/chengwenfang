package com.workbench.mode.hotnews.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 雷达视频营销标题兜底规则。 */
class SourceVideoContentAssistServiceTest {
  @Test
  void detects_bilibili_hard_sell_title() {
    assertThat(SourceVideoContentAssistService.looksLikeHardSellTitle(
            "每天2次，氨糖+钙+软骨素，关节越来越年轻！赶紧试试"))
        .isTrue();
  }

  @Test
  void keeps_knowledge_titles() {
    assertThat(SourceVideoContentAssistService.looksLikeHardSellTitle(
            "专家提醒：中老年补钙不能只看广告"))
        .isFalse();
  }
}
