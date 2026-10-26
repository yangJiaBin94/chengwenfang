package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.sourcing.application.QuerySiteBlockSampleStore;
import com.chengwenfang.sourcing.domain.entity.SourceQuerySiteBlockEntity;
import com.chengwenfang.sourcing.facade.constant.SearchQueryDiagnoseKeys;
import com.chengwenfang.sourcing.facade.dto.request.QuerySiteBlockUpsertRequest;
import com.chengwenfang.sourcing.facade.dto.view.QuerySiteBlockView;
import com.chengwenfang.sourcing.facade.enums.QuerySiteBlockStatus;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceQuerySiteBlockMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 搜索词 × 网站拉黑名单读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuerySiteBlockService {

  /** 搜索词 × 网站拉黑表。 */
  private final SourceQuerySiteBlockMapper querySiteBlockMapper;
  /** 拉黑样例标题子表。 */
  private final QuerySiteBlockSampleStore sampleStore;

  /**
   * 列出主题下生效中的搜索词 × 网站拉黑。
   *
   * @param themeId 主题 ID
   * @return 拉黑列表
   */
  public List<QuerySiteBlockView> listActiveQuerySiteBlocks(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return List.of();
    }
    return querySiteBlockMapper.selectList(new LambdaQueryWrapper<SourceQuerySiteBlockEntity>()
            .eq(SourceQuerySiteBlockEntity::getThemeId, themeId)
            .eq(SourceQuerySiteBlockEntity::getStatus, QuerySiteBlockStatus.ACTIVE.getValue())
            .orderByDesc(SourceQuerySiteBlockEntity::getUpdatedAt))
        .stream()
        .map(this::toQuerySiteBlockView)
        .toList();
  }

  /**
   * 批量 upsert 搜索词 × 网站拉黑。
   *
   * @param requests upsert 请求
   * @return 写入后的视图
   */
  public List<QuerySiteBlockView> upsertQuerySiteBlocks(List<QuerySiteBlockUpsertRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return List.of();
    }
    List<QuerySiteBlockView> out = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();
    for (QuerySiteBlockUpsertRequest request : requests) {
      if (request == null
          || StrUtil.isBlank(request.getThemeId())
          || StrUtil.isBlank(request.getChannelType())
          || StrUtil.isBlank(request.getSearchQuery())
          || StrUtil.isBlank(request.getSiteKey())) {
        continue;
      }
      String themeId = request.getThemeId().trim();
      String channelType = request.getChannelType().trim().toLowerCase();
      String searchQuery = StrUtil.subPre(
          request.getSearchQuery().trim(), SearchQueryDiagnoseKeys.MAX_SEARCH_QUERY_LEN);
      String siteKey = StrUtil.subPre(
          request.getSiteKey().trim().toLowerCase(), SearchQueryDiagnoseKeys.MAX_SITE_KEY_LEN);
      SourceQuerySiteBlockEntity existing = querySiteBlockMapper.selectOne(
          new LambdaQueryWrapper<SourceQuerySiteBlockEntity>()
              .eq(SourceQuerySiteBlockEntity::getThemeId, themeId)
              .eq(SourceQuerySiteBlockEntity::getChannelType, channelType)
              .eq(SourceQuerySiteBlockEntity::getSearchQuery, searchQuery)
              .eq(SourceQuerySiteBlockEntity::getSiteKey, siteKey)
              .last("LIMIT 1"));
      int hitDelta = request.getHitDelta() == null || request.getHitDelta() < 1
          ? 1
          : request.getHitDelta();
      List<String> previousSamples =
          existing == null ? List.of() : sampleStore.read(existing.getId());
      List<String> mergedSamples =
          QuerySiteBlockSampleStore.merge(previousSamples, request.getSampleTitles());
      String reason = StrUtil.blankToDefault(request.getReason(), "主题过滤偏题占比过高");
      if (existing == null) {
        SourceQuerySiteBlockEntity created = SourceQuerySiteBlockEntity.builder()
            .id(IdsUtil.newId())
            .themeId(themeId)
            .channelType(channelType)
            .searchQuery(searchQuery)
            .siteKey(siteKey)
            .status(QuerySiteBlockStatus.ACTIVE.getValue())
            .hitCount(hitDelta)
            .reason(reason)
            .createdAt(now)
            .updatedAt(now)
            .build();
        querySiteBlockMapper.insert(created);
        sampleStore.write(created.getId(), mergedSamples);
        out.add(toQuerySiteBlockView(created, mergedSamples));
        continue;
      }
      int previousHits = existing.getHitCount() == null ? 0 : existing.getHitCount();
      existing.setStatus(QuerySiteBlockStatus.ACTIVE.getValue());
      existing.setHitCount(previousHits + hitDelta);
      existing.setReason(reason);
      existing.setUpdatedAt(now);
      querySiteBlockMapper.updateById(existing);
      sampleStore.write(existing.getId(), mergedSamples);
      out.add(toQuerySiteBlockView(existing, mergedSamples));
    }
    return out;
  }

  /**
   * 实体转拉黑视图。
   *
   * @param entity 实体
   * @return 视图
   */
  QuerySiteBlockView toQuerySiteBlockView(SourceQuerySiteBlockEntity entity) {
    if (entity == null) {
      return null;
    }
    return toQuerySiteBlockView(entity, sampleStore.read(entity.getId()));
  }

  /**
   * 实体转拉黑视图（带已读样例）。
   *
   * @param entity  实体
   * @param samples 样例标题
   * @return 视图
   */
  QuerySiteBlockView toQuerySiteBlockView(
      SourceQuerySiteBlockEntity entity, List<String> samples) {
    return QuerySiteBlockView.builder()
        .id(entity.getId())
        .themeId(entity.getThemeId())
        .channelType(entity.getChannelType())
        .searchQuery(entity.getSearchQuery())
        .siteKey(entity.getSiteKey())
        .status(entity.getStatus())
        .hitCount(entity.getHitCount())
        .sampleTitles(samples == null ? List.of() : samples)
        .reason(entity.getReason())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

}
