package com.workbench.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.mode.hotnews.domain.entity.HotnewsBriefItemEntity;
import com.workbench.mode.hotnews.domain.entity.HotnewsBriefSourceEntity;
import com.workbench.mode.hotnews.domain.entity.TopicBriefEntity;
import com.workbench.mode.hotnews.facade.constant.HotnewsBriefItemTypes;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsBriefSourceRow;
import com.workbench.mode.hotnews.infrastructure.mapper.HotnewsBriefItemMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.HotnewsBriefSourceMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 简报结构化读写：主行 + items + sources ↔ summary Map / 来源行。
 */
@Component
@RequiredArgsConstructor
public class BriefStructuredStore {
  private final HotnewsBriefItemMapper itemMapper;
  private final HotnewsBriefSourceMapper sourceMapper;
  private final HotNewsCodec codec;

  /**
   * 组装 summary Map（视图/兼容键序）。
   *
   * @param brief 简报主行
   * @return summary；永不为 null
   */
  public Map<String, Object> readSummary(TopicBriefEntity brief) {
    Map<String, Object> summary = new LinkedHashMap<>();
    if (brief == null) {
      return summary;
    }
    summary.put("overview", StrUtil.nullToEmpty(brief.getOverview()));
    summary.put("coreThesis", StrUtil.nullToEmpty(brief.getCoreThesis()));
    summary.put("coreEvent", StrUtil.nullToEmpty(brief.getCoreEvent()));
    summary.put("confirmedFacts", readItems(brief.getId(), HotnewsBriefItemTypes.CONFIRMED_FACT));
    summary.put("readerImpact", readItems(brief.getId(), HotnewsBriefItemTypes.READER_IMPACT));
    summary.put("recommendedAngles", readItems(brief.getId(), HotnewsBriefItemTypes.RECOMMENDED_ANGLE));
    summary.put("unknowns", readItems(brief.getId(), HotnewsBriefItemTypes.UNKNOWN));
    if (StrUtil.isNotBlank(brief.getNarrativeShape())) {
      summary.put("narrativeShape", brief.getNarrativeShape());
    }
    if (StrUtil.isNotBlank(brief.getConfirmationNote())) {
      summary.put("confirmationNote", brief.getConfirmationNote());
    }
    return summary;
  }

  /**
   * 读取来源快照行。
   *
   * @param briefId 简报 id
   * @return 行列表
   */
  public List<HotNewsBriefSourceRow> readSources(String briefId) {
    if (StrUtil.isBlank(briefId)) {
      return List.of();
    }
    List<HotNewsBriefSourceRow> out = new ArrayList<>();
    for (HotnewsBriefSourceEntity row : sourceMapper.selectList(
        new LambdaQueryWrapper<HotnewsBriefSourceEntity>()
            .eq(HotnewsBriefSourceEntity::getBriefId, briefId)
            .orderByAsc(HotnewsBriefSourceEntity::getSeq))) {
      out.add(HotNewsBriefSourceRow.builder()
          .id(row.getRowId())
          .topicId(row.getTopicId())
          .sourceItemId(row.getSourceItemId())
          .role(row.getRole())
          .title(row.getTitle())
          .name(row.getName())
          .sourceName(row.getSourceName())
          .sourceTitle(row.getSourceTitle())
          .url(row.getUrl())
          .sourceUrl(row.getSourceUrl())
          .summary(row.getSummary())
          .build());
    }
    return out;
  }

  /**
   * 序列化来源行为 JSON（审核入参兼容）。
   *
   * @param briefId 简报 id
   * @return JSON 数组字符串
   */
  public String writeSourcesJson(String briefId) {
    List<HotNewsBriefSourceRow> rows = readSources(briefId);
    if (rows.isEmpty()) {
      return "[]";
    }
    return codec.writeJson(rows);
  }

  /**
   * 将 summary Map 与来源写入子表，并回填主行标量字段。
   *
   * @param brief   已持久化或待插入的主行（须有 id）
   * @param summary summary Map
   * @param sources 来源行
   */
  @Transactional
  public void writeChildren(
      TopicBriefEntity brief, Map<String, Object> summary, List<HotNewsBriefSourceRow> sources) {
    if (brief == null || StrUtil.isBlank(brief.getId())) {
      return;
    }
    Map<String, Object> map = summary == null ? Map.of() : summary;
    brief.setOverview(HotNewsCodec.asString(map.get("overview")));
    brief.setCoreThesis(HotNewsCodec.asString(map.get("coreThesis")));
    brief.setCoreEvent(HotNewsCodec.asString(map.get("coreEvent")));
    brief.setNarrativeShape(HotNewsCodec.asString(map.get("narrativeShape")));
    brief.setConfirmationNote(HotNewsCodec.asString(map.get("confirmationNote")));
    itemMapper.delete(new LambdaQueryWrapper<HotnewsBriefItemEntity>()
        .eq(HotnewsBriefItemEntity::getBriefId, brief.getId()));
    writeItems(brief.getId(), HotnewsBriefItemTypes.CONFIRMED_FACT,
        HotNewsCodec.asStringList(map.get("confirmedFacts")));
    writeItems(brief.getId(), HotnewsBriefItemTypes.READER_IMPACT,
        HotNewsCodec.asStringList(map.get("readerImpact")));
    writeItems(brief.getId(), HotnewsBriefItemTypes.RECOMMENDED_ANGLE,
        HotNewsCodec.asStringList(map.get("recommendedAngles")));
    writeItems(brief.getId(), HotnewsBriefItemTypes.UNKNOWN,
        HotNewsCodec.asStringList(map.get("unknowns")));
    sourceMapper.delete(new LambdaQueryWrapper<HotnewsBriefSourceEntity>()
        .eq(HotnewsBriefSourceEntity::getBriefId, brief.getId()));
    if (CollUtil.isEmpty(sources)) {
      return;
    }
    int seq = 0;
    for (HotNewsBriefSourceRow source : sources) {
      if (source == null) {
        continue;
      }
      sourceMapper.insert(HotnewsBriefSourceEntity.builder()
          .id(IdsUtil.newId())
          .briefId(brief.getId())
          .seq(seq++)
          .rowId(source.getId())
          .topicId(source.getTopicId())
          .sourceItemId(source.getSourceItemId())
          .role(source.getRole())
          .title(source.getTitle())
          .name(source.getName())
          .sourceName(source.getSourceName())
          .sourceTitle(source.getSourceTitle())
          .url(source.getUrl())
          .sourceUrl(source.getSourceUrl())
          .summary(source.getSummary())
          .build());
    }
  }

  private List<String> readItems(String briefId, String itemType) {
    if (StrUtil.isBlank(briefId)) {
      return List.of();
    }
    return itemMapper.selectList(new LambdaQueryWrapper<HotnewsBriefItemEntity>()
            .eq(HotnewsBriefItemEntity::getBriefId, briefId)
            .eq(HotnewsBriefItemEntity::getItemType, itemType)
            .orderByAsc(HotnewsBriefItemEntity::getSeq))
        .stream()
        .map(HotnewsBriefItemEntity::getContent)
        .filter(StrUtil::isNotBlank)
        .toList();
  }

  private void writeItems(String briefId, String itemType, List<String> values) {
    if (CollUtil.isEmpty(values)) {
      return;
    }
    int seq = 0;
    for (String value : values) {
      if (StrUtil.isBlank(value)) {
        continue;
      }
      itemMapper.insert(HotnewsBriefItemEntity.builder()
          .id(IdsUtil.newId())
          .briefId(briefId)
          .itemType(itemType)
          .seq(seq++)
          .content(value)
          .build());
    }
  }
}
