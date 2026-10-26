package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.mode.hotnews.domain.TopicDimensionScore;
import com.chengwenfang.mode.hotnews.domain.TopicScoreDetails;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsTopicScoreDimensionEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsTopicScoreMetricEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsTopicScoreTagEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotnewsScoreDimensionSets;
import com.chengwenfang.mode.hotnews.facade.constant.HotnewsScoreTagTypes;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsTopicScoreDimensionMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsTopicScoreMetricMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsTopicScoreTagMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 选题评分明细：结构化表 ↔ {@link TopicScoreDetails}。
 */
@Component
@RequiredArgsConstructor
public class TopicScoreDetailsStore {
  private final HotnewsTopicScoreMetricMapper metricMapper;
  private final HotnewsTopicScoreDimensionMapper dimensionMapper;
  private final HotnewsTopicScoreTagMapper tagMapper;

  /**
   * 读取评分明细；无 metrics 行返回空对象（等价原 "{}"）。
   *
   * @param topicId 选题 id
   * @return 明细；永不为 null
   */
  public TopicScoreDetails read(String topicId) {
    if (StrUtil.isBlank(topicId)) {
      return new TopicScoreDetails();
    }
    HotnewsTopicScoreMetricEntity metric = metricMapper.selectById(topicId);
    if (metric == null) {
      return new TopicScoreDetails();
    }
    TopicScoreDetails details = new TopicScoreDetails();
    details.setSourceCount(n(metric.getSourceCount()));
    details.setOfficialCount(n(metric.getOfficialCount()));
    details.setPrimaryCount(n(metric.getPrimaryCount()));
    details.setSecondaryCount(n(metric.getSecondaryCount()));
    details.setCommunityCount(n(metric.getCommunityCount()));
    details.setUnknownDateCount(n(metric.getUnknownDateCount()));
    details.setIndependentSourceCount(n(metric.getIndependentSourceCount()));
    details.setRecentSourceCount(n(metric.getRecentSourceCount()));
    details.setAiHits(n(metric.getAiHits()));
    details.setDepthHits(n(metric.getDepthHits()));
    details.setSourceGrowthRatio(metric.getSourceGrowthRatio() == null ? 0D : metric.getSourceGrowthRatio());
    details.setFreshnessHours(metric.getFreshnessHours() == null ? 0D : metric.getFreshnessHours());
    details.setMetricNote(metric.getMetricNote());
    details.setRubricVersion(metric.getRubricVersion());
    details.setRecommendUse(metric.getRecommendUse());
    details.setRiskNote(metric.getRiskNote());
    details.setOriginKind(metric.getOriginKind());
    details.setSourceTypes(readTags(topicId, HotnewsScoreTagTypes.SOURCE_TYPE));
    details.setSignals(readTags(topicId, HotnewsScoreTagTypes.SIGNAL));
    details.setDimensions(readDimensions(topicId, HotnewsScoreDimensionSets.CURRENT));
    details.setRubricSnapshot(readDimensions(topicId, HotnewsScoreDimensionSets.RUBRIC_SNAPSHOT));
    return details;
  }

  /**
   * 全量覆盖写入评分明细。
   *
   * @param topicId 选题 id
   * @param details 明细；null 视为清空
   */
  @Transactional
  public void write(String topicId, TopicScoreDetails details) {
    if (StrUtil.isBlank(topicId)) {
      return;
    }
    clearChildren(topicId);
    if (details == null) {
      metricMapper.deleteById(topicId);
      return;
    }
    HotnewsTopicScoreMetricEntity metric = HotnewsTopicScoreMetricEntity.builder()
        .topicId(topicId)
        .sourceCount(details.getSourceCount())
        .officialCount(details.getOfficialCount())
        .primaryCount(details.getPrimaryCount())
        .secondaryCount(details.getSecondaryCount())
        .communityCount(details.getCommunityCount())
        .unknownDateCount(details.getUnknownDateCount())
        .independentSourceCount(details.getIndependentSourceCount())
        .recentSourceCount(details.getRecentSourceCount())
        .aiHits(details.getAiHits())
        .depthHits(details.getDepthHits())
        .sourceGrowthRatio(details.getSourceGrowthRatio())
        .freshnessHours(details.getFreshnessHours())
        .metricNote(details.getMetricNote())
        .rubricVersion(details.getRubricVersion())
        .recommendUse(details.getRecommendUse())
        .riskNote(details.getRiskNote())
        .originKind(details.getOriginKind())
        .build();
    if (metricMapper.selectById(topicId) == null) {
      metricMapper.insert(metric);
    } else {
      metricMapper.updateById(metric);
    }
    writeTags(topicId, HotnewsScoreTagTypes.SOURCE_TYPE, details.getSourceTypes());
    writeTags(topicId, HotnewsScoreTagTypes.SIGNAL, details.getSignals());
    writeDimensions(topicId, HotnewsScoreDimensionSets.CURRENT, details.getDimensions());
    writeDimensions(topicId, HotnewsScoreDimensionSets.RUBRIC_SNAPSHOT, details.getRubricSnapshot());
  }

  /**
   * 删除选题下评分子行与 metrics。
   *
   * @param topicId 选题 id
   */
  @Transactional
  public void delete(String topicId) {
    if (StrUtil.isBlank(topicId)) {
      return;
    }
    clearChildren(topicId);
    metricMapper.deleteById(topicId);
  }

  private void clearChildren(String topicId) {
    dimensionMapper.delete(new LambdaQueryWrapper<HotnewsTopicScoreDimensionEntity>()
        .eq(HotnewsTopicScoreDimensionEntity::getTopicId, topicId));
    tagMapper.delete(new LambdaQueryWrapper<HotnewsTopicScoreTagEntity>()
        .eq(HotnewsTopicScoreTagEntity::getTopicId, topicId));
  }

  private List<String> readTags(String topicId, String tagType) {
    return tagMapper.selectList(new LambdaQueryWrapper<HotnewsTopicScoreTagEntity>()
            .eq(HotnewsTopicScoreTagEntity::getTopicId, topicId)
            .eq(HotnewsTopicScoreTagEntity::getTagType, tagType)
            .orderByAsc(HotnewsTopicScoreTagEntity::getSeq))
        .stream()
        .map(HotnewsTopicScoreTagEntity::getTagValue)
        .filter(StrUtil::isNotBlank)
        .toList();
  }

  private void writeTags(String topicId, String tagType, List<String> values) {
    if (CollUtil.isEmpty(values)) {
      return;
    }
    int seq = 0;
    for (String value : values) {
      if (StrUtil.isBlank(value)) {
        continue;
      }
      tagMapper.insert(HotnewsTopicScoreTagEntity.builder()
          .id(IdsUtil.newId())
          .topicId(topicId)
          .tagType(tagType)
          .seq(seq++)
          .tagValue(value)
          .build());
    }
  }

  private List<TopicDimensionScore> readDimensions(String topicId, String dimensionSet) {
    List<TopicDimensionScore> out = new ArrayList<>();
    for (HotnewsTopicScoreDimensionEntity row : dimensionMapper.selectList(
        new LambdaQueryWrapper<HotnewsTopicScoreDimensionEntity>()
            .eq(HotnewsTopicScoreDimensionEntity::getTopicId, topicId)
            .eq(HotnewsTopicScoreDimensionEntity::getDimensionSet, dimensionSet)
            .orderByAsc(HotnewsTopicScoreDimensionEntity::getSeq))) {
      out.add(TopicDimensionScore.builder()
          .key(row.getDimensionKey())
          .label(row.getLabel())
          .weight(row.getWeight())
          .score(row.getScore())
          .reason(row.getReason())
          .build());
    }
    return out;
  }

  private void writeDimensions(String topicId, String dimensionSet, List<TopicDimensionScore> dimensions) {
    if (CollUtil.isEmpty(dimensions)) {
      return;
    }
    int seq = 0;
    for (TopicDimensionScore dimension : dimensions) {
      if (dimension == null) {
        continue;
      }
      dimensionMapper.insert(HotnewsTopicScoreDimensionEntity.builder()
          .id(IdsUtil.newId())
          .topicId(topicId)
          .dimensionSet(dimensionSet)
          .seq(seq++)
          .dimensionKey(StrUtil.blankToDefault(dimension.getKey(), ""))
          .label(dimension.getLabel())
          .weight(dimension.getWeight())
          .score(dimension.getScore())
          .reason(dimension.getReason())
          .build());
    }
  }

  private static int n(Integer value) {
    return value == null ? 0 : value;
  }
}
