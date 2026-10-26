package com.workbench.sourcing.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.IdsUtil;
import com.workbench.sourcing.domain.entity.SourceQuerySiteBlockSampleEntity;
import com.workbench.sourcing.facade.constant.SearchQueryDiagnoseKeys;
import com.workbench.sourcing.infrastructure.mapper.SourceQuerySiteBlockSampleMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 拉黑样例标题子表读写。
 */
@Component
@RequiredArgsConstructor
public class QuerySiteBlockSampleStore {
  private final SourceQuerySiteBlockSampleMapper sampleMapper;

  /**
   * 读取样例标题列表（按 seq）。
   *
   * @param blockId 拉黑 id
   * @return 标题列表
   */
  public List<String> read(String blockId) {
    if (StrUtil.isBlank(blockId)) {
      return List.of();
    }
    List<SourceQuerySiteBlockSampleEntity> rows = sampleMapper.selectList(
        new LambdaQueryWrapper<SourceQuerySiteBlockSampleEntity>()
            .eq(SourceQuerySiteBlockSampleEntity::getBlockId, blockId)
            .orderByAsc(SourceQuerySiteBlockSampleEntity::getSeq));
    List<String> out = new ArrayList<>();
    for (SourceQuerySiteBlockSampleEntity row : rows) {
      if (row != null && StrUtil.isNotBlank(row.getTitle())) {
        out.add(row.getTitle());
      }
    }
    return out;
  }

  /**
   * 全量覆盖写入样例标题。
   *
   * @param blockId 拉黑 id
   * @param titles  标题；可空
   */
  @Transactional
  public void write(String blockId, List<String> titles) {
    if (StrUtil.isBlank(blockId)) {
      return;
    }
    sampleMapper.delete(new LambdaQueryWrapper<SourceQuerySiteBlockSampleEntity>()
        .eq(SourceQuerySiteBlockSampleEntity::getBlockId, blockId));
    if (titles == null || titles.isEmpty()) {
      return;
    }
    int seq = 0;
    for (String title : titles) {
      if (StrUtil.isBlank(title)) {
        continue;
      }
      sampleMapper.insert(SourceQuerySiteBlockSampleEntity.builder()
          .id(IdsUtil.newId())
          .blockId(blockId)
          .seq(seq++)
          .title(title.trim())
          .build());
      if (seq >= SearchQueryDiagnoseKeys.MAX_SAMPLE_TITLES) {
        break;
      }
    }
  }

  /**
   * 合并已有与本轮样例，去重并截断。
   *
   * @param existing 已有标题
   * @param incoming 本轮样例
   * @return 合并列表
   */
  public static List<String> merge(List<String> existing, List<String> incoming) {
    LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (existing != null) {
      for (String title : existing) {
        if (StrUtil.isNotBlank(title)) {
          merged.add(title.trim());
        }
      }
    }
    if (incoming != null) {
      for (String title : incoming) {
        if (StrUtil.isNotBlank(title)) {
          merged.add(title.trim());
        }
      }
    }
    List<String> out = new ArrayList<>();
    for (String title : merged) {
      out.add(title);
      if (out.size() >= SearchQueryDiagnoseKeys.MAX_SAMPLE_TITLES) {
        break;
      }
    }
    return out;
  }
}
