package com.chengwenfang.mode.course.application.course;

import com.chengwenfang.mode.course.application.prompt.CoursePrompts;

import com.chengwenfang.common.IllustrationPromptFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程 Markdown 拆分辅助（由 CourseMarkdownUtil 门面委派）。
 */
public final class CourseMarkdownImages {
  /** 工具类不可实例化。 */
  private CourseMarkdownImages() {}

  /**
   * 判断提示词块是否为封面图（marker/prompt 含封面语义）。
   *
   * @param marker 标题/加粗标记行
   * @param prompt fenced 内提示词正文
   * @return true 表示封面提示词
   */
  public static boolean isCoverPrompt(String marker, String prompt) {
    String markerText = marker == null ? "" : marker;
    String promptText = prompt == null ? "" : prompt;
    return markerText.contains("封面") || promptText.contains("课程封面") || promptText.contains("公众号封面");
  }
  /**
   * 扫描全文中的插图提示词块。
   *
   * @param content Markdown；null 视为空
   * @return 命中列表（含 marker、prompt 与起止下标）
   */
  public static List<CourseMarkdownUtil.PromptHit> findPrompts(String content) {
    List<CourseMarkdownUtil.PromptHit> out = new ArrayList<>();
    Matcher matcher = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(content == null ? "" : content);
    while (matcher.find()) {
      String marker = matcher.group("marker").stripLeading();
      String prompt = matcher.group("prompt").strip();
      out.add(new CourseMarkdownUtil.PromptHit(marker, prompt, matcher.start(), matcher.end()));
    }
    return out;
  }
  /**
   * 为公众号转换准备源文：封面提示词删除，内文提示词替换为 {@code [[COURSE_IMAGE_N]]}。
   *
   * @param draft 课程原稿
   * @return 替换后的正文与 token 顺序列表
   */
  public static CourseMarkdownUtil.WechatSource prepareWechatSource(String draft) {
    List<String> tokens = new ArrayList<>();
    Matcher matcher = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(draft == null ? "" : draft);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String marker = matcher.group("marker");
      String prompt = matcher.group("prompt").strip();
      if (prompt.isBlank()) {
        // 空围栏不产生 token，直接丢掉
        matcher.appendReplacement(sb, "\n");
        continue;
      }
      if (isCoverPrompt(marker, prompt)) {
        // 封面由页眉统一插入 token，不在正文留提示词
        matcher.appendReplacement(sb, "\n");
        continue;
      }
      String token = "[[COURSE_IMAGE_" + (tokens.size() + 1) + "]]";
      tokens.add(token);
      matcher.appendReplacement(sb, Matcher.quoteReplacement("\n\n" + token + "\n"));
    }
    matcher.appendTail(sb);
    return new CourseMarkdownUtil.WechatSource(sb.toString(), List.copyOf(tokens));
  }
  /**
   * 确定性公众号块转换：去分隔线/代码围栏/表格线，表格改列表，清洗一级标题课次编号。
   * <p>进入 fenced 块后整段丢弃至闭合围栏，避免只删 {@code ```} 行却把提示词正文留成段落。
   *
   * @param source 源 Markdown 块
   * @return 转换后文本
   */
  public static String deterministicWechatChunk(String source) {
    List<String> converted = new ArrayList<>();
    boolean insideFence = false;
    for (String line : (source == null ? "" : source).strip().split("\\R", -1)) {
      String stripped = line.strip();
      // 围栏开闭：整块丢弃（含围栏内正文）
      if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
        insideFence = !insideFence;
        continue;
      }
      if (insideFence) {
        continue;
      }
      // 水平线直接丢弃
      if (stripped.matches("(?:---+|\\*\\*\\*+|___+)")) {
        continue;
      }
      // 表格对齐行丢弃
      if (stripped.matches("\\|?(?:\\s*:?-+:?\\s*\\|)+\\s*:?-+:?\\s*\\|?")) {
        continue;
      }
      // 表格数据行改为无点列表
      if (stripped.startsWith("|") && stripped.endsWith("|")) {
        String[] cells = stripped.replaceAll("^\\||\\|$", "").split("\\|");
        StringBuilder row = new StringBuilder("- ");
        for (int index = 0; index < cells.length; index++) {
          if (index > 0) {
            row.append("｜");
          }
          row.append(cells[index].strip());
        }
        converted.add(row.toString());
        continue;
      }
      if (line.startsWith("# ")) {
        String title = line.substring(2).replaceAll("第\\s*\\d+\\s*[课篇章][：:\\s｜|\\-]*", "").strip();
        converted.add("# " + title);
        continue;
      }
      converted.add(line);
    }
    return String.join("\n", converted).strip();
  }
  /**
   * 将配图档位解析为 [min, max] 插图数量区间。
   *
   * @param imageCountOption 档位键，如 {@code 3-5}
   * @return 长度为 2 的数组：下限、上限
   */
  public static int[] imageBounds(String imageCountOption) {
    return switch (imageCountOption == null ? "" : imageCountOption) {
      case "up-to-3" -> new int[]{0, 3};
      case "3-5" -> new int[]{3, 5};
      case "5-7" -> new int[]{5, 7};
      default -> new int[]{0, 999};
    };
  }
  /**
   * 压缩丢失的插图提示词块插回「本课小结」之前。
   *
   * @param content  压缩后正文
   * @param required 压缩前收集的提示词块
   * @return 回填后的正文
   */
  public static String restoreMissingPromptBlocks(String content, List<CourseMarkdownUtil.PromptHit> required) {
    if (required == null || required.isEmpty()) {
      return content == null ? "" : content;
    }
    Set<String> existing = new HashSet<>();
    for (CourseMarkdownUtil.PromptHit hit : findPrompts(content)) {
      if (hit.prompt() != null && !hit.prompt().isBlank()) {
        existing.add(hit.prompt().strip());
      }
    }
    List<String> missing = new ArrayList<>();
    for (CourseMarkdownUtil.PromptHit hit : required) {
      if (hit.prompt() == null || hit.prompt().isBlank()) {
        continue;
      }
      if (!existing.contains(hit.prompt().strip())) {
        missing.add(hit.marker().strip());
      }
    }
    if (missing.isEmpty()) {
      return content;
    }
    return CourseMarkdownSections.insertBeforeSummary(content, String.join("\n\n", missing));
  }
  /**
   * 从二级标题抽取可用于补插图的具体题材，避免一律回落到课名。
   *
   * @param content     原稿
   * @param courseTitle 课名（用于排除）
   * @return 去重后的题材列表；可能为空
   */
  public static List<String> extractIllustrationSubjects(String content, String courseTitle) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    List<String> subjects = new ArrayList<>();
    Matcher matcher = Pattern.compile("(?m)^#{2,3}\\s+(.+?)\\s*$").matcher(content);
    while (matcher.find()) {
      String heading = matcher.group(1).strip()
          .replaceAll("#+$", "")
          .replaceAll("\\*+", "")
          .strip();
      if (heading.isBlank()) {
        continue;
      }
      if (heading.contains("本课小结")
          || heading.contains("下节课预告")
          || heading.contains("本课目标")
          || heading.startsWith("开场")) {
        continue;
      }
      if (courseTitle != null && !courseTitle.isBlank()
          && (heading.equals(courseTitle) || heading.contains(courseTitle))) {
        continue;
      }
      if (!subjects.contains(heading)) {
        subjects.add(heading);
      }
    }
    return subjects;
  }
  /**
   * 内文插图提示词不足时，在「本课小结」前确定性补概念图块。
   *
   * @param content     原稿
   * @param minimum     最少内文插图数（不含封面）
   * @param courseTitle 课程标题（仅作语境，不要求画进画面）
   * @param examples    可用于提示词主体的例子；空则从二级标题抽取
   * @return 补齐后的正文
   */
  public static String ensureInlinePromptCount(
      String content, int minimum, String courseTitle, List<String> examples) {
    List<CourseMarkdownUtil.PromptHit> inline = inlinePromptHits(content);
    int missing = Math.max(0, minimum - inline.size());
    if (missing == 0) {
      return content == null ? "" : content;
    }
    List<String> sourceExamples = (examples == null || examples.isEmpty())
        ? extractIllustrationSubjects(content, courseTitle)
        : examples;
    if (sourceExamples.isEmpty()) {
      sourceExamples = List.of("本段核心对比", "关键结构示意", "读者易错点提醒");
    }
    // 轮换构图提示，降低多张补图视觉雷同
    String[] layouts = {
        "左右对比构图，两侧对象关系清晰",
        "自上而下流程构图，步骤不超过三步",
        "中心放射结构，突出一个主概念与两三个关联项",
        "前后因果构图，左侧问题右侧解决路径"
    };
    List<String> blocks = new ArrayList<>();
    for (int index = 0; index < missing; index++) {
      String subject = sourceExamples.get(index % sourceExamples.size());
      String layout = layouts[index % layouts.length];
      blocks.add("""
          **插图位置：%s**

          ```text
          解释「%s」的概念图：%s；突出与该概念直接相关的对象与关系，避免添加正文未提及的事实；禁止在画面上写课程标题或课次；如需文字仅用≤8字短标签；清晰简洁，16:9 横向构图。
          ```
          """.formatted(subject, subject, layout).strip());
    }
    return CourseMarkdownSections.insertBeforeSummary(content, String.join("\n\n", blocks));
  }
  /**
   * 删除超出上限的内文插图提示词块（保留封面）。
   *
   * @param content 原稿
   * @param maximum 内文插图上限
   * @return 裁剪后的正文
   */
  public static String limitInlinePrompts(String content, int maximum) {
    if (content == null) {
      return "";
    }
    if (maximum < 0) {
      maximum = 0;
    }
    StringBuffer sb = new StringBuffer();
    Matcher matcher = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(content);
    int inlineSeen = 0;
    while (matcher.find()) {
      String marker = matcher.group("marker");
      String prompt = matcher.group("prompt").strip();
      if (isCoverPrompt(marker, prompt)) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
        continue;
      }
      inlineSeen++;
      if (inlineSeen <= maximum) {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
      } else {
        matcher.appendReplacement(sb, "\n");
      }
    }
    matcher.appendTail(sb);
    return sb.toString().replaceAll("\n{3,}", "\n\n").strip();
  }
  /**
   * 将内文插图提示词按标题锚点分散，降低堆叠。
   *
   * @param content 原稿
   * @return 重排后的正文；不足 2 个内文块则原样
   */
  public static String distributeInlinePrompts(String content) {
    if (content == null || content.isBlank()) {
      return content == null ? "" : content;
    }
    List<CourseMarkdownUtil.PromptHit> matches = inlinePromptHits(content);
    if (matches.size() < 2) {
      return content;
    }
    List<String> blocks = matches.stream().map(hit -> hit.marker().strip()).toList();
    String body = content;
    for (int index = matches.size() - 1; index >= 0; index--) {
      CourseMarkdownUtil.PromptHit hit = matches.get(index);
      body = body.substring(0, hit.start()).stripTrailing()
          + "\n\n"
          + body.substring(hit.end()).stripLeading();
    }
    List<int[]> anchors = CourseMarkdownSections.findHeadingAnchors(body, "###");
    if (anchors.size() < blocks.size()) {
      anchors = CourseMarkdownSections.findHeadingAnchors(body, "##");
    }
    final String bodySnapshot = body;
    anchors = anchors.stream()
        .filter(span -> {
          String line = bodySnapshot.substring(span[0], Math.min(bodySnapshot.length(), span[1]));
          return !line.contains("本课小结") && !line.contains("下节课预告");
        })
        .toList();
    if (anchors.isEmpty()) {
      return body.stripTrailing() + "\n\n" + String.join("\n\n", blocks);
    }
    List<int[]> insertions = new ArrayList<>();
    for (int index = 0; index < blocks.size(); index++) {
      int anchorIndex = Math.min(
          anchors.size() - 1,
          Math.round(index * (anchors.size() - 1) / (float) Math.max(1, blocks.size() - 1)));
      insertions.add(new int[]{anchors.get(anchorIndex)[0], index});
    }
    insertions.sort((left, right) -> Integer.compare(right[0], left[0]));
    for (int[] insertion : insertions) {
      int position = insertion[0];
      String block = blocks.get(insertion[1]);
      body = body.substring(0, position).stripTrailing()
          + "\n\n"
          + block
          + "\n\n"
          + body.substring(position).stripLeading();
    }
    return body.strip();
  }
  /**
   * 统计内文插图提示词数量（不含封面）。
   *
   * @param content 原稿
   * @return 内文提示词块数
   */
  public static int countInlinePrompts(String content) {
    return inlinePromptHits(content).size();
  }
  /**
   * 素材索要标记命中次数（对齐原版 {@code MATERIAL_REQUEST_MARKERS}）。
   *
   * @param content 正文
   * @return 命中条数
   */
  public static int countMaterialRequestMarkers(String content) {
    if (content == null || content.isBlank()) {
      return 0;
    }
    int hits = 0;
    for (String marker : CoursePrompts.MATERIAL_REQUEST_MARKERS) {
      if (content.contains(marker)) {
        hits++;
      }
    }
    return hits;
  }
  public static List<CourseMarkdownUtil.PromptHit> inlinePromptHits(String content) {
    List<CourseMarkdownUtil.PromptHit> inline = new ArrayList<>();
    for (CourseMarkdownUtil.PromptHit hit : findPrompts(content)) {
      if (isCoverPrompt(hit.marker(), hit.prompt())) {
        continue;
      }
      // 空围栏不算有效插图需求（压缩模型常留下空 ```text）
      if (hit.prompt() == null || hit.prompt().isBlank()) {
        continue;
      }
      inline.add(hit);
    }
    return inline;
  }
  /**
   * 将脏插图写法收成权威标准块（{@code **插图位置** + {@code ```text}）。
   *
   * @param content 原稿
   * @return 规范化后正文
   */
  public static String canonicalizeIllustrationPrompts(String content) {
    return IllustrationPromptFormat.canonicalizeIllustrationPrompts(content);
  }
  /**
   * 删除无标记的裸生图提示词段落。
   *
   * @param content Markdown
   * @return 清理后正文
   */
  public static String stripBareImagePromptParagraphs(String content) {
    return IllustrationPromptFormat.stripBareImagePromptParagraphs(content);
  }
  /**
   * 公众号是否仍含裸生图提示词残留。
   *
   * @param wechat 公众号正文
   * @return 有泄漏则为 true
   */
  public static boolean hasBareImagePromptLeak(String wechat) {
    return IllustrationPromptFormat.hasBareImagePromptLeak(wechat);
  }
  /**
   * 清除非正式「插图位置 / 提示词」行（无加粗/标题的裸行；不碰标准 **插图位置** + 围栏块）。
   */
  public static String stripMalformedIllustrationNotes(String content) {
    String cleaned = content == null ? "" : content;
    cleaned = cleaned.replaceAll("(?m)^\\s*（插图位置[^\\n]*$\\n?", "");
    cleaned = cleaned.replaceAll("(?m)^\\s*（提示词[^\\n]*$\\n?", "");
    // 裸行「插图位置：…」（不以 # 或 ** 开头）
    cleaned = cleaned.replaceAll("(?m)^(?!\\s*#{1,6}\\s)(?!\\s*\\*\\*)\\s*插图位置[:：][^\\n]*$\\n?", "");
    cleaned = cleaned.replaceAll("(?m)^\\s*提示词[:：][^\\n]*$\\n?", "");
    return cleaned.replaceAll("\n{3,}", "\n\n").strip();
  }
}
