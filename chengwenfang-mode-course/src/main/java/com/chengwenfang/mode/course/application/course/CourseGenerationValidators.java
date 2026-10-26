package com.chengwenfang.mode.course.application.course;

import com.chengwenfang.quality.facade.AuditFacade;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程生成校验器：草稿完整性、公式样式/表达式与知识库终检。
 * <p>失败统一抛 {@link IllegalStateException}，错误码前缀供 Job 侧识别。
 */
public class CourseGenerationValidators {
  /** 公众号内文插图行（alt 以「插图 N」开头）。 */
  private static final Pattern INLINE_IMAGE = Pattern.compile("(?m)^!\\[插图\\s*\\d+[^]]*\\]\\([^)]*\\)\\s*$");
  /** 公众号封面图行。 */
  private static final Pattern COVER_IMAGE = Pattern.compile("(?m)^!\\[课程封面[^]]*\\]\\([^)]*\\)\\s*$");
  /** checklist F2：页眉须为「系列行 / 空引用 / 覆盖 / 空引用 / 摘要」。 */
  private static final Pattern WECHAT_HEADER_LAYOUT = Pattern.compile(
      "(?m)^>\\s*系列：.*难度：.*预计阅读：.*\\n"
          + ">\\s*\\n"
          + ">\\s*本课覆盖：.+\\n"
          + ">\\s*\\n"
          + ">\\s*摘要：.+$");
  /** checklist F1：拒绝残留的「### 插图」提示标题。 */
  private static final Pattern ILLUSTRATION_CAPTION_HEADING =
      Pattern.compile("(?m)^#{2,6}\\s*插图");

  /** 审核门面：公式样式/LaTeX 检测与知识库规则。 */
  private final AuditFacade audit;

  /**
   * @param audit 审核门面
   */
  public CourseGenerationValidators(AuditFacade audit) {
    this.audit = audit;
  }

  /**
   * 校验公式块须使用引用块样式（原稿与公众号均检查）。
   *
   * @param draft  课程原稿 Markdown
   * @param wechat 公众号 Markdown
   * @throws IllegalStateException 存在未使用引用块样式的独立公式时
   */
  public void validateFormulaStyle(String draft, String wechat) {
    for (String label : List.of("课程原稿", "公众号文章")) {
      String text = "课程原稿".equals(label) ? draft : wechat;
      if (audit.hasUnstyledFormulaBlocks(text)) {
        throw new IllegalStateException("FORMULA_STYLE_INVALID: " + label + " 存在未使用引用块样式的独立公式");
      }
    }
  }

  /**
   * 校验公式不得残留 LaTeX 排版命令（原稿与公众号均检查）。
   *
   * @param draft  课程原稿 Markdown
   * @param wechat 公众号 Markdown
   * @throws IllegalStateException 仍含 LaTeX 命令时
   */
  public void validateFormulaExpression(String draft, String wechat) {
    for (String label : List.of("课程原稿", "公众号文章")) {
      String text = "课程原稿".equals(label) ? draft : wechat;
      if (audit.hasLatexFormulaCommands(text)) {
        throw new IllegalStateException("FORMULA_EXPRESSION_INVALID: " + label + " 仍含 LaTeX 排版命令");
      }
    }
  }

  /**
   * 校验草稿必备结构（小结/预告顺序）与正文字数上限。
   *
   * @param draft        课程原稿全文
   * @param maxBodyChars 正文上限（不含提示词块等）
   * @throws IllegalStateException 结构不完整或超字数时
   */
  public void validateDraft(String draft, int maxBodyChars) {
    String stripped = draft == null ? "" : draft.strip();
    rejectMaterialRequests(stripped);
    // 对齐原版 validate_course_draft：只检查长度与小结→预告顺序，不做唯一性计数
    int summary = stripped.indexOf("本课小结");
    int preview = stripped.indexOf("下节课预告");
    if (stripped.length() < 800 || summary < 0 || preview <= summary) {
      throw new IllegalStateException("GENERATED_DRAFT_INCOMPLETE: 课程原稿不完整");
    }
    int body = CourseMarkdownUtil.lessonBodyCharCount(draft);
    if (body > maxBodyChars) {
      throw new IllegalStateException(
          "COURSE_BODY_TOO_LONG: 原稿正文 " + body + " 超过上限 " + maxBodyChars);
    }
  }

  /**
   * 命中过多「请提供素材」类话术时拒绝（对齐原版 MATERIAL_REQUEST_MARKERS）。
   */
  public void rejectMaterialRequests(String content) {
    if (CourseMarkdownUtil.countMaterialRequestMarkers(content) >= 2) {
      throw new IllegalStateException("GENERATED_CONTENT_REQUESTED_MATERIALS: 模型向用户索要课程素材");
    }
  }

  /**
   * 将独立公式规范为引用块样式。
   */
  public String normalizeFormulaBlocks(String content) {
    return audit.normalizeFormulaBlocks(content);
  }

  /**
   * checklist F1/F2：终检字数、结构、配图、页眉字段与布局、封面与知识库规则。
   */
  public void validateFinal(String draft, String wechat, CourseGenerationContext ctx) {
    validateFinal(draft, wechat, ctx, null);
  }

  /**
   * checklist F1/F2：终检（含封面结果行）。
   *
   * @param imageResults 配图结果；null 时只做缺图别名检查
   */
  public void validateFinal(
      String draft,
      String wechat,
      CourseGenerationContext ctx,
      List<java.util.Map<String, Object>> imageResults) {
    if (draft == null || draft.isBlank() || wechat == null || wechat.isBlank()) {
      throw new IllegalStateException("COURSE_CONTENT_INCOMPLETE");
    }
    for (String label : List.of("课程原稿", "公众号文章")) {
      String text = "课程原稿".equals(label) ? draft : wechat;
      int body = CourseMarkdownUtil.lessonBodyCharCount(text);
      if (body > ctx.maxBodyChars()) {
        throw new IllegalStateException(
            "COURSE_BODY_TOO_LONG: " + label + " 正文 " + body + " 超过 " + ctx.maxBodyChars());
      }
      // 唯一性只在终检；去掉代码块后再计次，避免模型把「不要写本课小结」写进围栏误伤
      String structureText = CourseMarkdownUtil.stripFencedCode(text);
      int summary = structureText.indexOf("本课小结");
      int preview = structureText.indexOf("下节课预告");
      long summaryCount = CourseMarkdownUtil.countOccurrences(structureText, "本课小结");
      long previewCount = CourseMarkdownUtil.countOccurrences(structureText, "下节课预告");
      if (summary < 0 || preview <= summary || summaryCount != 1 || previewCount != 1) {
        throw new IllegalStateException("COURSE_CONTENT_STRUCTURE_INVALID: " + label);
      }
    }
    if (!CourseMarkdownUtil.findPrompts(wechat).isEmpty()) {
      throw new IllegalStateException("WECHAT_IMAGE_PROMPTS_REMAIN: 公众号仍残留插图提示词");
    }
    if (CourseMarkdownUtil.hasBareImagePromptLeak(wechat)) {
      throw new IllegalStateException("WECHAT_IMAGE_PROMPTS_REMAIN: 公众号仍残留裸生图提示词");
    }
    if (wechat.contains(CourseMarkdownUtil.COURSE_COVER_TOKEN)
        || wechat.contains("[[COURSE_IMAGE_")
        || wechat.contains("插图位置")
        || wechat.contains("插图提示词")
        || wechat.contains("未选择推荐风格")
        || ILLUSTRATION_CAPTION_HEADING.matcher(wechat).find()) {
      throw new IllegalStateException("WECHAT_IMAGE_PROMPTS_REMAIN: 公众号仍残留占位符");
    }
    Matcher inlineMatcher = INLINE_IMAGE.matcher(wechat);
    java.util.ArrayList<int[]> spans = new java.util.ArrayList<>();
    while (inlineMatcher.find()) {
      spans.add(new int[]{inlineMatcher.start(), inlineMatcher.end()});
    }
    int count = spans.size();
    if (count < ctx.minImages() || count > ctx.maxImages()) {
      throw new IllegalStateException(
          "WECHAT_IMAGE_COUNT_INVALID: 插图 " + count + " 不在 [" + ctx.minImages() + "," + ctx.maxImages() + "]");
    }
    for (int index = 0; index + 1 < spans.size(); index++) {
      String between = wechat.substring(spans.get(index)[1], spans.get(index + 1)[0]);
      String readable = between.replaceAll("[#>*_`~\\s]", "");
      if (readable.length() < 30) {
        throw new IllegalStateException("WECHAT_IMAGES_CLUSTERED: 插图间距过近");
      }
    }
    String header = String.join("\n", wechat.strip().split("\\R", -1)).lines().limit(14)
        .reduce("", (acc, line) -> acc.isEmpty() ? line : acc + "\n" + line);
    for (String field : List.of("系列：", "难度：", "预计阅读：", "本课覆盖：", "摘要：")) {
      if (!header.contains(field)) {
        throw new IllegalStateException("WECHAT_HEADER_INCOMPLETE: 缺少 " + field);
      }
    }
    if (!WECHAT_HEADER_LAYOUT.matcher(header).find()) {
      throw new IllegalStateException(
          "WECHAT_HEADER_LAYOUT_INVALID: 公众号课程信息区必须将本课覆盖和摘要独立换行");
    }
    validateCover(wechat, imageResults);
    for (var rule : audit.checkKnowledge(wechat, draft)) {
      if (rule.isBlocking() && !rule.isPassed()) {
        throw new IllegalStateException(rule.getCode() + ": " + rule.getDetail());
      }
    }
  }

  /**
   * 配图前对 token 化原稿与公众号做知识库规则；阻塞项包成可修复码。
   */
  public void rejectKnowledgeGaps(String wechat, String tokenizedDraft) {
    for (var rule : audit.checkKnowledge(wechat, tokenizedDraft)) {
      if (rule.isBlocking() && !rule.isPassed()) {
        throw new IllegalStateException(
            "GENERATED_CONTENT_REJECTED: " + rule.getCode() + "：" + rule.getDetail());
      }
    }
  }

  /**
   * 对齐 Python {@code _validate_manifest}：原稿与公众号的 {@code ![]()} 数量一致，
   * 且原稿中的 {@code $$…$$} / {@code $…$} 公式子串须仍在公众号中。
   */
  public void assertContentManifest(String draft, String wechat) {
    String source = draft == null ? "" : draft;
    String target = wechat == null ? "" : wechat;
    int draftImages = countMarkdownImages(source);
    int wechatImages = countMarkdownImages(target);
    if (draftImages != wechatImages) {
      throw new IllegalStateException(
          "WECHAT_CONTENT_NOT_EQUIVALENT: 公众号版本的图片引用数量与课程原稿不一致");
    }
    Matcher formulaMatcher = Pattern.compile(
            "\\$\\$[\\s\\S]*?\\$\\$|(?<!\\$)\\$[^$\\n]+\\$(?!\\$)")
        .matcher(source);
    while (formulaMatcher.find()) {
      String formula = formulaMatcher.group();
      if (!target.contains(formula)) {
        throw new IllegalStateException(
            "WECHAT_CONTENT_NOT_EQUIVALENT: 公众号版本缺少课程原稿中的公式或代码示例");
      }
    }
  }

  private static int countMarkdownImages(String content) {
    Matcher matcher = Pattern.compile("!\\[[^]]*\\]\\([^)]*\\)").matcher(content == null ? "" : content);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private void validateCover(String wechat, List<java.util.Map<String, Object>> imageResults) {
    if (imageResults != null) {
      List<java.util.Map<String, Object>> covers = imageResults.stream()
          .filter(row -> "cover".equals(String.valueOf(row.get("kind"))))
          .toList();
      if (covers.size() != 1) {
        throw new IllegalStateException("COURSE_COVER_INVALID: 课程必须生成且只能生成一张封面图");
      }
      java.util.Map<String, Object> cover = covers.get(0);
      String coverUrl = cover.get("publicUrl") == null ? "" : String.valueOf(cover.get("publicUrl"));
      Object width = cover.get("width");
      Object height = cover.get("height");
      String size = cover.get("size") == null ? "" : String.valueOf(cover.get("size"));
      boolean sizeOk = "2400x1024".equals(size)
          || (width instanceof Number w && height instanceof Number h
              && w.intValue() == 2400 && h.intValue() == 1024);
      Matcher firstSection = Pattern.compile("(?m)^##\\s+").matcher(wechat);
      int firstSectionAt = firstSection.find() ? firstSection.start() : -1;
      if (!sizeOk
          || coverUrl.isBlank()
          || CourseMarkdownUtil.countOccurrences(wechat, coverUrl) != 1
          || (firstSectionAt >= 0 && wechat.indexOf(coverUrl) > firstSectionAt)) {
        throw new IllegalStateException("COURSE_COVER_INVALID: 公众号封面尺寸或顶部位置不正确");
      }
      return;
    }
    if (!COVER_IMAGE.matcher(wechat).find() && !wechat.contains("![课程封面](")) {
      String top = wechat.strip().lines().limit(20)
          .reduce("", (acc, line) -> acc.isEmpty() ? line : acc + "\n" + line);
      if (!top.contains("/api/images/")) {
        throw new IllegalStateException("COURSE_COVER_INVALID: WECHAT_COVER_MISSING");
      }
    }
  }
}
