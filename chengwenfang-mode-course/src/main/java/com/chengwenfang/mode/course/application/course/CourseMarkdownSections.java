package com.chengwenfang.mode.course.application.course;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程 Markdown 拆分辅助（由 CourseMarkdownUtil 门面委派）。
 */
public final class CourseMarkdownSections {
  /** 工具类不可实例化。 */
  private CourseMarkdownSections() {}

  /**
   * 按二级标题优先、再按段落将 Markdown 切分为不超过 limit 的块。
   *
   * @param content 源 Markdown
   * @param limit   单块最大字符数
   * @return 非空块列表；空输入返回单元素空串列表
   */
  public static List<String> splitMarkdown(String content, int limit) {
    String stripped = content == null ? "" : content.strip();
    if (stripped.isEmpty()) {
      return List.of("");
    }
    // 在 ## 标题处切开，尽量保持小节完整
    String[] sections = stripped.split("(?m)(?=^##+\\s+)");
    List<String> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String section : sections) {
      if (section.isBlank()) {
        continue;
      }
      // 当前块装不下整节则先落盘
      if (current.length() > 0 && current.length() + section.length() > limit) {
        chunks.add(current.toString().strip());
        current.setLength(0);
      }
      if (section.length() <= limit) {
        if (current.length() > 0) {
          current.append("\n\n");
        }
        current.append(section);
        continue;
      }
      // 超长节再按空行段落切
      if (current.length() > 0) {
        chunks.add(current.toString().strip());
        current.setLength(0);
      }
      for (String paragraph : section.split("\n\n")) {
        if (current.length() > 0 && current.length() + paragraph.length() + 2 > limit) {
          chunks.add(current.toString().strip());
          current.setLength(0);
        }
        if (current.length() > 0) {
          current.append("\n\n");
        }
        current.append(paragraph);
      }
    }
    if (current.length() > 0) {
      chunks.add(current.toString().strip());
    }
    return chunks.isEmpty() ? List.of(stripped) : chunks;
  }
  /**
   * 规范化终端章节：重复的「本课小结」保留首个，重复的「下节课预告」保留末个。
   *
   * @param content 合并后的公众号正文
   * @return 去重后的正文
   */
  public static String normalizeTerminalSections(String content) {
    Pattern heading = Pattern.compile("(?m)^#{2,6}\\s*(?:📝\\s*)?(本课小结|下节课预告)\\s*$");
    Matcher matcher = heading.matcher(content == null ? "" : content);
    List<int[]> matches = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    while (matcher.find()) {
      matches.add(new int[]{matcher.start(), matcher.end()});
      labels.add(matcher.group(1));
    }
    String result = content == null ? "" : content;
    List<int[]> removals = new ArrayList<>();
    for (String section : List.of("本课小结", "下节课预告")) {
      List<Integer> indexes = new ArrayList<>();
      for (int index = 0; index < labels.size(); index++) {
        if (section.equals(labels.get(index))) {
          indexes.add(index);
        }
      }
      if (indexes.size() < 2) {
        continue;
      }
      // 小结保留第一次，预告保留最后一次（分块续写常见重复）
      int retain = "下节课预告".equals(section) ? indexes.get(indexes.size() - 1) : indexes.get(0);
      for (int idx : indexes) {
        if (idx == retain) {
          continue;
        }
        int start = matches.get(idx)[0];
        int following = result.length();
        for (int[] candidate : matches) {
          if (candidate[0] > start) {
            following = candidate[0];
            break;
          }
        }
        removals.add(new int[]{start, following});
      }
    }
    // 自后向前删除，避免下标漂移
    removals.sort((left, right) -> Integer.compare(right[0], left[0]));
    for (int[] removal : removals) {
      result = result.substring(0, removal[0]).stripTrailing()
          + "\n\n"
          + result.substring(removal[1]).stripLeading();
    }
    return result.replaceAll("\n{3,}", "\n\n").strip();
  }
  /**
   * 原版 Python 不对分段做「任务复述」硬拒收；越界的小结/预告标题由
   * {@link #sanitizeDraftSection} 裁掉。此方法保留为兼容入口，恒返回 null。
   *
   * @param content    已清洗的本节 Markdown
   * @param sectionKey {@code draft_opening} 等
   * @return 始终为 null
   */
  public static String sectionRejectionReason(String content, String sectionKey) {
    return null;
  }
  /**
   * 清洗分段原稿：丢掉复述提示词的前言，补上本节标题，并裁掉不该出现的小结/预告。
   *
   * @param content    模型原文
   * @param sectionKey 段落 typeKey，如 {@code draft_core}
   * @param heading    本节标题，如「开场与本课目标」
   * @return 可拼进原稿的 Markdown
   */
  public static String sanitizeDraftSection(String content, String sectionKey, String heading) {
    String text = content == null ? "" : content.strip();
    // 对齐 Python ContentGenerationWorkflow._sanitize_draft_section：
    // 缺 ## 时补规划标题；仅裁掉越界的小结/预告标题，不做其它清洗。
    if (!text.startsWith("##")) {
      text = "## " + heading + "\n\n" + text;
    }
    return cutReservedHeadings(text, sectionKey).strip();
  }
  /** 第一个 Markdown 标题行的起始下标；没有则返回 -1。 */
  public static int indexOfMarkdownHeading(String text) {
    Matcher matcher = Pattern.compile("(?m)^#{1,6}\\s+").matcher(text);
    return matcher.find() ? matcher.start() : -1;
  }
  public static String insertBeforeSummary(String content, String insertion) {
    String text = content == null ? "" : content;
    if (insertion == null || insertion.isBlank()) {
      return text;
    }
    Matcher summary = Pattern.compile("(?m)^#{2,6}\\s+.*本课小结.*$").matcher(text);
    if (!summary.find()) {
      return text.stripTrailing() + "\n\n" + insertion.strip();
    }
    return text.substring(0, summary.start()).stripTrailing()
        + "\n\n"
        + insertion.strip()
        + "\n\n"
        + text.substring(summary.start()).stripLeading();
  }
  public static List<int[]> findHeadingAnchors(String body, String prefix) {
    Pattern pattern = Pattern.compile("(?m)^" + Pattern.quote(prefix) + "\\s+.+$");
    Matcher matcher = pattern.matcher(body);
    List<int[]> anchors = new ArrayList<>();
    while (matcher.find()) {
      anchors.add(new int[]{matcher.start(), matcher.end()});
    }
    return anchors;
  }
  /**
   * 开场/原理/案例遇到小结或预告标题即截断；小结段、预告段只保留自己的标题。
   */
  public static String cutReservedHeadings(String content, String sectionKey) {
    String allowed = switch (sectionKey) {
      case "draft_summary" -> "本课小结";
      case "draft_preview" -> "下节课预告";
      default -> null;
    };
    StringBuilder output = new StringBuilder();
    boolean first = true;
    for (String line : content.strip().split("\\R", -1)) {
      Matcher heading = Pattern.compile("^#{2,6}\\s+(.+?)\\s*$").matcher(line.strip());
      if (heading.matches()
          && (heading.group(1).contains("本课小结") || heading.group(1).contains("下节课预告"))) {
        if (allowed != null && heading.group(1).contains(allowed) && output.isEmpty()) {
          output.append("## ").append(allowed);
          first = false;
          continue;
        }
        break;
      }
      if (!first) {
        output.append('\n');
      }
      output.append(line);
      first = false;
    }
    return output.toString();
  }
}
