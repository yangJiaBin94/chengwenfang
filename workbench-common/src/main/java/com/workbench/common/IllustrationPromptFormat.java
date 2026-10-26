package com.workbench.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 插图提示词唯一权威格式与脏写法规范化/裸残留清理。
 * <p>权威形态：{@code **插图位置：主题**} + {@code ```text} 围栏。课程与热点共用。
 */
public final class IllustrationPromptFormat {

  /**
   * 标准提示词块（与课程/热点 PROMPT_BLOCK 口径一致）。
   */
  public static final Pattern PROMPT_BLOCK = Pattern.compile(
      "(?<marker>(?:^|\\n)(?!\\s*!\\[)(?:\\s*#{1,6}\\s+|\\s*\\*\\*)"
          + "[^\\n]*(?:插图|提示词|prompt)[^\\n]*\\n+"
          + "(?:(?!\\s*```)[^\\n]*\\n+){0,4}"
          + "```[^\\n]*\\n(?<prompt>.*?)\\n```)",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /**
   * 无围栏：裸「插图位置」行 + 可选「提示词：」行/段。
   */
  private static final Pattern BARE_MARKER_AND_PROMPT = Pattern.compile(
      "(?ms)^(?!\\s*#{1,6}\\s)(?!\\s*\\*\\*)\\s*插图位置[:：]\\s*(?<subject>[^\\n]+)\\s*\\n+"
          + "(?:\\s*提示词[:：]\\s*)?(?<prompt>(?:(?!^\\s*(?:#{1,6}\\s|\\*\\*|插图位置[:：]|## )).)+?)"
          + "(?=\\n\\s*\\n|\\n\\s*(?:#{1,6}\\s|\\*\\*|插图位置[:：])|\\z)");

  /**
   * 括号脏写法：（插图位置…提示词…）可跨行。
   */
  private static final Pattern PAREN_ILLUSTRATION = Pattern.compile(
      "(?ms)（\\s*插图位置[:：]?\\s*(?<subject>[^\\n｜|]*?)(?:[｜|][^\\n]*?)?\\s*\\n?"
          + "\\s*提示词[:：]?\\s*(?:```[^\\n]*\\n)?(?<prompt>.*?)(?:\\n```)?\\s*）");

  /**
   * {@code ### 插图 N：…} + 引用块提示词。
   */
  private static final Pattern CAPTION_HEADING_QUOTE = Pattern.compile(
      "(?ms)^#{2,6}\\s*插图\\s*\\d*[:：]?\\s*(?<subject>[^\\n]*)\\n"
          + "(?:\\s*\\n)*(?<quotes>(?:>[^\\n]*(?:\\n|$))+)");

  /**
   * Midjourney / Keynote 风格裸生图段落特征。
   */
  private static final Pattern BARE_PROMPT_SIGNATURE = Pattern.compile(
      "(?i)(--v\\s*\\d+|Apple\\s*Keynote|16:9\\s*比例|深海蓝[^\\n]{0,20}三维科技|"
          + "高端\\s*SaaS\\s*仪表盘|霓虹边缘光|悬浮玻璃)");

  /** 工具类不可实例化。 */
  private IllustrationPromptFormat() {}

  /**
   * 将脏插图写法尽量收成权威标准块；无法可靠还原的脏段删除。
   * <p>已是标准 {@link #PROMPT_BLOCK} 的块保留并规范化标记行为 {@code **插图位置：…**}。
   *
   * @param content 原稿 Markdown；null 视为空
   * @return 规范化后的正文
   */
  public static String canonicalizeIllustrationPrompts(String content) {
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    String text = content.replace("\r\n", "\n").replace('\r', '\n');
    List<String> preserved = new ArrayList<>();
    // 先保护已有标准块，避免被脏模式误伤
    StringBuffer protectedBuf = new StringBuffer();
    Matcher standard = PROMPT_BLOCK.matcher(text);
    while (standard.find()) {
      String marker = standard.group("marker").stripLeading();
      String prompt = standard.group("prompt").strip();
      String subject = extractSubject(marker);
      String canonical = formatCanonicalBlock(subject, prompt);
      preserved.add(canonical);
      standard.appendReplacement(
          protectedBuf, Matcher.quoteReplacement("\n\n@@ILLUST_PROMPT_" + (preserved.size() - 1) + "@@\n\n"));
    }
    standard.appendTail(protectedBuf);
    String working = protectedBuf.toString();

    working = replaceDirtyWithCanonical(working, PAREN_ILLUSTRATION, preserved);
    working = replaceDirtyWithCanonical(working, CAPTION_HEADING_QUOTE, preserved);
    working = replaceDirtyWithCanonical(working, BARE_MARKER_AND_PROMPT, preserved);

    // 残留无法结构化的非正式插图说明行直接丢掉
    working = working.replaceAll("(?m)^\\s*（插图位置[^\\n]*$\\n?", "");
    working = working.replaceAll("(?m)^\\s*（提示词[^\\n]*$\\n?", "");
    working = working.replaceAll(
        "(?m)^(?!\\s*#{1,6}\\s)(?!\\s*\\*\\*)\\s*插图位置[:：][^\\n]*$\\n?", "");
    working = working.replaceAll("(?m)^\\s*提示词[:：][^\\n]*$\\n?", "");
    working = working.replaceAll(
        "(?ms)^#{2,6}\\s*插图\\s*\\d*[^\\n]*\\n(?:\\s*\\n)*(?:>[^\\n]*(?:\\n|$))+",
        "\n");

    for (int index = 0; index < preserved.size(); index++) {
      working = working.replace("@@ILLUST_PROMPT_" + index + "@@", preserved.get(index));
    }
    return working.replaceAll("\n{3,}", "\n\n").strip();
  }

  /**
   * 删除无标记、但明显是生图提示词的段落（公众号残留兜底）。
   *
   * @param content Markdown；null 视为空
   * @return 清理后正文
   */
  public static String stripBareImagePromptParagraphs(String content) {
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
    // 保护标准提示词块与代码围栏、图片行
    List<String> preserved = new ArrayList<>();
    String masked = PROMPT_BLOCK.matcher(normalized).replaceAll(match -> {
      preserved.add(match.group());
      return "\n@@BARE_STRIP_KEEP_" + (preserved.size() - 1) + "@@\n";
    });
    masked = Pattern.compile("(?s)```.*?```").matcher(masked).replaceAll(match -> {
      preserved.add(match.group());
      return "\n@@BARE_STRIP_KEEP_" + (preserved.size() - 1) + "@@\n";
    });

    String[] parts = masked.split("\n\\s*\n");
    List<String> kept = new ArrayList<>();
    for (String part : parts) {
      String stripped = part.strip();
      if (stripped.isEmpty()) {
        continue;
      }
      if (stripped.startsWith("@@BARE_STRIP_KEEP_")) {
        kept.add(stripped);
        continue;
      }
      if (isBareImagePromptParagraph(stripped)) {
        continue;
      }
      kept.add(stripped);
    }
    String joined = String.join("\n\n", kept);
    for (int index = 0; index < preserved.size(); index++) {
      joined = joined.replace("@@BARE_STRIP_KEEP_" + index + "@@", preserved.get(index));
    }
    return joined.replaceAll("\n{3,}", "\n\n").strip();
  }

  /**
   * 是否仍存在裸生图提示词残留（不含已标准围栏内的合法提示词）。
   *
   * @param content Markdown
   * @return 有裸残留则为 true
   */
  public static boolean hasBareImagePromptLeak(String content) {
    if (content == null || content.isBlank()) {
      return false;
    }
    String masked = PROMPT_BLOCK.matcher(content).replaceAll("\n");
    masked = Pattern.compile("(?s)```.*?```").matcher(masked).replaceAll("\n");
    for (String part : masked.replace("\r\n", "\n").split("\n\\s*\n")) {
      if (isBareImagePromptParagraph(part.strip())) {
        return true;
      }
    }
    return false;
  }

  /**
   * 拼标准插图提示词块。
   *
   * @param subject 插图主题/位置说明
   * @param prompt  生图提示词正文
   * @return 标准 Markdown 块
   */
  public static String formatCanonicalBlock(String subject, String prompt) {
    String safeSubject = (subject == null || subject.isBlank()) ? "正文插图" : subject.strip();
    String safePrompt = prompt == null ? "" : prompt.strip();
    return "**插图位置：" + safeSubject + "**\n\n```text\n" + safePrompt + "\n```";
  }

  /**
   * 判断一段是否像无标记的生图提示词（整段视觉描述，非正常技术正文）。
   *
   * @param paragraph 段落文本
   * @return 是裸提示词则为 true
   */
  public static boolean isBareImagePromptParagraph(String paragraph) {
    if (paragraph == null || paragraph.isBlank()) {
      return false;
    }
    String text = paragraph.strip();
    // 图片 Markdown / 标题 / token 不算
    if (text.startsWith("![") || text.startsWith("#") || text.startsWith("[[COURSE_")
        || text.startsWith("[[HOT_NEWS_") || text.startsWith("> 系列：")) {
      return false;
    }
    if (!BARE_PROMPT_SIGNATURE.matcher(text).find()) {
      return false;
    }
    // 过短可能是正文顺带提到风格词，要求一定长度
    String compact = text.replaceAll("\\s+", "");
    if (compact.length() < 40) {
      return false;
    }
    // 含明显教学叙述连接词则更像正文，放行
    if (text.contains("因此") || text.contains("例如") || text.contains("也就是说")
        || text.contains("下一课") || text.contains("本课")) {
      return false;
    }
    return true;
  }

  private static String replaceDirtyWithCanonical(
      String working, Pattern pattern, List<String> preserved) {
    Matcher matcher = pattern.matcher(working);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String subject = safeGroup(matcher, "subject");
      String prompt = safeGroup(matcher, "prompt");
      if (prompt == null || prompt.isBlank()) {
        // 引用块形式：quotes 组
        prompt = safeGroup(matcher, "quotes");
        if (prompt != null) {
          prompt = prompt.replaceAll("(?m)^>\\s?", "").strip();
        }
      }
      if (prompt == null || prompt.isBlank()) {
        matcher.appendReplacement(buffer, "\n");
        continue;
      }
      // 提示词行里可能仍带「提示词：」前缀
      prompt = prompt.replaceFirst("^\\s*提示词[:：]\\s*", "").strip();
      if (subject != null) {
        subject = subject.replaceFirst("^\\s*提示词[:：].*$", "").strip();
      }
      String canonical = formatCanonicalBlock(subject, prompt);
      preserved.add(canonical);
      matcher.appendReplacement(
          buffer, Matcher.quoteReplacement("\n\n@@ILLUST_PROMPT_" + (preserved.size() - 1) + "@@\n\n"));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String extractSubject(String marker) {
    if (marker == null || marker.isBlank()) {
      return "正文插图";
    }
    String line = marker.strip().split("\\R", 2)[0].strip();
    line = line.replaceAll("^#{1,6}\\s*", "");
    line = line.replaceAll("^\\*\\*|\\*\\*$", "").strip();
    line = line.replaceFirst("^插图位置[:：]\\s*", "");
    line = line.replaceFirst("^插图提示词[:：]?\\s*", "");
    line = line.replaceFirst("^提示词[:：]\\s*", "");
    return line.isBlank() ? "正文插图" : line;
  }

  private static String safeGroup(Matcher matcher, String name) {
    try {
      return matcher.group(name);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}
