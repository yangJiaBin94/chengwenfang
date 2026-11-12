package com.chengwenfang.mode.hotnews.application.generation.hotnews.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 产出的成文佐证截屏计划；{@code shots} 为空表示不需要截屏。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsAttachEvidencePlan {
  /** 截屏请求列表（实现侧会截断至上限）。 */
  @Builder.Default
  private List<HotNewsEvidenceShotSpec> shots = new ArrayList<>();
}
