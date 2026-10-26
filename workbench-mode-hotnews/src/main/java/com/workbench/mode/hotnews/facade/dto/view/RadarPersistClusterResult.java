package com.workbench.mode.hotnews.facade.dto.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 雷达幸存者落库与成簇结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadarPersistClusterResult {
  /** 新插入条数。 */
  private int inserted;
  /** 去重跳过条数。 */
  private int deduped;
  /** 选题簇数（本批次关联）。 */
  private int topicCount;
  /** 候选 tempId → source_item id。 */
  @Builder.Default
  private Map<String, String> sourceIdByTempId = new LinkedHashMap<>();
  /** 本批次选题 ID 列表。 */
  @Builder.Default
  private List<String> topicIds = new ArrayList<>();
  /** 选题 ID → 成员事实速览。 */
  @Builder.Default
  private Map<String, List<String>> memberBriefsByTopicId = new LinkedHashMap<>();
  /** 选题 ID → 标题。 */
  @Builder.Default
  private Map<String, String> titleByTopicId = new LinkedHashMap<>();
}
