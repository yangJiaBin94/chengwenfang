package com.chengwenfang.mode.hotnews.domain;

import com.chengwenfang.mode.hotnews.facade.enums.InferredSourceKind;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 热点选题纯函数工具：标题相似度、聚类评分与每日雷达限额。
 * <p>自 Python HotNewsService 移植，无副作用。
 */
public final class TopicMathUtil {
  public static final int DAILY_TOPIC_LIMIT = 10;
  private static final Pattern NON_TOKEN = Pattern.compile("[^a-z0-9\\u4e00-\\u9fff]+");

  private TopicMathUtil() {}

  /**
   * 计算本批还需生成的 AI 主题种子条数。
   * <p>不足 {@link #DAILY_TOPIC_LIMIT} 时按差额补齐；已满但尚无 AI 种子时仍补 1 条参与评分排序；
   * 已满且已有种子时返回 0（重跑不重复灌入）。
   *
   * @param batchTopicCount     本批当前选题总数（含已有 AI 种子）
   * @param existingAiSeedCount 本批已有 AI 种子数
   * @return 需要新生成的条数（≥0）
   */
  public static int aiThemeSeedNeed(int batchTopicCount, int existingAiSeedCount) {
    int safeBatch = Math.max(0, batchTopicCount);
    int safeSeeds = Math.max(0, existingAiSeedCount);
    int deficit = Math.max(0, DAILY_TOPIC_LIMIT - safeBatch);
    if (deficit > 0) {
      return deficit;
    }
    return safeSeeds > 0 ? 0 : 1;
  }

  /** 评分用的单条来源证据（可信度由 AI 写作评分判断，此处不含信任等级）。 */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class SourceEvidence {
    /** 来源 URL。 */
    private final String url;
    /** 渠道 ID。 */
    private final String channelId;
    /** 观测时间。 */
    private final LocalDateTime seenAt;
  }

  /** 选题聚类综合评分结果。 */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class ClusterScore {
    /** 热度分。 */
    private final int heatScore;
    /** 写作分。 */
    private final int writingScore;
    /** 置信分。 */
    private final int confidenceScore;
    /** 速度分。 */
    private final int velocityScore;
    /** 新鲜度分。 */
    private final int freshnessScore;
    /** 证据分。 */
    private final int evidenceScore;
    /** 生命周期标签。 */
    private final String lifecycle;
    /** 评分原因说明。 */
    private final String scoreReason;
    /** 分项明细。 */
    private final TopicScoreDetails scoreDetails;
  }

  /** 归一化标题：小写、去标点，截断至 300 字符。 */
  public static String cleanTitle(String value) {
    if (value == null) {
      return "";
    }
    String cleaned = NON_TOKEN.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
    return cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned;
  }

  /** 标题是否相似（归一化 Levenshtein 比率 ≥ 0.72，近似 difflib.SequenceMatcher）。 */
  public static boolean similar(String left, String right) {
    String cleanedLeft = cleanTitle(left);
    String cleanedRight = cleanTitle(right);
    if (cleanedLeft.isEmpty() || cleanedRight.isEmpty()) {
      return false;
    }
    if (cleanedLeft.equals(cleanedRight)) {
      return true;
    }
    int distance = levenshtein(cleanedLeft, cleanedRight);
    double ratio = 1.0 - ((double) distance / Math.max(cleanedLeft.length(), cleanedRight.length()));
    return ratio >= 0.72;
  }

  /** 按日保留 writingScore 最高的前 {@link #DAILY_TOPIC_LIMIT} 条。 */
  public static <T> List<T> keepTopPerDay(
      List<T> rows, java.util.function.Function<T, String> dayKey, java.util.function.ToIntFunction<T> writingScore) {
    Map<String, List<T>> byDay = new LinkedHashMap<>();
    for (T row : rows) {
      byDay.computeIfAbsent(dayKey.apply(row), ignored -> new ArrayList<>()).add(row);
    }
    List<T> kept = new ArrayList<>();
    for (List<T> daily : byDay.values()) {
      daily.sort(Comparator.comparingInt(writingScore).reversed());
      kept.addAll(daily.subList(0, Math.min(DAILY_TOPIC_LIMIT, daily.size())));
    }
    return kept;
  }

  /**
   * 计算采集事实侧评分（热度/速度/新鲜度/证据/生命周期）。
   *
   * <p>写作价值分不再由规则合成，固定为 0，由主题量表 AI 评分写入。
   */
  public static ClusterScore scoreCluster(
      String title, String summary, List<SourceEvidence> sources, LocalDateTime now) {
    LocalDateTime clock = now == null ? LocalDateTime.now() : now;
    int sourceCount = sources.size();
    int unknownDateCount = 0;
    Set<String> domains = new LinkedHashSet<>();
    int recentCount = 0;
    LocalDateTime recentCutoff = clock.minus(24, ChronoUnit.HOURS);
    LocalDateTime lastSeen = null;

    for (SourceEvidence source : sources) {
      LocalDateTime seen = source.seenAt();
      if (seen == null) {
        unknownDateCount++;
      } else {
        if (lastSeen == null || seen.isAfter(lastSeen)) {
          lastSeen = seen;
        }
        if (!seen.isBefore(recentCutoff)) {
          recentCount++;
        }
      }
      domains.add(sourceIdentity(source.url(), source.channelId()));
    }

    int independentCount = domains.size();
    // 无明确发布时间时不按「刚采到」给满分，避免旧闻被当成今日热点
    double freshnessHours = lastSeen == null
        ? 720.0
        : Math.max(0.0, ChronoUnit.SECONDS.between(lastSeen, clock) / 3600.0);
    int velocityScore = Math.min(100, (int) Math.round(
        (recentCount / (double) Math.max(sourceCount, 1)) * 60 + recentCount * 10));
    int freshnessScore = freshnessScore(freshnessHours);
    // 单来源证据偏弱；可信度不再按人工角色分桶，交由 AI 写作评分判断
    int weakPenalty = sourceCount == 1 ? 12 : 0;
    int evidenceScore = Math.max(0, Math.min(100,
        20
            + sourceCount * 12
            + independentCount * 18
            - weakPenalty
            - unknownDateCount * 5));

    int heatScore = Math.min(100, (int) Math.round(
        18
            + sourceCount * 10
            + independentCount * 12
            + velocityScore * 0.25
            + freshnessScore * 0.15));
    String lifecycle = lifecycle(sourceCount, heatScore, velocityScore, freshnessScore, evidenceScore);
    String reason = lifecycleLabel(lifecycle) + "：" + sourceCount + " 个来源，"
        + independentCount + " 个独立来源，近 24 小时新增 " + recentCount + " 个；"
        + "证据分 " + evidenceScore + "，速度分 " + velocityScore + "，"
        + "明确发布时间 " + (sourceCount - unknownDateCount) + "/" + sourceCount
        + "。写作价值待按主题量表 AI 评分。";
    if (reason.length() > 1000) {
      reason = reason.substring(0, 1000);
    }
    List<String> sourceTypes = inferSourceTypes(sources);
    List<String> signals = scoreSignals(
        independentCount, recentCount, lifecycle, evidenceScore, unknownDateCount, sourceTypes);
    TopicScoreDetails details = TopicScoreDetails.builder()
        .sourceCount(sourceCount)
        .officialCount(0)
        .primaryCount(0)
        .secondaryCount(0)
        .communityCount(0)
        .unknownDateCount(unknownDateCount)
        .sourceTypes(sourceTypes)
        .independentSourceCount(independentCount)
        .recentSourceCount(recentCount)
        .aiHits(0)
        .depthHits(0)
        .sourceGrowthRatio(Math.round(recentCount * 100.0 / Math.max(sourceCount, 1)) / 100.0)
        .freshnessHours(Math.round(freshnessHours * 10.0) / 10.0)
        .signals(signals)
        .metricNote("热度和速度为公开来源增量代理指标；来源可信度由主题量表 AI 评分判断")
        .build();
    // writingScore 由主题量表 AI 评分写入，此处仅保留采集事实分
    return new ClusterScore(
        heatScore,
        0,
        evidenceScore,
        velocityScore,
        freshnessScore,
        evidenceScore,
        lifecycle,
        reason,
        details);
  }

  static List<String> inferSourceTypes(List<SourceEvidence> sources) {
    Set<String> types = new LinkedHashSet<>();
    for (SourceEvidence source : sources) {
      String host = hostOf(source.url());
      if (host.contains(InferredSourceKind.GITHUB.getValue())) {
        types.add(InferredSourceKind.GITHUB.getValue());
      } else if (host.contains(InferredSourceKind.ARXIV.getValue())) {
        types.add(InferredSourceKind.ARXIV.getValue());
      } else {
        types.add(InferredSourceKind.WEB.getValue());
      }
    }
    return new ArrayList<>(types);
  }

  static List<String> scoreSignals(
      int independentCount,
      int recentCount,
      String lifecycle,
      int evidenceScore,
      int unknownDateCount,
      List<String> sourceTypes) {
    List<String> signals = new ArrayList<>();
    if (independentCount >= 2) {
      signals.add("多独立来源复现");
    }
    if (recentCount > 0) {
      signals.add("24 小时内有新增");
    }
    if ("rising".equals(lifecycle)) {
      signals.add("公开来源增量上升");
    }
    if (evidenceScore < 55) {
      signals.add("证据仍需补强");
    }
    if (unknownDateCount > 0) {
      signals.add(unknownDateCount + " 个来源发布时间不明确");
    }
    if (sourceTypes != null && sourceTypes.contains(InferredSourceKind.GITHUB.getValue())) {
      signals.add("包含 GitHub 开源项目来源");
    }
    if (signals.isEmpty()) {
      signals.add("信号较弱，建议观察");
    }
    return signals;
  }

  static int freshnessScore(double hours) {
    if (hours <= 6) {
      return 100;
    }
    if (hours <= 24) {
      return 80;
    }
    if (hours <= 72) {
      return 60;
    }
    if (hours <= 168) {
      return 35;
    }
    return 15;
  }

  static String lifecycle(
      int sourceCount,
      int heatScore,
      int velocityScore,
      int freshnessScore,
      int evidenceScore) {
    if (sourceCount <= 1 && freshnessScore >= 80) {
      return "early";
    }
    if (velocityScore >= 65 && sourceCount >= 2) {
      return "rising";
    }
    if (heatScore >= 70 && freshnessScore >= 60) {
      return "peak";
    }
    if (freshnessScore <= 35 && evidenceScore >= 70) {
      return "evergreen";
    }
    return "cooling";
  }

  static String lifecycleLabel(String value) {
    return switch (value) {
      case "early" -> "早期上升";
      case "rising" -> "快速上升";
      case "peak" -> "热度高峰";
      case "cooling" -> "热度回落";
      case "evergreen" -> "深度储备";
      default -> value;
    };
  }

  static String sourceIdentity(String url, String channelId) {
    String host = hostOf(url);
    if (host != null && !host.isBlank()) {
      return host;
    }
    return channelId == null ? "" : channelId;
  }

  static String hostOf(String url) {
    if (url == null || url.isBlank()) {
      return "";
    }
    try {
      String host = URI.create(url.trim()).getHost();
      if (host == null) {
        return "";
      }
      host = host.toLowerCase(Locale.ROOT);
      return host.startsWith("www.") ? host.substring(4) : host;
    } catch (Exception ignored) {
      return "";
    }
  }

  static int levenshtein(String left, String right) {
    int[] prev = new int[right.length() + 1];
    int[] curr = new int[right.length() + 1];
    for (int col = 0; col <= right.length(); col++) {
      prev[col] = col;
    }
    for (int row = 1; row <= left.length(); row++) {
      curr[0] = row;
      char ca = left.charAt(row - 1);
      for (int col = 1; col <= right.length(); col++) {
        int cost = ca == right.charAt(col - 1) ? 0 : 1;
        curr[col] = Math.min(Math.min(curr[col - 1] + 1, prev[col] + 1), prev[col - 1] + cost);
      }
      int[] tmp = prev;
      prev = curr;
      curr = tmp;
    }
    return prev[right.length()];
  }
}
