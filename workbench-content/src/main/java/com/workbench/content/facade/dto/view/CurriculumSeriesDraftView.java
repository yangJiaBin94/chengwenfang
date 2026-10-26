package com.workbench.content.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课表草案中的系列。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumSeriesDraftView {
  /** 临时 id（前端拖拽用）。 */
  private String tempId;
  /** 系列名称。 */
  private String name;
  /** 排序。 */
  private Integer sortOrder;
  /** 一句话定位。 */
  private String positioning;
  /** 课次列表。 */
  @Builder.Default
  private List<CurriculumLessonDraftView> lessons = new ArrayList<>();
}
