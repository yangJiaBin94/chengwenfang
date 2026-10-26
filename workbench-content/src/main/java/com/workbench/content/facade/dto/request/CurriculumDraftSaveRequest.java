package com.workbench.content.facade.dto.request;

import com.workbench.content.facade.dto.view.CurriculumDraftView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 人工编辑课表草案请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurriculumDraftSaveRequest {
  private CurriculumDraftView draft;
}
