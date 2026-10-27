package com.chengwenfang.mode.hotnews.application.mode;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.dto.view.ThemeListMetrics;
import com.chengwenfang.content.facade.spi.ThemeListMetricsContributor;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceItemMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 热点模式主题列表指标：来源条目数与候选选题数。
 */
@Component
@RequiredArgsConstructor
public class HotNewsThemeListMetricsContributor implements ThemeListMetricsContributor {

  private final SourceItemMapper sourceItemMapper;
  private final TopicClusterMapper topicClusterMapper;

  @Override
  public boolean supportsMode(String modeCode) {
    return ContentModeIds.isHotNews(null, modeCode);
  }

  @Override
  public Map<String, ThemeListMetrics> countByThemeIds(Set<String> themeIds) {
    if (CollUtil.isEmpty(themeIds)) {
      return Collections.emptyMap();
    }
    Map<String, Long> itemCounts = groupCount(
        sourceItemMapper.selectMaps(new QueryWrapper<SourceItemEntity>()
            .select("theme_id", "COUNT(*) AS cnt")
            .in("theme_id", themeIds)
            .groupBy("theme_id")));
    Map<String, Long> candidateCounts = groupCount(
        topicClusterMapper.selectMaps(new QueryWrapper<TopicClusterEntity>()
            .select("theme_id", "COUNT(*) AS cnt")
            .in("theme_id", themeIds)
            .eq("status", HotNewsTopicStatuses.CANDIDATE)
            .groupBy("theme_id")));
    Map<String, ThemeListMetrics> result = new HashMap<>();
    for (String themeId : themeIds) {
      long items = itemCounts.getOrDefault(themeId, 0L);
      long candidates = candidateCounts.getOrDefault(themeId, 0L);
      if (items == 0L && candidates == 0L) {
        continue;
      }
      result.put(
          themeId,
          ThemeListMetrics.builder().itemCount(items).candidateCount(candidates).build());
    }
    return result;
  }

  /**
   * 将 GROUP BY 结果行收成 themeId → 计数。
   *
   * @param rows selectMaps 结果
   * @return 计数表
   */
  private static Map<String, Long> groupCount(List<Map<String, Object>> rows) {
    if (CollUtil.isEmpty(rows)) {
      return Collections.emptyMap();
    }
    Map<String, Long> out = new HashMap<>();
    for (Map<String, Object> row : rows) {
      if (row == null) {
        continue;
      }
      Object themeIdValue = row.get("theme_id");
      if (themeIdValue == null) {
        themeIdValue = row.get("THEME_ID");
      }
      String themeId = themeIdValue == null ? null : String.valueOf(themeIdValue);
      if (StrUtil.isBlank(themeId)) {
        continue;
      }
      Object cntValue = row.get("cnt");
      if (cntValue == null) {
        cntValue = row.get("CNT");
      }
      out.put(themeId, Convert.toLong(cntValue, 0L));
    }
    return out;
  }
}
