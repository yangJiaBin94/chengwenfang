package com.workbench.content.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 主题课表草案（未 commit 前的系列/课次结构）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumDraftView {
  /** 合约版本。 */
  private int schemaVersion;
  /** 路线说明。 */
  private String routeNarrative;
  /** 系列草案列表。 */
  @Builder.Default
  private List<CurriculumSeriesDraftView> series = new ArrayList<>();
}
