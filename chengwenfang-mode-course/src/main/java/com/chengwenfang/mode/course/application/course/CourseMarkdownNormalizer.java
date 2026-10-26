package com.chengwenfang.mode.course.application.course;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程 Markdown 拆分辅助（由 CourseMarkdownUtil 门面委派）。
 */
public final class CourseMarkdownNormalizer {
  /** 工具类不可实例化。 */
  private CourseMarkdownNormalizer() {}

  /**
   * 将独立一行的 shell/Python 命令反引号改写为缩进代码行（对齐 Python
   * {@code _normalize_standalone_code_lines}）。
   */
  public static String normalizeStandaloneCodeLines(String content) {
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    Pattern command = Pattern.compile(
        "^(?:[A-Za-z]:[\\\\/]|[./~]|(?:python3?|pip3?|source|where|which|cd|deactivate)\\b|"
            + "\\.venv[\\\\/]|[A-Za-z_][\\w-]*\\s+(?:-[\\w-]+|[./\\\\]|[A-Za-z_]))");
    Pattern backtickLine = Pattern.compile("^\\s*`([^`\\n]+)`\\s*$");
    String[] lines = content.split("\\R", -1);
    List<String> normalized = new ArrayList<>();
    for (String line : lines) {
      Matcher match = backtickLine.matcher(line);
      if (match.matches()) {
        String value = match.group(1).strip();
        if (command.matcher(value).find()) {
          normalized.add("    " + value);
          continue;
        }
      }
      normalized.add(line);
    }
    return String.join("\n", normalized);
  }
  /** 去掉围栏代码后再做正文扫描，避免代码示例里的词误伤。 */
  public static String stripFencedCode(String text) {
    return (text == null ? "" : text).replaceAll("(?s)```.*?```", "");
  }
  /** 标题前的文字是否在复述任务，而不是课程正文。 */
  public static boolean looksLikeReasoningPreamble(String preamble) {
    if (preamble == null || preamble.isBlank()) {
      return false;
    }
    return preamble.contains("我们需要回答用户")
        || preamble.contains("用户要求")
        || preamble.contains("不要解释")
        || preamble.contains("我需要输出")
        || preamble.contains("我需要写")
        || preamble.contains("压缩后的完整课程原稿")
        || preamble.contains("只输出压缩后")
        || looksLikeTrailingReasoning(preamble);
  }
  /**
   * 压缩/整稿末尾常见的思维链尾巴（字数自检、输出格式说明等）。
   * 出现在「下节课预告」正文之后时应裁掉，否则终检会把「本课小结」计次打爆。
   */
  public static boolean looksLikeTrailingReasoning(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    return text.contains("现在计算")
        || text.contains("只输出完整")
        || text.contains("只输出压缩")
        || text.contains("输出格式")
        || text.contains("最终输出")
        || text.contains("输出前请")
        || text.contains("我们需要")
        || text.contains("我需要")
        || text.contains("用户说")
        || text.contains("用户要求")
        || text.contains("用户要我")
        || text.contains("总计约")
        || text.contains("检查公式")
        || text.contains("检查非中文")
        || text.contains("本课小结：约")
        || text.contains("下节课预告：约")
        || text.contains("本课小结、下节课预告")
        || text.contains("本课小结保留")
        || text.contains("下节课预告保留")
        || text.contains("正文要压缩")
        || text.contains("注意规则：")
        || text.contains("这个正文大概")
        || text.contains("大概不到")
        || Pattern.compile("约\\s*\\d+\\s*字").matcher(text).find()
        || Pattern.compile("\\d+\\s*字。?\\s*很好").matcher(text).find();
  }
  /**
   * 去掉全文标题前的推理前言，并裁掉「下节课预告」之后的思维链尾巴。
   * <p>原版 Python 压缩后不做专用裁剪；当前模型常在预告后继续自检字数，
   * 若不裁掉，终检 {@code content.count("本课小结") != 1} 会误杀。
   *
   * @param content Markdown 全文
   * @return 可提交的课程原稿
   */
  public static String sanitizeFullDraft(String content) {
    String text = content == null ? "" : content.strip();
    int headingAt = CourseMarkdownSections.indexOfMarkdownHeading(text);
    if (headingAt > 0 && looksLikeReasoningPreamble(text.substring(0, headingAt))) {
      text = text.substring(headingAt).strip();
    }
    return stripTrailingReasoningAfterPreview(text);
  }
  /**
   * 保留最后一个「下节课预告」标题及其正常正文，裁掉其后的推理/自检尾巴。
   */
  public static String stripTrailingReasoningAfterPreview(String content) {
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    Matcher matcher = Pattern.compile("(?m)^#{2,6}\\s+.*下节课预告.*$").matcher(content);
    int previewEnd = -1;
    while (matcher.find()) {
      previewEnd = matcher.end();
    }
    if (previewEnd < 0) {
      return content;
    }
    String head = content.substring(0, previewEnd);
    String[] lines = content.substring(previewEnd).split("\\R", -1);
    StringBuilder body = new StringBuilder();
    for (int index = 0; index < lines.length; index++) {
      String line = lines[index];
      String stripped = line.strip();
      if (stripped.isEmpty()) {
        // 预告段落后的空行先收下；若下一非空行已是推理则不再追加空行
        int next = index + 1;
        while (next < lines.length && lines[next].strip().isEmpty()) {
          next++;
        }
        if (next < lines.length && looksLikeTrailingReasoning(lines[next].strip())) {
          break;
        }
        body.append('\n');
        continue;
      }
      if (looksLikeTrailingReasoning(stripped)) {
        break;
      }
      // 预告之后不应再出现新的课程标题；若出现且像推理说明，直接截断
      if (stripped.matches("^#{1,6}\\s+.*") && !stripped.contains("下节课预告")) {
        break;
      }
      body.append(line).append('\n');
    }
    return (head + body).strip();
  }
  /**
   * 删除提示词正文为空的插图块（含封面空块）。
   */
  public static String stripEmptyPromptBlocks(String content) {
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    StringBuffer sb = new StringBuffer();
    Matcher matcher = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(content);
    while (matcher.find()) {
      String prompt = matcher.group("prompt").strip();
      if (prompt.isBlank()) {
        matcher.appendReplacement(sb, "\n");
      } else {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
      }
    }
    matcher.appendTail(sb);
    return sb.toString().replaceAll("\n{3,}", "\n\n").strip();
  }
  /**
   * 剔除夹在正文中的推理腔段落（压缩/分段模型自言自语）。
   * <p>按 {@code \\R\\R+} 分段，兼容 CRLF 原稿。
   */
  public static String stripInlineReasoningParagraphs(String content) {
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
    String[] parts = normalized.split("\n\\s*\n");
    List<String> kept = new ArrayList<>();
    for (String part : parts) {
      String stripped = part.strip();
      if (stripped.isEmpty()) {
        continue;
      }
      if (looksLikeInlineReasoning(stripped)) {
        continue;
      }
      kept.add(stripped);
    }
    return String.join("\n\n", kept).replaceAll("\n{3,}", "\n\n").strip();
  }
  /**
   * 压缩脏稿常见「半截草稿 + 推理 + 完整草稿」：若有多个一级标题，保留最后一份含小结/预告的完整稿。
   */
  public static String preferLastCompleteDraft(String content) {
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    String text = content.replace("\r\n", "\n").replace('\r', '\n');
    Matcher matcher = Pattern.compile("(?m)^#\\s+.+").matcher(text);
    List<Integer> starts = new ArrayList<>();
    while (matcher.find()) {
      starts.add(matcher.start());
    }
    if (starts.size() < 2) {
      return text;
    }
    for (int i = starts.size() - 1; i >= 0; i--) {
      String candidate = text.substring(starts.get(i)).strip();
      if (candidate.contains("本课小结") && candidate.contains("下节课预告")) {
        return candidate;
      }
    }
    return text.substring(starts.get(starts.size() - 1)).strip();
  }
  /**
   * 压缩/拼装后的可发布原稿出口：规范化提示词、去空提示词、去推理腔、对齐插图数量。
   */
  public static String finalizePublishableDraft(
      String content, int minImages, int maxImages, String courseTitle) {
    String text = content == null ? "" : content;
    text = text.replace("\r\n", "\n").replace('\r', '\n');
    text = sanitizeFullDraft(text);
    text = CourseMarkdownImages.canonicalizeIllustrationPrompts(text);
    text = stripEmptyPromptBlocks(text);
    text = CourseMarkdownImages.stripMalformedIllustrationNotes(text);
    text = stripInlineReasoningParagraphs(text);
    text = preferLastCompleteDraft(text);
    text = sanitizeFullDraft(text);
    text = CourseMarkdownImages.ensureInlinePromptCount(
        text, minImages, courseTitle, CourseMarkdownImages.extractIllustrationSubjects(text, courseTitle));
    text = CourseMarkdownImages.limitInlinePrompts(text, maxImages);
    text = CourseMarkdownImages.distributeInlinePrompts(text);
    text = stripEmptyPromptBlocks(text);
    return sanitizeFullDraft(text);
  }
  public static boolean looksLikeInlineReasoning(String paragraph) {
    if (paragraph == null || paragraph.isBlank()) {
      return false;
    }
    String text = paragraph.strip();
    // 标题与标准插图块不是推理（含围栏的段落用行级特征判断，避免单反引号误伤）
    if (text.startsWith("#")) {
      return false;
    }
    if (text.contains("```")) {
      return false;
    }
    if (looksLikeTrailingReasoning(text) || looksLikeReasoningPreamble(text)) {
      return true;
    }
    return text.contains("正文要压缩")
        || text.contains("现在，我们还需要")
        || text.contains("现在我们还需要")
        || text.contains("现在，我们需要决定")
        || text.contains("现在我们需要决定")
        || text.contains("让我起草")
        || text.contains("需要注意：用户要求")
        || text.contains("需要保留“操作截图”")
        || text.contains("需要保留「操作截图」")
        || text.contains("如果检查器期望")
        || text.contains("遵循不编造")
        || text.contains("注意规则：")
        || text.contains("我可以保留")
        || text.contains("原稿没有，不能编造")
        || text.contains("也许在“需要压缩的原稿”")
        || text.contains("我将写一个简洁版本");
  }
}
