package com.workbench.content.facade.dto.request;


import com.workbench.content.facade.dto.view.HumanReviewImportEntry;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 批量导入人工审核请求。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportHumanReviewsRequest {
  /** 导入条目。 */
  private List<HumanReviewImportEntry> entries;
}
