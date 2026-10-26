package com.workbench.mode.hotnews.domain;

import cn.hutool.core.util.StrUtil;
import com.workbench.common.IllustrationPromptFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 热点原稿 Markdown 辅助：插图提示词块解析、正文字数（不计提示词）、公众号占位准备。
 */
public final class HotNewsMarkdownUtil {
  /**
   * 插图提示词块：含「插图/提示词/prompt」的标题或加粗行，以及随后的 fenced prompt。
   */
  public static final Pattern PROMPT_BLOCK = Pattern.compile(
      "(?<marker>(?:^|\\n)(?!\\s*!\\[)(?:\\s*#{1,6}\\s+|\\s*\\*\\*)"
          + "[^\\n]*(?:插图|提示词|prompt)[^\\n]*\\n+"
          + "(?:(?!\\s*```)[^\\n]*\\n+){0,4}"
          + "```[^\\n]*\\n(?<prompt>.*?)\\n```)",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /** 封面占位。 */
  public static final String COVER_TOKEN = "[[HOT_NEWS_COVER]]";

  /** 默认插图风格（AI/科技类选题通用）。 */
  public static final String DEFAULT_ILLUSTRATION_STYLE =
      "科技立体信息图、微立体渲染、光影层次清晰、富有创意";

  /**
   * 生图平衡约束（拼入提示词尾部）：要看得出主题，又不过度堆砌。
   */
  public static final String SIMPLE_VISUAL_CONSTRAINT =
      "画面必须一眼读出本篇核心主题与判断，禁止做成与标题无关的抽象装饰或空符号墙；"
          + "用 3～5 个信息承载元素讲清关键对比/关系（例如规模差、开闭源、结论对立），层次清楚并留出呼吸感；"
          + "不要密集仪表盘、人群群像、满屏小图标或大段说明文字；"
          + "如需文字，只用与主题直接相关的短标签（每个≤8字，全图≤3个）。";

  /**
   * AI/科技选题硬性风格：科技感、立体、有创意，避免扁平单调。
   */
  public static final String AI_TECH_STYLE_CONSTRAINT =
      "【硬性风格】AI/科技类主题必须具备明显科技感与未来感；"
          + "主体用微立体/浅 3D 造型（有体积、边缘光或柔和投影），禁止纯扁平剪贴画；"
          + "构图与隐喻要有创意变化，避免左右两色块+两个图标的单调模板；"
          + "材质可含玻璃、金属、能量流、芯片/节点网络等科技质感，但保持画面干净可读。";

  /** 封面额外强调：一眼读懂核心内容，并吸引点击。 */
  public static final String COVER_THEME_CONSTRAINT =
      "这是公众号封面，目标是让人一眼看懂文章在讲什么，并产生点开欲望。"
          + "必须把本篇核心判断/冲突做成画面主信息（可含关键数字、前后对比、成本差、能力跃迁等），"
          + "看完封面应能用一句话说出「这篇文章大概讲什么」。"
          + "画面要有张力或好奇钩子（反差、意外数字、对立关系），信息元素仍控制在 3～5 个。"
          + "禁止只画抽象科技氛围、锁头、光点、几何块、通用机器人等与本篇论点无关的空装饰。";

  /**
   * 骨架缺省时的插图提示词硬性要求（Skill「热点文章骨架配图规范」不可用时回退）。
   * <p>不含「封面」字样，避免正文插图被误判为封面。
   *
   * @return 可拼进提示词的中文要求
   */
  public static String defaultImagePromptRequirements() {
    return AI_TECH_STYLE_CONSTRAINT + " " + SIMPLE_VISUAL_CONSTRAINT;
  }

  private HotNewsMarkdownUtil() {}

  /**
   * 正文插图占位 token。
   *
   * @param sequenceNo 从 1 起的序号
   * @return 占位字符串
   */
  public static String inlineToken(int sequenceNo) {
    return "[[HOT_NEWS_IMAGE_" + sequenceNo + "]]";
  }

  /**
   * 判断提示词块是否为封面图。
   * <p>优先看标记行；标记已写明「插图」且不含「封面」时一律视为正文图，
   * 避免生图要求正文里出现「封面」二字导致误判。
   *
   * @param marker 标题/加粗标记行
   * @param prompt 围栏内提示词
   * @return true 表示封面
   */
  public static boolean isCoverPrompt(String marker, String prompt) {
    String markerText = StrUtil.nullToEmpty(marker);
    if (markerText.contains("封面")) {
      return true;
    }
    if (markerText.contains("插图")) {
      return false;
    }
    String promptText = StrUtil.nullToEmpty(prompt);
    return promptText.contains("热点公众号封面")
        || promptText.contains("热点封面")
        || (promptText.contains("封面") && !promptText.contains("插图"));
  }

  /**
   * 扫描全文中的插图提示词块。
   *
   * @param content Markdown；null 视为空
   * @return 命中列表
   */
  public static List<PromptHit> findPrompts(String content) {
    List<PromptHit> out = new ArrayList<>();
    Matcher matcher = PROMPT_BLOCK.matcher(StrUtil.nullToEmpty(content));
    while (matcher.find()) {
      String marker = matcher.group("marker").stripLeading();
      String prompt = matcher.group("prompt").strip();
      out.add(new PromptHit(marker, prompt, matcher.start(), matcher.end()));
    }
    return out;
  }

  /**
   * 仅内文插图提示词（不含封面）。
   *
   * @param content Markdown
   * @return 内文命中
   */
  public static List<PromptHit> findInlinePrompts(String content) {
    List<PromptHit> out = new ArrayList<>();
    for (PromptHit hit : findPrompts(content)) {
      if (!isCoverPrompt(hit.marker(), hit.prompt())) {
        out.add(hit);
      }
    }
    return out;
  }

  /**
   * 封面提示词正文；无则空串。
   *
   * @param content 原稿
   * @return 封面 prompt
   */
  public static String coverPrompt(String content) {
    for (PromptHit hit : findPrompts(content)) {
      if (isCoverPrompt(hit.marker(), hit.prompt()) && StrUtil.isNotBlank(hit.prompt())) {
        return hit.prompt();
      }
    }
    return "";
  }

  /**
   * 任意语言 Markdown 围栏（``` / ~~~），整块不计入正文字数。
   */
  private static final Pattern FENCED_BLOCK = Pattern.compile(
      "(?is)(?:```|~~~)[^\\n]*\\R.*?\\R(?:```|~~~)");

  /**
   * 统计正文字数：去掉插图提示词块、全部代码/提示词围栏、图片与链接语法、热点占位与装饰后的非空白字符。
   *
   * @param markdown 正文
   * @return 字数
   */
  public static int countBodyChars(String markdown) {
    String cleaned = PROMPT_BLOCK.matcher(StrUtil.nullToEmpty(markdown)).replaceAll("\n");
    cleaned = FENCED_BLOCK.matcher(cleaned).replaceAll("\n");
    cleaned = cleaned.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ");
    cleaned = cleaned.replaceAll("\\[[^\\]]*\\]\\([^)]*\\)", " ");
    cleaned = cleaned.replaceAll("\\[\\[HOT_NEWS_(?:COVER|IMAGE_\\d+)]]", " ");
    cleaned = cleaned.replaceAll("[#>*_`~\\-]+", " ");
    cleaned = cleaned.replaceAll("\\s+", "");
    return cleaned.length();
  }

  /**
   * 为公众号转换准备源文：封面提示词删除，内文提示词替换为 {@code [[HOT_NEWS_IMAGE_N]]}。
   *
   * @param draft 原稿（含插图提示词）
   * @return 替换后的正文与内文 token 列表
   */
  public static WechatSource prepareWechatSource(String draft) {
    List<String> tokens = new ArrayList<>();
    Matcher matcher = PROMPT_BLOCK.matcher(StrUtil.nullToEmpty(draft));
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String marker = matcher.group("marker");
      String prompt = matcher.group("prompt").strip();
      if (StrUtil.isBlank(prompt)) {
        matcher.appendReplacement(buffer, "\n");
        continue;
      }
      if (isCoverPrompt(marker, prompt)) {
        matcher.appendReplacement(buffer, "\n");
        continue;
      }
      String token = inlineToken(tokens.size() + 1);
      tokens.add(token);
      matcher.appendReplacement(buffer, Matcher.quoteReplacement("\n\n" + token + "\n"));
    }
    matcher.appendTail(buffer);
    return new WechatSource(buffer.toString(), List.copyOf(tokens));
  }

  /**
   * 确保原稿含 1 个封面 + 指定数量正文插图提示词；不足则确定性补齐并保留已有块。
   *
   * @param content                 原稿
   * @param inlineCount             正文插图张数（不含封面）
   * @param title                   文章标题
   * @param illustrationStyle       插图风格
   * @param coverBrief              封面构图要点
   * @param imagePromptRequirements 骨架给出的生图硬性要求（可空则用默认）
   * @return 补齐后的原稿
   */
  public static String ensureImagePrompts(
      String content,
      int inlineCount,
      String title,
      String illustrationStyle,
      String coverBrief,
      String imagePromptRequirements) {
    // 定稿前先把脏写法收成标准块，后续只认 PROMPT_BLOCK
    String draft = canonicalizeIllustrationPrompts(StrUtil.nullToEmpty(content));
    int safeInline = Math.max(2, inlineCount);
    String style = StrUtil.blankToDefault(illustrationStyle, DEFAULT_ILLUSTRATION_STYLE);
    String coverPoint = StrUtil.blankToDefault(coverBrief, StrUtil.blankToDefault(title, "核心冲突"));
    String safeTitle = StrUtil.blankToDefault(title, "热点主题");
    String requirements = StrUtil.blankToDefault(
        imagePromptRequirements, defaultImagePromptRequirements());

    if (StrUtil.isBlank(coverPrompt(draft))) {
      draft = draft.strip() + "\n\n" + coverPromptBlock(style, coverPoint, requirements) + "\n";
    }

    List<PromptHit> inlineHits = findInlinePrompts(draft);
    int missing = Math.max(0, safeInline - inlineHits.size());
    if (missing > 0) {
      List<String> blocks = new ArrayList<>();
      String[] subjects = {
          "核心变化",
          "影响面对比",
          "读者可带走的判断",
          "关键冲突",
          "行业连锁反应",
          "下一步信号"
      };
      for (int index = 0; index < missing; index++) {
        int number = inlineHits.size() + index + 1;
        String subject = subjects[(number - 1) % subjects.length];
        blocks.add(inlinePromptBlock(number, subject, safeTitle, style, requirements));
      }
      draft = draft.strip() + "\n\n" + String.join("\n\n", blocks) + "\n";
    }

    // 超出档位则裁掉多余内文提示词，封面保留
    return limitInlinePrompts(draft, safeInline);
  }

  /**
   * 删除超出上限的内文插图提示词块（保留封面）。
   *
   * @param content 原稿
   * @param maximum 内文插图上限
   * @return 裁剪后的正文
   */
  public static String limitInlinePrompts(String content, int maximum) {
    if (StrUtil.isBlank(content)) {
      return "";
    }
    int maxInline = Math.max(0, maximum);
    StringBuffer buffer = new StringBuffer();
    Matcher matcher = PROMPT_BLOCK.matcher(content);
    int inlineSeen = 0;
    while (matcher.find()) {
      String marker = matcher.group("marker");
      String prompt = matcher.group("prompt").strip();
      if (isCoverPrompt(marker, prompt)) {
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
        continue;
      }
      inlineSeen++;
      if (inlineSeen <= maxInline) {
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
      } else {
        matcher.appendReplacement(buffer, "\n");
      }
    }
    matcher.appendTail(buffer);
    return buffer.toString().replaceAll("\n{3,}", "\n\n").strip();
  }

  /**
   * 确保公众号正文保留封面与内文占位；缺则追加。
   *
   * @param wechat        公众号正文
   * @param expectedInline 期望内文占位数
   * @return 补齐占位后的正文
   */
  public static String ensureWechatTokens(String wechat, int expectedInline) {
    String body = StrUtil.nullToEmpty(wechat);
    if (!body.contains(COVER_TOKEN)) {
      body = body.strip() + "\n\n" + COVER_TOKEN + "\n";
    }
    int count = Math.max(0, expectedInline);
    for (int sequence = 1; sequence <= count; sequence++) {
      String token = inlineToken(sequence);
      if (!body.contains(token)) {
        body = body.strip() + "\n\n" + token + "\n";
      }
    }
    return body;
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
   * 公众号正文是否仍泄漏插图提示词生产中间内容。
   *
   * @param wechat 公众号正文
   * @return 泄漏则为 true
   */
  public static boolean hasPromptLeak(String wechat) {
    String text = StrUtil.nullToEmpty(wechat);
    if (!findPrompts(text).isEmpty()) {
      return true;
    }
    if (text.contains("插图提示词")
        || text.contains("插图位置")
        || text.contains("提示词：")
        || text.contains("提示词:")) {
      return true;
    }
    if (Pattern.compile("(?m)^#{1,6}\\s*插图").matcher(text).find()) {
      return true;
    }
    // 裸行「插图 2」生产标题（非图片 Markdown 行）
    if (Pattern.compile("(?m)^\\s*(?:\\*\\*)?插图\\s*\\d+[^\\n!]*(?:\\*\\*)?\\s*$")
        .matcher(text)
        .find()) {
      return true;
    }
    return IllustrationPromptFormat.hasBareImagePromptLeak(text);
  }

  /**
   * 清除公众号生产残留：提示词块、提示标题、「插图 N」说明行、裸生图段落。
   *
   * @param wechat 公众号正文
   * @return 清理后正文
   */
  public static String stripWechatProductionResidue(String wechat) {
    String cleaned = PROMPT_BLOCK.matcher(StrUtil.nullToEmpty(wechat)).replaceAll("\n");
    cleaned = cleaned.replaceAll(
        "(?ms)^\\s*(?:#{1,6}\\s*)?(?:\\*\\*)?插图(?:位置|提示词)[^\\n]*(?:\\*\\*)?\\s*\\n"
            + "(?:\\s*\\n)*(?:(?:>[^\\n]*(?:\\n|$))|(?:```.*?```\\s*))*",
        "\n");
    cleaned = cleaned.replaceAll(
        "(?m)^\\s*(?:#{1,6}\\s*)?(?:\\*\\*)?插图(?:位置|提示词)[^\\n]*(?:\\*\\*)?\\s*$\\n?",
        "");
    cleaned = cleaned.replaceAll("(?m)^\\s*提示词[:：][^\\n]*$\\n?", "");
    cleaned = cleaned.replaceAll(
        "(?ms)^#{1,6}\\s*插图\\s*\\d*[^\\n]*\\n(?:\\s*\\n)*(?:>[^\\n]*(?:\\n|$))+",
        "\n");
    cleaned = cleaned.replaceAll("(?m)^#{1,6}\\s*插图\\s*\\d*[^\\n]*$\\n?", "");
    cleaned = cleaned.replaceAll(
        "(?m)^\\s*(?:\\*\\*)?插图\\s*\\d+[^\\n!]*(?:\\*\\*)?\\s*$\\n?",
        "");
    cleaned = IllustrationPromptFormat.stripBareImagePromptParagraphs(cleaned);
    return cleaned.replaceAll("\n{3,}", "\n\n").strip();
  }

  /**
   * 清除正文禁用符号：「」→中文双引号，//→｜，->→→，——→逗号。
   * <p>插图提示词围栏、代码围栏、图片与链接 URL 不改动。
   *
   * @param content Markdown
   * @return 清理后文本
   */
  public static String sanitizeProseSymbols(String content) {
    if (StrUtil.isBlank(content)) {
      return StrUtil.nullToEmpty(content);
    }
    List<String> preserved = new ArrayList<>();
    String masked = content;
    masked = Pattern.compile("(?s)```.*?```").matcher(masked).replaceAll(match -> {
      preserved.add(match.group());
      return "@@PRESERVE_" + (preserved.size() - 1) + "@@";
    });
    masked = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)").matcher(masked).replaceAll(match -> {
      preserved.add(match.group());
      return "@@PRESERVE_" + (preserved.size() - 1) + "@@";
    });
    masked = Pattern.compile("\\[[^\\]]+\\]\\(https?://[^)]+\\)").matcher(masked).replaceAll(match -> {
      preserved.add(match.group());
      return "@@PRESERVE_" + (preserved.size() - 1) + "@@";
    });
    masked = masked.replace('「', '“').replace('」', '”');
    masked = masked.replace("——", "，");
    masked = masked.replace("->", "→");
    masked = masked.replaceAll("(?<![:\\w])/\\/(?!/)", "｜");
    for (int index = 0; index < preserved.size(); index++) {
      masked = masked.replace("@@PRESERVE_" + index + "@@", preserved.get(index));
    }
    return masked;
  }

  /**
   * 将图片 Markdown 紧跟插入匹配提示词块之后；已存在则不重复。
   *
   * @param draft         原稿
   * @param promptText    围栏内提示词正文
   * @param imageMarkdown 图片引用
   * @return 更新后的原稿
   */
  public static String attachImageAfterPrompt(String draft, String promptText, String imageMarkdown) {
    String text = StrUtil.nullToEmpty(draft);
    String prompt = StrUtil.nullToEmpty(promptText).strip();
    String image = StrUtil.nullToEmpty(imageMarkdown).strip();
    if (StrUtil.isBlank(text) || StrUtil.isBlank(image)) {
      return text;
    }
    Matcher matcher = PROMPT_BLOCK.matcher(text);
    StringBuffer buffer = new StringBuffer();
    boolean replaced = false;
    while (matcher.find()) {
      String block = matcher.group();
      String blockPrompt = matcher.group("prompt").strip();
      if (!replaced && (prompt.isBlank() || prompt.equals(blockPrompt))) {
        String after = block;
        if (!after.contains(image)) {
          after = after.stripTrailing() + "\n\n" + image;
        }
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(after));
        replaced = true;
      } else {
        matcher.appendReplacement(buffer, Matcher.quoteReplacement(block));
      }
    }
    matcher.appendTail(buffer);
    if (!replaced) {
      return text.strip() + "\n\n" + image + "\n";
    }
    return buffer.toString();
  }

  /**
   * 校验原稿中每张图片引用是否紧跟在对应插图提示词块之后。
   *
   * @param draft 配图回填后的原稿
   * @return 合规则为 true
   */
  public static boolean draftImagesFollowPrompts(String draft) {
    String text = StrUtil.nullToEmpty(draft);
    Matcher imageMatcher = Pattern.compile("(?m)^!\\[[^\\]]*\\]\\([^)]+\\)\\s*$").matcher(text);
    while (imageMatcher.find()) {
      String before = text.substring(0, imageMatcher.start()).stripTrailing();
      if (!before.endsWith("```")) {
        return false;
      }
      int closeFence = before.lastIndexOf("```");
      int openFence = before.lastIndexOf("```", closeFence - 1);
      if (openFence < 0) {
        return false;
      }
      String window = before.substring(Math.max(0, openFence - 160));
      if (!PROMPT_BLOCK.matcher(window).find()
          && !window.contains("插图")
          && !window.toLowerCase().contains("prompt")) {
        return false;
      }
    }
    return true;
  }

  /**
   * 拼封面提示词块。
   *
   * @param style                   风格
   * @param coverPoint              构图要点
   * @param imagePromptRequirements 生图硬性要求
   * @return Markdown 块
   */
  public static String coverPromptBlock(
      String style, String coverPoint, String imagePromptRequirements) {
    String requirements = StrUtil.blankToDefault(
        imagePromptRequirements, defaultImagePromptRequirements());
    return """
        **插图位置：封面图**

        ```text
        热点公众号封面。风格：%s。封面须直观表达文章核心内容并吸引点击。构图要点：%s。%s %s
        ```
        """.formatted(style, coverPoint, COVER_THEME_CONSTRAINT, requirements)
        .strip();
  }

  /**
   * 拼正文插图提示词块。
   *
   * @param sequence                序号（从 1 起）
   * @param subject                 画面主题
   * @param title                   文章标题
   * @param style                   风格
   * @param imagePromptRequirements 生图硬性要求
   * @return Markdown 块
   */
  public static String inlinePromptBlock(
      int sequence,
      String subject,
      String title,
      String style,
      String imagePromptRequirements) {
    String requirements = StrUtil.blankToDefault(
        imagePromptRequirements, defaultImagePromptRequirements());
    return """
        **插图位置：插图 %d｜%s**

        ```text
        热点正文插图第 %d 张。风格：%s。围绕「%s」表达「%s」这一核心判断；%s
        ```
        """.formatted(sequence, subject, sequence, style, title, subject, requirements)
        .strip();
  }

  /**
   * 单次插图提示词命中。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class PromptHit {
    /** 标题/加粗行。 */
    private final String marker;
    /** 围栏内提示词。 */
    private final String prompt;
    /** 起始下标。 */
    private final int start;
    /** 结束下标（不含）。 */
    private final int end;
  }

  /**
   * 公众号转换源：已替换 token 的正文与内文 token 列表。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class WechatSource {
    /** 处理后的 Markdown。 */
    private final String prepared;
    /** 内文图片 token 顺序。 */
    private final List<String> imageTokens;
  }
}
