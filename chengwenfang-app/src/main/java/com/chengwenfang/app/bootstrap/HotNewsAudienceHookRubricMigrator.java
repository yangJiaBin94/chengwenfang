package com.chengwenfang.app.bootstrap;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.application.ThemeProfileStore;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.content.facade.ScoringRubricSupport;
import com.chengwenfang.content.facade.constant.ContentModeIds;
import com.chengwenfang.content.facade.constant.HotNewsScoringRubricKeys;
import com.chengwenfang.content.facade.dto.view.ContentThemeView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricDimensionView;
import com.chengwenfang.content.facade.dto.view.ScoringRubricView;
import com.chengwenfang.identity.facade.OwnerContext;
import com.chengwenfang.identity.facade.constant.SeedUserIds;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 幂等：为所有热点主题量表注入「打开欲」维并写回 briefing/defaults。
 */
@Slf4j
@Component
@Order(25)
@Profile("!test & !bootstrap-seed")
@RequiredArgsConstructor
public class HotNewsAudienceHookRubricMigrator implements ApplicationRunner {
  private final ContentFacade content;
  private final ThemeProfileStore profiles;

  /**
   * 启动时迁移热点主题量表。
   *
   * @param args 启动参数（未使用）
   */
  @Override
  public void run(ApplicationArguments args) {
    OwnerContext.runAs(SeedUserIds.ADMIN, this::migrateAll);
  }

  private void migrateAll() {
    List<ContentThemeView> themes;
    try {
      themes = content.listThemes(ContentModeIds.HOT_NEWS_CODE);
    } catch (Exception exception) {
      log.warn("热点量表打开欲迁移跳过：无法列出主题 err={}", exception.getMessage());
      return;
    }
    if (CollUtil.isEmpty(themes)) {
      return;
    }
    int updated = 0;
    LocalDateTime now = LocalDateTime.now();
    for (ContentThemeView theme : themes) {
      if (theme == null || StrUtil.isBlank(theme.getId())) {
        continue;
      }
      if (migrateTheme(theme.getId(), now)) {
        updated++;
      }
    }
    if (updated > 0) {
      log.info("热点量表打开欲迁移完成 themes={}", updated);
    }
  }

  /**
   * 迁移单主题 briefing 与 defaults。
   *
   * @param themeId 主题 ID
   * @param now     更新时间
   * @return 是否写库
   */
  private boolean migrateTheme(String themeId, LocalDateTime now) {
    boolean changed = false;
    Map<String, Object> briefing = profiles.loadBriefing(themeId);
    if (CollUtil.isNotEmpty(briefing)) {
      ScoringRubricView raw = ScoringRubricSupport.readFrom(briefing);
      if (needsEnsure(raw) || needsFrontierGuidance(themeId, raw)) {
        ScoringRubricView ensured =
            withFrontierGuidance(themeId, ScoringRubricSupport.ensureAudienceHook(raw));
        if (ensured != null && ScoringRubricSupport.isValid(ensured)) {
          ScoringRubricSupport.writeInto(briefing, ensured);
          profiles.saveBriefing(themeId, briefing, now);
          changed = true;
        }
      }
    }
    Map<String, Object> defaults = profiles.loadDefaults(themeId);
    if (CollUtil.isNotEmpty(defaults)) {
      ScoringRubricView raw = ScoringRubricSupport.readFrom(defaults);
      if (needsEnsure(raw) || needsFrontierGuidance(themeId, raw)) {
        ScoringRubricView ensured =
            withFrontierGuidance(themeId, ScoringRubricSupport.ensureAudienceHook(raw));
        if (ensured != null && ScoringRubricSupport.isValid(ensured)) {
          ScoringRubricSupport.writeInto(defaults, ensured);
          profiles.saveDefaults(themeId, defaults, now);
          changed = true;
        }
      }
    }
    return changed;
  }

  /**
   * AI 前沿种子主题是否仍是旧版打分指引。
   *
   * @param themeId 主题 ID
   * @param rubric  量表
   * @return true 需要改写 scoreGuidance
   */
  private static boolean needsFrontierGuidance(String themeId, ScoringRubricView rubric) {
    if (!BuiltinCatalogSeedService.HOT_NEWS_SEED_THEME_ID.equals(themeId) || rubric == null) {
      return false;
    }
    String guidance = StrUtil.nullToEmpty(rubric.getScoreGuidance());
    return !guidance.contains("打开欲");
  }

  /**
   * 种子主题写回打开欲导向的 scoreGuidance。
   *
   * @param themeId 主题 ID
   * @param rubric  已 ensure 的量表
   * @return 可能改写指引后的量表
   */
  private static ScoringRubricView withFrontierGuidance(String themeId, ScoringRubricView rubric) {
    if (rubric == null || !needsFrontierGuidance(themeId, rubric)) {
      return rubric;
    }
    return ScoringRubricView.builder()
        .version(rubric.getVersion())
        .generatedAt(rubric.getGeneratedAt())
        .scoreGuidance(
            "高分须先有打开欲与可转述钩子；官方/产品/多源优先，预印本 alone 不足以支撑主推；纯方法论文整体压低")
        .dimensions(rubric.getDimensions())
        .build();
  }

  /**
   * 是否缺少打开欲维或其权重不足。
   *
   * @param rubric 量表
   * @return true 需要 ensure
   */
  private static boolean needsEnsure(ScoringRubricView rubric) {
    if (rubric == null || CollUtil.isEmpty(rubric.getDimensions())) {
      return false;
    }
    ScoringRubricDimensionView hook = null;
    int maxOther = 0;
    for (ScoringRubricDimensionView dim : rubric.getDimensions()) {
      if (dim == null) {
        continue;
      }
      if (HotNewsScoringRubricKeys.AUDIENCE_HOOK.equals(dim.getKey())) {
        hook = dim;
      } else if (dim.getWeight() != null) {
        maxOther = Math.max(maxOther, dim.getWeight());
      }
    }
    if (hook == null) {
      return true;
    }
    int weight = hook.getWeight() == null ? 0 : hook.getWeight();
    return weight < HotNewsScoringRubricKeys.AUDIENCE_HOOK_WEIGHT || weight < maxOther;
  }
}
