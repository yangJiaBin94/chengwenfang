package com.workbench.mode.course.application.course;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 课程配图：将生成结果回写原稿/公众号，并做 token 扫尾与组版。
 */
public final class CourseImageComposer {
  /** 工具类不可实例化。 */
  private CourseImageComposer() {}

  /**
   * 将单张配图结果写回原稿与公众号。
   *
   * @param ctx           上下文
   * @param item          计划项
   * @param result        图片元数据
   * @param draftContent  当前原稿
   * @param wechatContent 当前公众号
   * @return 回写后正文
   */
  static ApplyResult applyImageResult(
      CourseGenerationContext ctx,
      CourseImagePipeline.ImagePlanItem item,
      Map<String, Object> result,
      String draftContent,
      String wechatContent) {
    boolean isCover = "cover".equals(item.kind());
    Integer inlineNo = item.inlineNo();
    String prompt = item.prompt();
    String alt = isCover ? "封面图：" + ctx.title() : "插图 " + inlineNo;
    String imageMarkdown = "![" + alt + "](" + result.get("publicUrl") + ")";
    String draft = draftContent;
    Matcher draftMatcher = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(draft);
    boolean replaced = false;
    StringBuffer draftSb = new StringBuffer();
    while (draftMatcher.find()) {
      if (!replaced && prompt.strip().equals(draftMatcher.group("prompt").strip())) {
        draftMatcher.appendReplacement(
            draftSb, Matcher.quoteReplacement(draftMatcher.group() + "\n\n" + imageMarkdown));
        replaced = true;
      } else {
        draftMatcher.appendReplacement(draftSb, Matcher.quoteReplacement(draftMatcher.group()));
      }
    }
    draftMatcher.appendTail(draftSb);
    draft = draftSb.toString();
    if (!replaced && isCover) {
      draft = insertGeneratedCoverPrompt(draft, prompt, imageMarkdown);
    }
    String wechat = wechatContent;
    Matcher wechatMatcher = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(wechat);
    StringBuffer wechatSb = new StringBuffer();
    while (wechatMatcher.find()) {
      if (prompt.strip().equals(wechatMatcher.group("prompt").strip())) {
        wechatMatcher.appendReplacement(wechatSb, Matcher.quoteReplacement(imageMarkdown));
      } else {
        wechatMatcher.appendReplacement(wechatSb, Matcher.quoteReplacement(wechatMatcher.group()));
      }
    }
    wechatMatcher.appendTail(wechatSb);
    wechat = wechatSb.toString().replace(item.token(), imageMarkdown);
    if (prompt != null && !prompt.isBlank()) {
      wechat = wechat.replace(prompt, "");
    }
    return new ApplyResult(draft, wechat);
  }

  /**
   * 原稿无封面提示词块时插入生成块。
   *
   * @param content       原稿
   * @param prompt        封面提示词
   * @param imageMarkdown 图片 markdown
   * @return 插入后正文
   */
  static String insertGeneratedCoverPrompt(String content, String prompt, String imageMarkdown) {
    String block = "**插图位置：课程封面**\n\n```text\n" + prompt + "\n```\n\n" + imageMarkdown;
    Matcher title = Pattern.compile("^#\\s+.+(?:\\n|$)").matcher(content == null ? "" : content.strip());
    if (title.find()) {
      String body = content.strip().substring(title.end()).stripLeading();
      return title.group().strip() + "\n\n" + block + "\n\n" + body;
    }
    return block + "\n\n" + (content == null ? "" : content.strip());
  }

  /**
   * checklist I3：将转换模型留下的 token 标题/裸 token 换成图片 markdown。
   * <p>对齐 Python apply 循环后的 {@code ### [[COURSE_IMAGE_N]]} 与残留 token 替换。
   *
   * @param wechat       公众号正文
   * @param imageResults 配图结果
   * @return 替换后正文
   */
  static String replaceLeftoverImageTokens(String wechat, List<Map<String, Object>> imageResults) {
    String text = wechat == null ? "" : wechat;
    List<Map<String, Object>> inlines = imageResults == null
        ? List.of()
        : imageResults.stream()
            .filter(row -> "inline".equals(String.valueOf(row.get("kind"))))
            .sorted((a, b) -> Integer.compare(asInt(a.get("sequenceNo")), asInt(b.get("sequenceNo"))))
            .toList();
    for (int inlineNo = 1; inlineNo <= inlines.size(); inlineNo++) {
      Map<String, Object> image = inlines.get(inlineNo - 1);
      String url = image.get("publicUrl") == null ? "" : String.valueOf(image.get("publicUrl"));
      String markdown = "![插图 " + inlineNo + "](" + url + ")";
      text = text.replaceAll("(?m)^###\\s*\\[\\[COURSE_IMAGE_" + inlineNo + "]]\\s*$",
          Matcher.quoteReplacement(markdown));
      text = text.replace("[[COURSE_IMAGE_" + inlineNo + "]]", markdown);
    }
    Map<String, Object> cover = imageResults == null
        ? null
        : imageResults.stream()
            .filter(row -> "cover".equals(String.valueOf(row.get("kind"))))
            .findFirst()
            .orElse(null);
    if (cover != null && cover.get("publicUrl") != null) {
      String coverMd = "![课程封面](" + cover.get("publicUrl") + ")";
      text = text.replace(CourseMarkdownUtil.COURSE_COVER_TOKEN, coverMd);
    }
    // 标题包住的 token 先剥成裸 token，再按序号替换或删除越界 token
    text = text.replaceAll("(?m)^\\s*#{1,6}\\s*(\\[\\[COURSE_IMAGE_\\d+]])\\s*$\\n?", "$1\n");
    Matcher leftover = Pattern.compile("\\[\\[COURSE_IMAGE_(\\d+)]]").matcher(text);
    StringBuffer sb = new StringBuffer();
    while (leftover.find()) {
      int tokenNo = Integer.parseInt(leftover.group(1));
      String replacement = "";
      if (tokenNo >= 1 && tokenNo <= inlines.size()) {
        Object url = inlines.get(tokenNo - 1).get("publicUrl");
        replacement = "![插图 " + tokenNo + "](" + (url == null ? "" : url) + ")";
      }
      leftover.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    leftover.appendTail(sb);
    return sb.toString();
  }

  /**
   * checklist I4：组版并清理生产残留（合并原版 {@code _compose_wechat_images} + {@code _normalize_wechat_images}）。
   * <p>封面在页眉后；内文仅在第一个 ## 与本课小结之间、段落后≥20 字处均分。
   *
   * @param content      公众号正文
   * @param imageResults 配图结果
   * @return 组版后正文
   */
  static String composeWechatImages(String content, List<Map<String, Object>> imageResults) {
    String cleaned = stripPromptBlocks(content == null ? "" : content);
    cleaned = cleaned.replaceAll("\\[\\[COURSE_(?:COVER|IMAGE_\\d+)]]", "");
    // applyImageResult 可能已写入 /api/files/...；组版前必须清掉，否则与重新插入叠成双倍
    cleaned = cleaned.replaceAll(
        "(?m)^!\\[[^]]*\\]\\((?:/api/(?:images|files)/[^)]+|/screenshot-placeholder\\.svg)\\)\\s*$\\n?",
        "");
    // 对齐原版：清掉转换模型/原稿残留的「插图位置」「提示词」生产说明（非正式 token）
    cleaned = stripImageProductionResidue(cleaned);

    Map<String, Object> cover = imageResults.stream()
        .filter(row -> "cover".equals(String.valueOf(row.get("kind"))))
        .findFirst()
        .orElse(null);
    List<Map<String, Object>> inlines = imageResults.stream()
        .filter(row -> "inline".equals(String.valueOf(row.get("kind"))))
        .sorted((left, right) -> Integer.compare(asInt(left.get("sequenceNo")), asInt(right.get("sequenceNo"))))
        .toList();

    String body = cleaned.strip();
    Pattern title = Pattern.compile("^#\\s+.+(?:\\n|$)");
    Matcher titleMatch = title.matcher(body);
    String titleLine;
    String rest;
    if (titleMatch.find()) {
      titleLine = titleMatch.group().strip();
      rest = body.substring(titleMatch.end()).stripLeading();
    } else {
      titleLine = "";
      rest = body;
    }

    StringBuilder out = new StringBuilder();
    if (!titleLine.isBlank()) {
      out.append(titleLine).append("\n\n");
    }
    while (rest.startsWith(">")) {
      int nl = rest.indexOf('\n');
      if (nl < 0) {
        out.append(rest).append("\n\n");
        rest = "";
        break;
      }
      out.append(rest, 0, nl + 1);
      rest = rest.substring(nl + 1);
      if (!rest.startsWith(">") && rest.startsWith("\n")) {
        out.append('\n');
        rest = rest.stripLeading();
        break;
      }
    }
    if (cover != null && cover.get("publicUrl") != null) {
      out.append("![课程封面](").append(cover.get("publicUrl")).append(")\n\n");
    }

    Matcher summary = Pattern.compile("(?m)^##\\s*(?:📝\\s*)?本课小结").matcher(rest);
    int bodyEnd = summary.find() ? summary.start() : rest.length();
    Matcher firstSection = Pattern.compile("(?m)^##\\s+.+$").matcher(rest);
    int bodyStart = firstSection.find() ? firstSection.end() : 0;
    String prefix = rest.substring(0, Math.min(bodyStart, rest.length()));
    String middle = rest.substring(Math.min(bodyStart, rest.length()), Math.min(bodyEnd, rest.length()));
    String suffix = rest.substring(Math.min(bodyEnd, rest.length()));

    if (inlines.isEmpty()) {
      out.append(rest);
      return out.toString().replaceAll("\n{3,}", "\n\n").strip() + "\n";
    }

    List<int[]> anchors = new ArrayList<>();
    Matcher para = Pattern.compile("(?ms)(?<!\\S)(?![#>!`])[^\\n].*?(?=\\n\\s*\\n|\\Z)").matcher(middle);
    while (para.find()) {
      String chunk = para.group();
      if (chunk.replaceAll("\\s+", "").length() >= 20) {
        anchors.add(new int[]{para.start(), para.end()});
      }
    }
    List<Integer> selectedEnds = new ArrayList<>();
    if (!anchors.isEmpty()) {
      for (int index = 0; index < inlines.size(); index++) {
        int anchorIndex = Math.min(
            anchors.size() - 1,
            Math.round((index + 1f) * (anchors.size() - 1) / (inlines.size() + 1f)));
        int end = anchors.get(anchorIndex)[1];
        if (selectedEnds.isEmpty() || end != selectedEnds.get(selectedEnds.size() - 1)) {
          selectedEnds.add(end);
        }
      }
      if (selectedEnds.size() < inlines.size()) {
        selectedEnds.clear();
        int take = Math.min(inlines.size(), anchors.size());
        for (int i = anchors.size() - take; i < anchors.size(); i++) {
          selectedEnds.add(anchors.get(i)[1]);
        }
      }
    }

    StringBuilder rebuiltMiddle = new StringBuilder(middle);
    for (int index = inlines.size() - 1; index >= 0; index--) {
      Map<String, Object> img = inlines.get(index);
      int no = img.get("inlineNo") instanceof Number number ? number.intValue() : index + 1;
      String markdown = "![插图 " + no + "](" + img.get("publicUrl") + ")";
      if (selectedEnds.isEmpty() || index >= selectedEnds.size()) {
        rebuiltMiddle.append("\n\n").append(markdown);
      } else {
        int pos = selectedEnds.get(index);
        rebuiltMiddle.insert(pos, "\n\n" + markdown + "\n\n");
      }
    }
    out.append(prefix).append(rebuiltMiddle).append(suffix);
    return out.toString().replaceAll("\n{3,}", "\n\n").strip() + "\n";
  }

  /**
   * 去掉插图提示词围栏块。
   *
   * @param content 正文
   * @return 清理后正文
   */
  static String stripPromptBlocks(String content) {
    return CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(content == null ? "" : content)
        .replaceAll("\n")
        .replaceAll("\n{3,}", "\n\n");
  }

  /**
   * checklist I4：清除非正式插图生产说明（含 {@code ### 插图 N} + 引用块）。
   * <p>原稿或转换模型可能写出「插图位置：…」「提示词：…」而无 fenced 块，
   * 这些不会变成 {@code [[COURSE_IMAGE_N]]}，必须在组版时删掉，否则终检
   * {@code WECHAT_IMAGE_PROMPTS_REMAIN}。
   *
   * @param content 正文
   * @return 清理后正文
   */
  static String stripImageProductionResidue(String content) {
    String cleaned = content == null ? "" : content;
    // 插图位置/插图提示词行 + 紧随的引用块或代码围栏
    cleaned = cleaned.replaceAll(
        "(?ms)^\\s*(?:#{1,6}\\s*)?(?:\\*\\*)?插图(?:位置|提示词)[^\\n]*(?:\\*\\*)?\\s*\\n"
            + "(?:\\s*\\n)*(?:(?:>[^\\n]*(?:\\n|$))|(?:```.*?```\\s*))*",
        "\n");
    cleaned = cleaned.replaceAll(
        "(?m)^\\s*(?:#{1,6}\\s*)?(?:\\*\\*)?插图(?:位置|提示词)[^\\n]*(?:\\*\\*)?\\s*$\\n?",
        "");
    // 裸行「提示词：…」（转换模型常拆成独立行）
    cleaned = cleaned.replaceAll("(?m)^\\s*提示词[:：][^\\n]*$\\n?", "");
    // 对齐 Python _normalize_wechat_images：转换模型常写成「### 插图 1：…」+ 引用块提示词
    cleaned = cleaned.replaceAll(
        "(?ms)^#{2,6}\\s*插图\\s*\\d*[^\\n]*\\n(?:\\s*\\n)*(?:>[^\\n]*(?:\\n|$))+",
        "\n");
    // 无标记 Midjourney/Keynote 裸提示词段落
    cleaned = CourseMarkdownUtil.stripBareImagePromptParagraphs(cleaned);
    return cleaned.replaceAll("\n{3,}", "\n\n").strip();
  }

  /**
   * 安全转 int。
   *
   * @param value 任意值
   * @return int；非 Number 为 0
   */
  private static int asInt(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  /**
   * 占位符回写中间结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  static final class ApplyResult {
    /** 更新后的原稿。 */
    private final String draft;
    /** 更新后的公众号正文。 */
    private final String wechat;
  }
}
