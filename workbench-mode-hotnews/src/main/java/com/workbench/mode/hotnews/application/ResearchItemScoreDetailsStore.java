package com.workbench.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.mode.hotnews.domain.ResearchItemScoreDetails;
import com.workbench.mode.hotnews.domain.TopicDimensionScore;
import com.workbench.mode.hotnews.domain.entity.HotnewsResearchItemDimensionEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.workbench.mode.hotnews.infrastructure.mapper.HotnewsResearchItemDimensionMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资料池评分明细：条目列 + dimensions 子表 ↔ {@link ResearchItemScoreDetails}。
 */
@Component
@RequiredArgsConstructor
public class ResearchItemScoreDetailsStore {
  private final TopicResearchItemMapper itemMapper;
  private final HotnewsResearchItemDimensionMapper dimensionMapper;

  /**
   * 读取评分明细；无明细返回 null（与原 score_details 空一致）。
   *
   * @param itemId 条目 id
   * @return 明细；无则 null
   */
  public ResearchItemScoreDetails read(String itemId) {
    if (StrUtil.isBlank(itemId)) {
      return null;
    }
    TopicResearchItemEntity item = itemMapper.selectById(itemId);
    if (item == null || !Boolean.TRUE.equals(item.getHasScoreDetails())) {
      return null;
    }
    List<HotnewsResearchItemDimensionEntity> rows = dimensionMapper.selectList(
        new LambdaQueryWrapper<HotnewsResearchItemDimensionEntity>()
            .eq(HotnewsResearchItemDimensionEntity::getItemId, itemId)
            .orderByAsc(HotnewsResearchItemDimensionEntity::getSeq));
    List<TopicDimensionScore> dimensions = new ArrayList<>();
    for (HotnewsResearchItemDimensionEntity row : rows) {
      dimensions.add(TopicDimensionScore.builder()
          .key(row.getDimensionKey())
          .label(row.getLabel())
          .weight(row.getWeight())
          .score(row.getScore())
          .reason(row.getReason())
          .build());
    }
    return ResearchItemScoreDetails.builder()
        .rubricVersion(item.getRubricVersion())
        .dimensions(dimensions)
        .recommendUse(item.getRecommendUse())
        .riskNote(item.getRiskNote())
        .build();
  }

  /**
   * 全量覆盖写入评分明细；null 表示清空。
   *
   * @param itemId  条目 id
   * @param details 明细；null 清空
   */
  @Transactional
  public void write(String itemId, ResearchItemScoreDetails details) {
    if (StrUtil.isBlank(itemId)) {
      return;
    }
    TopicResearchItemEntity item = itemMapper.selectById(itemId);
    if (item == null) {
      return;
    }
    dimensionMapper.delete(new LambdaQueryWrapper<HotnewsResearchItemDimensionEntity>()
        .eq(HotnewsResearchItemDimensionEntity::getItemId, itemId));
    if (details == null) {
      item.setHasScoreDetails(false);
      item.setRubricVersion(null);
      item.setRecommendUse(null);
      item.setRiskNote(null);
      itemMapper.updateById(item);
      return;
    }
    item.setHasScoreDetails(true);
    item.setRubricVersion(details.getRubricVersion());
    item.setRecommendUse(details.getRecommendUse());
    item.setRiskNote(details.getRiskNote());
    itemMapper.updateById(item);
    List<TopicDimensionScore> dimensions =
        details.getDimensions() == null ? List.of() : details.getDimensions();
    int seq = 0;
    for (TopicDimensionScore dimension : dimensions) {
      if (dimension == null) {
        continue;
      }
      dimensionMapper.insert(HotnewsResearchItemDimensionEntity.builder()
          .id(IdsUtil.newId())
          .itemId(itemId)
          .seq(seq++)
          .dimensionKey(StrUtil.blankToDefault(dimension.getKey(), ""))
          .label(dimension.getLabel())
          .weight(dimension.getWeight())
          .score(dimension.getScore())
          .reason(dimension.getReason())
          .build());
    }
  }

  /**
   * 插入条目后写入评分明细（新建场景）。
   *
   * @param item    已 insert 的条目
   * @param details 明细；可空
   */
  @Transactional
  public void writeAfterInsert(TopicResearchItemEntity item, ResearchItemScoreDetails details) {
    if (item == null || StrUtil.isBlank(item.getId())) {
      return;
    }
    if (details == null) {
      item.setHasScoreDetails(false);
      itemMapper.updateById(item);
      return;
    }
    write(item.getId(), details);
  }

  /**
   * 是否存在任何维度行（调试用）。
   *
   * @param itemId 条目 id
   * @return 有维度为 true
   */
  public boolean hasDimensions(String itemId) {
    if (StrUtil.isBlank(itemId)) {
      return false;
    }
    Long count = dimensionMapper.selectCount(new LambdaQueryWrapper<HotnewsResearchItemDimensionEntity>()
        .eq(HotnewsResearchItemDimensionEntity::getItemId, itemId));
    return count != null && count > 0;
  }

  /**
   * 判断明细是否等价于「无明细」（null 或空壳）。
   *
   * @param details 明细
   * @return 无明细为 true
   */
  public static boolean isBlankDetails(ResearchItemScoreDetails details) {
    if (details == null) {
      return true;
    }
    return details.getRubricVersion() == null
        && StrUtil.isBlank(details.getRecommendUse())
        && StrUtil.isBlank(details.getRiskNote())
        && CollUtil.isEmpty(details.getDimensions());
  }
}
