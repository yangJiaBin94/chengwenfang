package com.chengwenfang.mode.hotnews.application.generation.radarcollect.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自适应搜索线索抽取模型结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveSearchExtractAiResult {
  /** 板块/题材线索。 */
  @Builder.Default
  private List<String> sectors = new ArrayList<>();
  /** 标的/公司名。 */
  @Builder.Default
  private List<String> tickers = new ArrayList<>();
  /** 人物/机构名。 */
  @Builder.Default
  private List<String> people = new ArrayList<>();
}
