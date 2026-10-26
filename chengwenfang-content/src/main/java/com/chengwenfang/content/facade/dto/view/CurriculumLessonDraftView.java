package com.chengwenfang.content.facade.dto.view;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 课表草案中的单课。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumLessonDraftView {
  /** 课序号（从 1 连续）。 */
  private Integer lessonNo;
  /** 标题。 */
  private String title;
  /** 摘要。 */
  private String summary;
  /** 学习成果（可选）。 */
  @Builder.Default
  private List<String> learningOutcomes = new ArrayList<>();
}
