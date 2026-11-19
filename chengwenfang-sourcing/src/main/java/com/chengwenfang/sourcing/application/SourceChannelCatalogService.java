package com.chengwenfang.sourcing.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.sourcing.domain.entity.SourceChannelCatalogEntity;
import com.chengwenfang.sourcing.facade.dto.view.SourceChannelCatalogView;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceChannelCatalogMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 信源沉淀池只读查询。
 */
@Service
@RequiredArgsConstructor
public class SourceChannelCatalogService {
  /** 沉淀池 Mapper。 */
  private final SourceChannelCatalogMapper catalogMapper;

  /**
   * 列出启用条目。
   *
   * @return 视图列表
   */
  public List<SourceChannelCatalogView> listEnabledCatalog() {
    List<SourceChannelCatalogEntity> rows = catalogMapper.selectList(
        new LambdaQueryWrapper<SourceChannelCatalogEntity>()
            .eq(SourceChannelCatalogEntity::getEnabled, true)
            .orderByAsc(SourceChannelCatalogEntity::getSortOrder)
            .orderByAsc(SourceChannelCatalogEntity::getCode));
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    List<SourceChannelCatalogView> views = new ArrayList<>(rows.size());
    for (SourceChannelCatalogEntity row : rows) {
      if (row == null) {
        continue;
      }
      views.add(toView(row));
    }
    return views;
  }

  /**
   * 实体转视图。
   *
   * @param entity 行
   * @return 视图
   */
  private static SourceChannelCatalogView toView(SourceChannelCatalogEntity entity) {
    return SourceChannelCatalogView.builder()
        .id(entity.getId())
        .code(entity.getCode())
        .name(entity.getName())
        .channelType(entity.getChannelType())
        .baseUrl(entity.getBaseUrl())
        .configJson(entity.getConfigJson())
        .tags(parseTags(entity.getTagsJson()))
        .enabled(entity.getEnabled())
        .sortOrder(entity.getSortOrder())
        .build();
  }

  /**
   * 解析 tags_json。
   *
   * @param raw JSON 数组字符串
   * @return 标签列表
   */
  static List<String> parseTags(String raw) {
    if (StrUtil.isBlank(raw)) {
      return List.of();
    }
    try {
      JSONArray array = JSONUtil.parseArray(raw.trim());
      List<String> tags = new ArrayList<>();
      for (Object item : array) {
        String tag = StrUtil.trim(StrUtil.nullToEmpty(item == null ? null : String.valueOf(item)))
            .toLowerCase();
        if (StrUtil.isNotBlank(tag)) {
          tags.add(tag);
        }
      }
      return tags;
    } catch (Exception ignored) {
      return List.of();
    }
  }
}
