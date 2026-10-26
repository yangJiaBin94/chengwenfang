package com.workbench.common;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 按主题信号判定标题（及可选正文摘要）是否明显相关。
 * <p>本地只认实词和少量中英同义词，命中即留。未命中不算硬丢弃，由调用方整包交给模型；模型失败则留下。
 */
public final class ThemeTitleRelevance {
  private ThemeTitleRelevance() {}

  /** 汉字滑窗最短长度（覆盖「中老年」「认知衰退」，不切两字以免「认知」误伤）。 */
  private static final int HAN_WINDOW_MIN = 3;
  /** 汉字滑窗最长长度。长词只切到 4 字，避免无意义碎片。 */
  private static final int HAN_WINDOW_MAX = 4;
  /** 整段汉字不超过该长度时直接当锚点。 */
  private static final int HAN_PHRASE_MAX = 6;
  /** 拉丁同义词至少多长才允许「opensource」这种包含匹配。 */
  private static final int LATIN_CONTAINS_MIN = 6;
  /** 连续汉字。 */
  private static final Pattern HAN_RUN = Pattern.compile("\\p{IsHan}+");
  /** 连续拉丁词，保留连字符以便识别 open-source。 */
  private static final Pattern LATIN_RUN = Pattern.compile("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*");

  /**
   * 主题相关性判定用的信号包。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Profile {
    /** 主题名；可空。 */
    private final String themeName;
    /** 搜索词；可空。 */
    private final List<String> searchQueries;
    /** 关注实体/要点；可空。 */
    private final List<String> mustWatch;

    /**
     * 规范化空列表。
     *
     * @return 不可变语义的安全 Profile
     */
    public Profile normalized() {
      return new Profile(
          StrUtil.trim(StrUtil.nullToEmpty(themeName)),
          searchQueries == null ? List.of() : List.copyOf(searchQueries),
          mustWatch == null ? List.of() : List.copyOf(mustWatch));
    }

    /**
     * 是否缺少可用信号。
     *
     * @return 无主题名且无查询/关注项时 true
     */
    public boolean isBlank() {
      Profile safe = normalized();
      return StrUtil.isBlank(safe.themeName)
          && CollUtil.isEmpty(safe.searchQueries)
          && CollUtil.isEmpty(safe.mustWatch);
    }
  }

  /**
   * 判定结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Decision {
    /** 本地词典是否已确认相关。 */
    private final boolean relevant;
    /** 词典未命中、需要整包问模型。空标题不是这类。 */
    private final boolean uncertain;
    /** 简短原因。 */
    private final String reason;

    /**
     * 是否应直接丢弃、不必问模型。
     *
     * @return 仅空文本等无法判断的情况
     */
    public boolean hardDrop() {
      return !relevant && !uncertain;
    }
  }

  /**
   * 仅按标题判定。
   *
   * @param profile 主题信号
   * @param title   标题
   * @return 判定
   */
  public static Decision judge(Profile profile, String title) {
    return judge(profile, title, null);
  }

  /**
   * 按标题与可选摘要判定。
   *
   * @param profile   主题信号
   * @param title     标题
   * @param factBrief 事实速览或摘要；可空
   * @return 判定
   */
  public static Decision judge(Profile profile, String title, String factBrief) {
    Profile safe = profile == null ? new Profile("", List.of(), List.of()) : profile.normalized();
    if (safe.isBlank()) {
      return new Decision(true, false, "无主题信号，放行");
    }
    String raw = StrUtil.nullToEmpty(title) + " " + StrUtil.nullToEmpty(factBrief);
    if (StrUtil.isBlank(normalizeText(raw))) {
      return new Decision(false, false, "标题为空，无法判断主题相关");
    }
    List<String> anchors = buildAnchors(safe);
    if (anchors.isEmpty()) {
      return new Decision(true, false, "无可用锚点，放行");
    }
    for (String anchor : anchors) {
      if (textContains(raw, anchor)) {
        return new Decision(true, false, "命中主题词：" + anchor);
      }
    }
    return new Decision(false, true, "标题与主题信号无明显重合");
  }

  /**
   * 是否应因主题不相关而跳过。
   * <p>词典未命中视为跳过。雷达采集不要用这个方法，未命中要留给模型。
   *
   * @param profile 主题信号
   * @param title   标题
   * @return true 表示应丢弃
   */
  public static boolean shouldSkip(Profile profile, String title) {
    return !judge(profile, title).relevant();
  }

  /**
   * 是否应因主题不相关而跳过（含摘要）。
   *
   * @param profile   主题信号
   * @param title     标题
   * @param factBrief 摘要
   * @return true 表示应丢弃
   */
  public static boolean shouldSkip(Profile profile, String title, String factBrief) {
    return !judge(profile, title, factBrief).relevant();
  }

  private static List<String> buildAnchors(Profile profile) {
    Set<String> anchors = new LinkedHashSet<>();
    addPhraseAnchors(anchors, profile.themeName());
    if (profile.searchQueries() != null) {
      for (String query : profile.searchQueries()) {
        addPhraseAnchors(anchors, query);
      }
    }
    if (profile.mustWatch() != null) {
      for (String watch : profile.mustWatch()) {
        addPhraseAnchors(anchors, watch);
      }
    }
    expandSynonyms(anchors);
    return new ArrayList<>(anchors);
  }

  private static void addPhraseAnchors(Set<String> anchors, String phrase) {
    if (StrUtil.isBlank(phrase)) {
      return;
    }
    String lowered = phrase.toLowerCase(Locale.ROOT);
    Matcher han = HAN_RUN.matcher(lowered);
    while (han.find()) {
      addHanAnchors(anchors, han.group());
    }
    Matcher latin = LATIN_RUN.matcher(lowered);
    while (latin.find()) {
      String word = latin.group().replace("-", "");
      if (word.length() >= 2) {
        anchors.add(word);
      }
    }
  }

  private static void addHanAnchors(Set<String> anchors, String run) {
    if (run.length() >= HAN_WINDOW_MIN && run.length() <= HAN_PHRASE_MAX) {
      anchors.add(run);
    }
    if (run.length() > HAN_WINDOW_MAX) {
      int maxLen = Math.min(HAN_WINDOW_MAX, run.length());
      for (int len = HAN_WINDOW_MIN; len <= maxLen; len++) {
        for (int index = 0; index + len <= run.length(); index++) {
          anchors.add(run.substring(index, index + len));
        }
      }
    }
    for (SynonymGroup group : SynonymGroup.GROUPS) {
      for (String key : group.keys) {
        if (!isLatin(key) && key.length() >= 2 && run.contains(key)) {
          anchors.add(key);
        }
      }
    }
  }

  private static void expandSynonyms(Set<String> anchors) {
    List<String> snapshot = new ArrayList<>(anchors);
    for (SynonymGroup group : SynonymGroup.GROUPS) {
      if (!group.triggeredBy(snapshot)) {
        continue;
      }
      anchors.addAll(group.aliases);
    }
  }

  private static String normalizeText(String value) {
    if (value == null) {
      return "";
    }
    return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "");
  }

  private static boolean textContains(String raw, String anchor) {
    if (StrUtil.isBlank(anchor)) {
      return false;
    }
    if (isLatin(anchor)) {
      Matcher latin = LATIN_RUN.matcher(raw.toLowerCase(Locale.ROOT));
      while (latin.find()) {
        String word = latin.group().replace("-", "");
        if (word.equals(anchor)) {
          return true;
        }
        if (anchor.length() >= LATIN_CONTAINS_MIN && word.contains(anchor)) {
          return true;
        }
      }
      return false;
    }
    return normalizeText(raw).contains(anchor);
  }

  private static boolean isLatin(String value) {
    return StrUtil.isNotBlank(value) && value.chars().allMatch(ch -> ch < 128);
  }

  /**
   * 一组同义词：主题信号里出现任一 key，就把 aliases 全部加入锚点。
   */
  private static final class SynonymGroup {
    private static final List<SynonymGroup> GROUPS = List.of(
        new SynonymGroup(
            List.of("大模型", "模型", "llm"),
            List.of("大模型", "模型", "llm", "model", "models")),
        new SynonymGroup(List.of("开源"), List.of("开源", "opensource")),
        new SynonymGroup(List.of("ai", "人工智能"), List.of("ai", "人工智能")),
        new SynonymGroup(List.of("agent"), List.of("agent")));

    private final List<String> keys;
    private final List<String> aliases;

    private SynonymGroup(List<String> keys, List<String> aliases) {
      this.keys = keys;
      this.aliases = aliases;
    }

    private boolean triggeredBy(List<String> anchors) {
      for (String anchor : anchors) {
        for (String key : keys) {
          if (anchor.equals(key) || (!isLatin(key) && anchor.contains(key))) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
