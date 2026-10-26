package com.chengwenfang.content.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.content.domain.entity.ThemeProfileEntity;
import com.chengwenfang.content.domain.entity.ThemeProfileItemEntity;
import com.chengwenfang.content.domain.entity.ThemeScoringRubricDimensionEntity;
import com.chengwenfang.content.domain.entity.ThemeScoringRubricEntity;
import com.chengwenfang.content.facade.constant.ThemeProfileKinds;
import com.chengwenfang.content.infrastructure.mapper.ThemeProfileItemMapper;
import com.chengwenfang.content.infrastructure.mapper.ThemeProfileMapper;
import com.chengwenfang.content.infrastructure.mapper.ThemeScoringRubricDimensionMapper;
import com.chengwenfang.content.infrastructure.mapper.ThemeScoringRubricMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主题画像结构化读写（briefing / defaults）。
 */
@Service
public class ThemeProfileStore {
  private final ThemeProfileMapper profileMapper;
  private final ThemeProfileItemMapper itemMapper;
  private final ThemeScoringRubricMapper rubricMapper;
  private final ThemeScoringRubricDimensionMapper dimensionMapper;
  private final ContentEntityUtil support;

  public ThemeProfileStore(
      ThemeProfileMapper profileMapper,
      ThemeProfileItemMapper itemMapper,
      ThemeScoringRubricMapper rubricMapper,
      ThemeScoringRubricDimensionMapper dimensionMapper,
      ContentEntityUtil support) {
    this.profileMapper = profileMapper;
    this.itemMapper = itemMapper;
    this.rubricMapper = rubricMapper;
    this.dimensionMapper = dimensionMapper;
    this.support = support;
  }

  /**
   * 读取问卷 JSON；无画像返回 null。
   *
   * @param themeId 主题 id
   * @return JSON 文本；无则 null
   */
  public String loadBriefingJson(String themeId) {
    Map<String, Object> map = loadBriefing(themeId);
    return map.isEmpty() ? null : support.writeJson(map);
  }

  /**
   * 读取问卷 Map；无画像返回空 Map。
   *
   * @param themeId 主题 id
   * @return briefing Map
   */
  public Map<String, Object> loadBriefing(String themeId) {
    return loadMap(themeId, ThemeProfileKinds.BRIEFING);
  }

  /**
   * 读取 defaults JSON；无则 null。
   *
   * @param themeId 主题 id
   * @return JSON 文本；无则 null
   */
  public String loadDefaultsJson(String themeId) {
    Map<String, Object> map = loadDefaults(themeId);
    return map.isEmpty() ? null : support.writeJson(map);
  }

  /**
   * 读取 defaults Map。
   *
   * @param themeId 主题 id
   * @return defaults Map
   */
  public Map<String, Object> loadDefaults(String themeId) {
    return loadMap(themeId, ThemeProfileKinds.DEFAULTS);
  }

  /**
   * 保存问卷。
   *
   * @param themeId   主题 id
   * @param briefing  问卷 Map
   * @param updatedAt 更新时间
   */
  @Transactional
  public void saveBriefing(String themeId, Map<String, Object> briefing, LocalDateTime updatedAt) {
    save(themeId, ThemeProfileKinds.BRIEFING, briefing, updatedAt);
  }

  /**
   * 保存 defaults。
   *
   * @param themeId   主题 id
   * @param defaults  defaults Map
   * @param updatedAt 更新时间
   */
  @Transactional
  public void saveDefaults(String themeId, Map<String, Object> defaults, LocalDateTime updatedAt) {
    save(themeId, ThemeProfileKinds.DEFAULTS, defaults, updatedAt);
  }

  /**
   * 确保存在空 briefing 画像行。
   *
   * @param themeId 主题 id
   */
  @Transactional
  public void ensureBriefing(String themeId) {
    ThemeProfileEntity existing = selectProfile(themeId, ThemeProfileKinds.BRIEFING);
    if (existing != null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    profileMapper.insert(ThemeProfileEntity.builder()
        .themeId(themeId)
        .profileKind(ThemeProfileKinds.BRIEFING)
        .schemaVersion(1)
        .wizardStep(1)
        .updatedAt(now)
        .build());
  }

  private Map<String, Object> loadMap(String themeId, String profileKind) {
    if (StrUtil.isBlank(themeId)) {
      return Map.of();
    }
    ThemeProfileEntity profile = selectProfile(themeId, profileKind);
    if (profile == null) {
      return Map.of();
    }
    List<ThemeProfileItemEntity> items = itemMapper.selectList(new LambdaQueryWrapper<ThemeProfileItemEntity>()
        .eq(ThemeProfileItemEntity::getThemeId, themeId)
        .eq(ThemeProfileItemEntity::getProfileKind, profileKind)
        .orderByAsc(ThemeProfileItemEntity::getItemType)
        .orderByAsc(ThemeProfileItemEntity::getSeq));
    ThemeScoringRubricEntity rubric = selectRubric(themeId, profileKind);
    List<ThemeScoringRubricDimensionEntity> dims = dimensionMapper.selectList(
        new LambdaQueryWrapper<ThemeScoringRubricDimensionEntity>()
            .eq(ThemeScoringRubricDimensionEntity::getThemeId, themeId)
            .eq(ThemeScoringRubricDimensionEntity::getProfileKind, profileKind)
            .orderByAsc(ThemeScoringRubricDimensionEntity::getSeq));
    if (ThemeProfileKinds.DEFAULTS.equals(profileKind)) {
      return ThemeProfileAssembler.toDefaultsMap(profile, items, rubric, dims);
    }
    return ThemeProfileAssembler.toBriefingMap(profile, items, rubric, dims);
  }

  private void save(String themeId, String profileKind, Map<String, Object> root, LocalDateTime updatedAt) {
    ThemeProfileAssembler.AssembledProfile assembled =
        ThemeProfileAssembler.fromMap(themeId, profileKind, root, updatedAt);
    clearChildren(themeId, profileKind);
    ThemeProfileEntity existing = selectProfile(themeId, profileKind);
    if (existing == null) {
      profileMapper.insert(assembled.profile());
    } else {
      profileMapper.update(
          assembled.profile(),
          new LambdaQueryWrapper<ThemeProfileEntity>()
              .eq(ThemeProfileEntity::getThemeId, themeId)
              .eq(ThemeProfileEntity::getProfileKind, profileKind));
    }
    if (CollUtil.isNotEmpty(assembled.items())) {
      for (ThemeProfileItemEntity item : assembled.items()) {
        itemMapper.insert(item);
      }
    }
    if (assembled.rubric() != null) {
      rubricMapper.insert(assembled.rubric());
      if (CollUtil.isNotEmpty(assembled.dimensions())) {
        for (ThemeScoringRubricDimensionEntity dim : assembled.dimensions()) {
          dimensionMapper.insert(dim);
        }
      }
    }
  }

  private void clearChildren(String themeId, String profileKind) {
    dimensionMapper.delete(new LambdaQueryWrapper<ThemeScoringRubricDimensionEntity>()
        .eq(ThemeScoringRubricDimensionEntity::getThemeId, themeId)
        .eq(ThemeScoringRubricDimensionEntity::getProfileKind, profileKind));
    rubricMapper.delete(new LambdaQueryWrapper<ThemeScoringRubricEntity>()
        .eq(ThemeScoringRubricEntity::getThemeId, themeId)
        .eq(ThemeScoringRubricEntity::getProfileKind, profileKind));
    itemMapper.delete(new LambdaQueryWrapper<ThemeProfileItemEntity>()
        .eq(ThemeProfileItemEntity::getThemeId, themeId)
        .eq(ThemeProfileItemEntity::getProfileKind, profileKind));
  }

  private ThemeProfileEntity selectProfile(String themeId, String profileKind) {
    return profileMapper.selectOne(new LambdaQueryWrapper<ThemeProfileEntity>()
        .eq(ThemeProfileEntity::getThemeId, themeId)
        .eq(ThemeProfileEntity::getProfileKind, profileKind)
        .last("LIMIT 1"));
  }

  private ThemeScoringRubricEntity selectRubric(String themeId, String profileKind) {
    return rubricMapper.selectOne(new LambdaQueryWrapper<ThemeScoringRubricEntity>()
        .eq(ThemeScoringRubricEntity::getThemeId, themeId)
        .eq(ThemeScoringRubricEntity::getProfileKind, profileKind)
        .last("LIMIT 1"));
  }
}
