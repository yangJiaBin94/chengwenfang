package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 写作价值打分的公共时间事实（与主题量表维度无关）。
 */
public final class TopicWritingScoreTimeFacts {
  /** 打分提示用时间格式。 */
  private static final DateTimeFormatter CLOCK =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private TopicWritingScoreTimeFacts() {}

  /**
   * 组装写入用户提示的「时间事实」段落。
   *
   * @param now         当前时间；空则用系统时钟
   * @param sources     选题来源（打分侧列表）；可空
   * @param publishFrom 选题聚合最早发布时间；可空（与列表卡片一致）
   * @param publishTo   选题聚合最晚发布时间；可空
   * @return 多行文本
   */
  public static String buildBlock(
      LocalDateTime now,
      List<HotNewsTopicSourceView> sources,
      LocalDateTime publishFrom,
      LocalDateTime publishTo) {
    LocalDateTime clock = now == null ? LocalDateTime.now() : now;
    StringBuilder out = new StringBuilder();
    out.append("当前时间：").append(CLOCK.format(clock)).append('\n');
    out.append("素材时间：")
        .append(summarizeMaterialTimes(sources, publishFrom, publishTo))
        .append('\n');
    out.append("说明：凡判断「新/旧/过时/热度退潮」等，必须对照以上时间；")
        .append("素材时间未知时相关维度不得满分，并在 reason/riskNote 写明时间未知。");
    return out.toString();
  }

  /**
   * 汇总素材时间：来源逐条 publishedAt 与选题聚合 publishFrom/To 取并集窗口（与列表卡片一致）。
   *
   * @param sources     来源列表
   * @param publishFrom 选题聚合最早；可空
   * @param publishTo   选题聚合最晚；可空
   * @return 摘要文案
   */
  public static String summarizeMaterialTimes(
      List<HotNewsTopicSourceView> sources,
      LocalDateTime publishFrom,
      LocalDateTime publishTo) {
    List<LocalDateTime> sourceTimes = collectSourcePublishedTimes(sources);
    LocalDateTime sourceEarliest = minTime(sourceTimes);
    LocalDateTime sourceLatest = maxTime(sourceTimes);

    List<LocalDateTime> merged = new ArrayList<>(sourceTimes);
    if (publishFrom != null) {
      merged.add(publishFrom);
    }
    if (publishTo != null) {
      merged.add(publishTo);
    }
    if (merged.isEmpty()) {
      if (CollUtil.isEmpty(sources)) {
        return "未知（无来源且选题无聚合发布时间）";
      }
      return "未知（来源未提供发布时间，选题亦无聚合发布时间）";
    }

    LocalDateTime earliest = minTime(merged);
    LocalDateTime latest = maxTime(merged);
    int sourceCount = sources == null ? 0 : sources.size();
    boolean widenedByTopic = !sourceTimes.isEmpty()
        && ((sourceEarliest != null && earliest.isBefore(sourceEarliest))
            || (sourceLatest != null && latest.isAfter(sourceLatest)));

    String suffix;
    if (sourceTimes.isEmpty()) {
      suffix = "回退自选题聚合 publishFrom/To；来源行无逐条发布时间";
    } else if (widenedByTopic) {
      suffix = "来源有发布时间 " + sourceTimes.size() + " / 共 " + sourceCount
          + "；已与选题聚合窗口合并";
    } else {
      suffix = "来源有发布时间 " + sourceTimes.size() + " / 共 " + sourceCount;
    }
    if (earliest.equals(latest)) {
      return CLOCK.format(earliest) + "（" + suffix + "）";
    }
    return "最早 " + CLOCK.format(earliest) + "，最晚 " + CLOCK.format(latest) + "（" + suffix + "）";
  }

  /**
   * @param times 时间列表
   * @return 最早；空则 null
   */
  private static LocalDateTime minTime(List<LocalDateTime> times) {
    if (times == null || times.isEmpty()) {
      return null;
    }
    return times.stream().min(Comparator.naturalOrder()).orElse(null);
  }

  /**
   * @param times 时间列表
   * @return 最晚；空则 null
   */
  private static LocalDateTime maxTime(List<LocalDateTime> times) {
    if (times == null || times.isEmpty()) {
      return null;
    }
    return times.stream().max(Comparator.naturalOrder()).orElse(null);
  }

  /**
   * 收集来源上的发布时间。
   *
   * @param sources 来源
   * @return 时间列表
   */
  static List<LocalDateTime> collectSourcePublishedTimes(List<HotNewsTopicSourceView> sources) {
    List<LocalDateTime> times = new ArrayList<>();
    if (sources == null) {
      return times;
    }
    for (HotNewsTopicSourceView source : sources) {
      if (source != null && source.getPublishedAt() != null) {
        times.add(source.getPublishedAt());
      }
    }
    return times;
  }

  /**
   * 单条来源发布时间展示；无则「时间未知」。
   *
   * @param publishedAt 发布时间
   * @return 展示串
   */
  public static String formatPublishedAt(LocalDateTime publishedAt) {
    if (publishedAt == null) {
      return "时间未知";
    }
    return CLOCK.format(publishedAt);
  }

  /**
   * 格式化来源列表行（含发布时间）；无 URL 也保留行，与素材时间汇总口径一致。
   *
   * @param sources 来源
   * @param maxRows 最多行数
   * @return 多行文本；无来源为「无」
   */
  public static String formatSourceLines(List<HotNewsTopicSourceView> sources, int maxRows) {
    if (CollUtil.isEmpty(sources)) {
      return "无";
    }
    int limit = Math.max(1, maxRows);
    StringBuilder lines = new StringBuilder();
    int index = 0;
    for (HotNewsTopicSourceView source : sources) {
      if (source == null) {
        continue;
      }
      index++;
      if (index > limit) {
        lines.append("- …其余来源已省略\n");
        break;
      }
      String site = StrUtil.firstNonBlank(source.getSiteLabel(), source.getSourceName(), "来源");
      String url = StrUtil.blankToDefault(source.getUrl(), "(无URL)");
      lines.append("- ")
          .append(site)
          .append(" | published=")
          .append(formatPublishedAt(source.getPublishedAt()))
          .append(" | ")
          .append(url);
      if (StrUtil.isNotBlank(source.getTitle())) {
        lines.append(" | ").append(StrUtil.maxLength(source.getTitle(), 80));
      }
      lines.append('\n');
    }
    return lines.length() == 0 ? "无" : lines.toString().trim();
  }
}
