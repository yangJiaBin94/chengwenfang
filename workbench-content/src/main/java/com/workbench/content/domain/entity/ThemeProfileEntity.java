package com.workbench.content.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 主题画像实体（问卷 briefing / defaults）。
 *
 * <p>主键为 (theme_id, profile_kind)；MyBatis-Plus 以 theme_id 为 {@link TableId}，查询须同时带 profile_kind。
 */
@TableName("theme_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeProfileEntity {
  /** 主题 ID。 */
  @TableId(value = "theme_id", type = IdType.INPUT)
  private String themeId;
  /** 画像种类：briefing / defaults。 */
  @TableField("profile_kind")
  private String profileKind;
  /** 原 schema_version。 */
  @TableField("schema_version")
  private Integer schemaVersion;
  /** 向导步骤。 */
  @TableField("wizard_step")
  private Integer wizardStep;
  /** naming.name。 */
  @TableField("naming_name")
  private String namingName;
  /** naming.description。 */
  @TableField("naming_description")
  private String namingDescription;
  /** naming.code。 */
  @TableField("naming_code")
  private String namingCode;
  /** naming.domain / defaults.domain。 */
  @TableField("domain")
  private String domain;
  /** naming.domain_custom。 */
  @TableField("domain_custom")
  private String domainCustom;
  /** audience.content_stance。 */
  @TableField("content_stance")
  private String contentStance;
  /** audience.readers_note。 */
  @TableField("readers_note")
  private String readersNote;
  /** audience.time_budget。 */
  @TableField("time_budget")
  private String timeBudget;
  /** goals.success_criteria。 */
  @TableField("success_criteria")
  private String successCriteria;
  /** scope.difficulty_curve。 */
  @TableField("difficulty_curve")
  private String difficultyCurve;
  /** structure.series_count_pref。 */
  @TableField("series_count_pref")
  private String seriesCountPref;
  /** structure.series_count_custom。 */
  @TableField("series_count_custom")
  private Integer seriesCountCustom;
  /** structure.lessons_per_series_pref。 */
  @TableField("lessons_per_series_pref")
  private String lessonsPerSeriesPref;
  /** structure.lessons_per_series_custom。 */
  @TableField("lessons_per_series_custom")
  private Integer lessonsPerSeriesCustom;
  /** structure.need_overview_lessons。 */
  @TableField("need_overview_lessons")
  private Boolean needOverviewLessons;
  /** scope.out_of_scope（热点 string；课程优先 items）。 */
  @TableField("out_of_scope")
  private String outOfScope;
  /** scope.locale。 */
  @TableField("locale")
  private String locale;
  /** radar.github_query。 */
  @TableField("github_query")
  private String githubQuery;
  /** expression.tone。 */
  @TableField("tone")
  private String tone;
  /** expression.length / defaults.length。 */
  @TableField("length_preference")
  private String lengthPreference;
  /** expression.boundaries。 */
  @TableField("boundaries")
  private String boundaries;
  /** lesson_forms JSON 数组文本。 */
  @TableField("lesson_forms")
  private String lessonForms;
  /** defaults.wordCountOption。 */
  @TableField("word_count_option")
  private String wordCountOption;
  /** 更新时间。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
