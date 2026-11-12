package com.chengwenfang.mode.hotnews.domain;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.IllustrationPromptFormat;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsEvidenceKeys;
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

  /** 微信 digest 最大字数（与发布器 DIGEST_MAX_CHARS 对齐）。 */
  public static final int WECHAT_DIGEST_MAX_CHARS = 120;

  /** Markdown「摘要：」行（可带引用前缀）。 */
  private static final Pattern DIGEST_LINE_PATTERN =
      Pattern.compile("(?m)^\\s*>?\\s*摘要\\s*[:：]\\s*(.+?)\\s*$");

  /** 佐证占位：[[HOT_NEWS_EVIDENCE:{uuid}]]。 */
  private static final Pattern EVIDENCE_TOKEN_PATTERN = Pattern.compile(
      "\\[\\[HOT_NEWS_EVIDENCE:([0-9a-fA-F\\-]{36})]]");

  /** Markdown 外链 [文字](http...)；保留本地 /api/ 路径。 */
  private static final Pattern MARKDOWN_HTTP_LINK = Pattern.compile(
      "\\[([^\\]]*)\\]\\((https?://[^)\\s]+)\\)", Pattern.CASE_INSENSITIVE);

  /** 「来源…：https://…」整行，剥掉 URL 保留来源文字。 */
  private static final Pattern SOURCE_LINE_WITH_URL = Pattern.compile(
      "(?m)^(来源[^:\\n：]*)[:：]\\s*(https?://\\S+)\\s*$");

  /** 正文裸 http(s) URL（不匹配已在 Markdown 图/链括号内的由其它步骤处理）。 */
  private static final Pattern BARE_HTTP_URL = Pattern.compile(
      "(?<![(\"'\\[])(https?://[^\\s)\\]\"'<>]+)", Pattern.CASE_INSENSITIVE);

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
   * 骨架缺省时的插图提示词硬性要求（提示词包「热点文章骨架配图规范」不可用时回退）。
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
   * <p>只看标题行（{@code marker} 首行）：写明「封面」→ 封面；写明「插图」→ 正文图。
   * {@code PROMPT_BLOCK} 的 marker 组会吞进围栏正文，不可对整段做「封面」子串判断，
   * 否则骨架 {@code imagePromptRequirements} 里的「封面要…」会把正文插图误判成封面。
   *
   * @param marker 标题/加粗标记（可含后续空行与围栏，仅首行参与标题判断）
   * @param prompt 围栏内提示词
   * @return true 表示封面
   */
  public static boolean isCoverPrompt(String marker, String prompt) {
    String heading = promptHeadingLine(marker);
    if (heading.contains("封面")) {
      return true;
    }
    if (heading.contains("插图")) {
      return false;
    }
    String promptText = StrUtil.nullToEmpty(prompt);
    return promptText.contains("热点公众号封面")
        || promptText.contains("热点封面")
        || (promptText.contains("封面") && !promptText.contains("插图"));
  }

  /**
   * 从提示词块 marker 取出标题行（去掉前导换行后的第一行）。
   *
   * @param marker {@link #PROMPT_BLOCK} 捕获的 marker；可空
   * @return 标题行；无则空串
   */
  static String promptHeadingLine(String marker) {
    String text = StrUtil.nullToEmpty(marker).strip();
    if (text.isEmpty()) {
      return "";
    }
    int newline = text.indexOf('\n');
    return newline < 0 ? text : text.substring(0, newline).strip();
  }

  /** 紧跟提示词块后的图片 Markdown。 */
  private static final Pattern IMAGE_AFTER_PROMPT = Pattern.compile(
      "^\\s*!\\[[^\\]]*\\]\\(([^)]+)\\)\\s*");

  /**
   * 归一化提示词文本便于比较是否同一画面。
   *
   * @param prompt 提示词
   * @return 归一化串
   */
  public static String normalizePromptText(String prompt) {
    return StrUtil.nullToEmpty(prompt).replaceAll("\\s+", " ").trim();
  }

  /**
   * 取出提示词块结束后紧随的图片 Markdown 行（若有）。
   *
   * @param content    全文
   * @param promptEnd  提示词块结束下标（不含）
   * @return 图片 Markdown；无则空串
   */
  public static String imageMarkdownAfter(String content, int promptEnd) {
    String text = StrUtil.nullToEmpty(content);
    if (promptEnd < 0 || promptEnd >= text.length()) {
      return "";
    }
    String tail = text.substring(promptEnd);
    Matcher matcher = IMAGE_AFTER_PROMPT.matcher(tail);
    if (matcher.find()) {
      return matcher.group(0).strip();
    }
    return "";
  }

  /**
   * 从图片 Markdown 解析 `/api/images/{id}` 中的 id。
   *
   * @param imageMarkdown 图片行
   * @return 图片 id；解析失败空串
   */
  public static String imageIdFromMarkdown(String imageMarkdown) {
    Matcher matcher = Pattern.compile("/api/images/([^)\\s]+)").matcher(StrUtil.nullToEmpty(imageMarkdown));
    if (matcher.find()) {
      return StrUtil.trim(matcher.group(1));
    }
    return "";
  }

  /**
   * 去掉指定提示词块后紧随的全部图片引用（保留提示词块本身）。
   * <p>连续多张一并去掉，避免历史重复挂图只清掉一张。
   *
   * @param content   全文
   * @param promptHit 提示词命中
   * @return 去图后的正文
   */
  public static String stripImageAfterPrompt(String content, PromptHit promptHit) {
    if (promptHit == null) {
      return StrUtil.nullToEmpty(content);
    }
    String text = StrUtil.nullToEmpty(content);
    int start = promptHit.end();
    if (start < 0 || start > text.length()) {
      return text;
    }
    String tail = text.substring(start);
    int removed = 0;
    while (true) {
      Matcher one = IMAGE_AFTER_PROMPT.matcher(tail.substring(removed));
      if (!one.find() || one.start() != 0) {
        break;
      }
      removed += one.end();
    }
    if (removed <= 0) {
      return text;
    }
    return text.substring(0, start) + tail.substring(removed);
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
    cleaned = cleaned.replaceAll("\\[\\[HOT_NEWS_(?:COVER|IMAGE_\\d+|EVIDENCE:[0-9a-fA-F\\-]{36})]]", " ");
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
   * 抽出公众号正文中的摘要行正文（不含「摘要：」前缀）。
   *
   * @param wechat 公众号 Markdown
   * @return 摘要正文；无则空串
   */
  public static String extractWechatDigestLine(String wechat) {
    Matcher matcher = DIGEST_LINE_PATTERN.matcher(StrUtil.nullToEmpty(wechat));
    if (matcher.find()) {
      return StrUtil.trim(matcher.group(1));
    }
    return "";
  }

  /**
   * 确保公众号正文含规范的 {@code > 摘要：} 行（紧挨一级标题之后），供推送写入微信 digest。
   * <p>已有摘要行则保留并规范化格式；缺失则用 fallback；正文截到 {@link #WECHAT_DIGEST_MAX_CHARS}。
   *
   * @param wechat          公众号 Markdown
   * @param fallbackSummary 缺失时的兜底摘要
   * @return 含摘要行的正文
   */
  public static String ensureWechatDigestLine(String wechat, String fallbackSummary) {
    String text = StrUtil.nullToEmpty(wechat).strip();
    String digest = StrUtil.blankToDefault(extractWechatDigestLine(text), StrUtil.nullToEmpty(fallbackSummary));
    digest = limitDigestChars(digest);
    if (StrUtil.isBlank(digest)) {
      return text;
    }
    // 去掉旧摘要行，再插回标题后
    String withoutDigest = DIGEST_LINE_PATTERN.matcher(text).replaceAll("").replaceAll("\n{3,}", "\n\n").strip();
    String digestLine = "> 摘要：" + digest;
    Matcher titleMatcher = Pattern.compile("^#\\s+.+\\s*$", Pattern.MULTILINE).matcher(withoutDigest);
    if (titleMatcher.find()) {
      int insertAt = titleMatcher.end();
      String before = withoutDigest.substring(0, insertAt).stripTrailing();
      String after = withoutDigest.substring(insertAt).stripLeading();
      if (StrUtil.isBlank(after)) {
        return before + "\n\n" + digestLine + "\n";
      }
      return before + "\n\n" + digestLine + "\n\n" + after;
    }
    return digestLine + "\n\n" + withoutDigest;
  }

  /**
   * 按 Unicode 码点截断摘要，对齐微信 digest 上限。
   *
   * @param text 摘要原文
   * @return 截断后文本
   */
  private static String limitDigestChars(String text) {
    String value = StrUtil.nullToEmpty(text).trim();
    if (value.isEmpty()) {
      return "";
    }
    return value.codePoints()
        .limit(WECHAT_DIGEST_MAX_CHARS)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString()
        .trim();
  }

  /**
   * 将佐证占位展开为 {@code /api/files/{id}} 图片 Markdown。
   *
   * @param markdown 含 {@code [[HOT_NEWS_EVIDENCE:uuid]]} 的正文
   * @return 展开后的正文
   */
  public static String expandEvidenceTokens(String markdown) {
    Matcher matcher = EVIDENCE_TOKEN_PATTERN.matcher(StrUtil.nullToEmpty(markdown));
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String fileId = matcher.group(1);
      String replacement = HotNewsEvidenceKeys.evidenceImageMarkdown(fileId, "来源截图");
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  /** 句末标点（中文）。 */
  private static final String SENTENCE_END_CHARS = "。！？";

  /** caption 开头重复的「来源：」前缀。 */
  private static final Pattern EVIDENCE_CAPTION_SOURCE_PREFIX = Pattern.compile(
      "^(?:来源\\s*[:：]\\s*)+");

  /** 插入点后紧跟的独立「来源：…」行。 */
  private static final Pattern LEADING_SOURCE_LINE = Pattern.compile(
      "^\\s*来源\\s*[:：][^\\n]*\\n?");

  /**
   * 去掉 caption 自带的「来源：」前缀；空则回落「参考资料」。
   *
   * @param caption 模型或调用方给的出处简称
   * @return 可拼进「来源：」行的正文
   */
  public static String normalizeEvidenceCaption(String caption) {
    String value = StrUtil.trim(caption);
    if (StrUtil.isNotBlank(value)) {
      value = EVIDENCE_CAPTION_SOURCE_PREFIX.matcher(value).replaceFirst("");
      value = StrUtil.trim(value);
    }
    return StrUtil.blankToDefault(value, "参考资料");
  }

  /**
   * 在锚点后插入佐证占位；锚点找不到则追加到文末。
   *
   * <p>短锚会吸附到句末，避免劈开「150.58元。还有…」；已有「来源：」行时不叠写第二行。
   * shortcut: 多处同串取首次, 需按 focusHint 消歧时再升级
   *
   * @param markdown    原稿
   * @param insertAfter 锚点子串；可空
   * @param caption     来源文字（不含 URL；可误带「来源：」前缀）
   * @param fileId      截屏文件 id
   * @return 插入后的正文
   */
  public static String insertEvidenceShot(
      String markdown, String insertAfter, String caption, String fileId) {
    if (StrUtil.isBlank(fileId)) {
      return StrUtil.nullToEmpty(markdown);
    }
    String body = StrUtil.nullToEmpty(markdown);
    String normalizedCaption = normalizeEvidenceCaption(caption);
    String token = HotNewsEvidenceKeys.evidenceToken(fileId);
    String anchor = StrUtil.trim(insertAfter);
    if (StrUtil.isNotBlank(anchor)) {
      int index = body.indexOf(anchor);
      if (index >= 0) {
        int insertAt = snapInsertAfterSentenceEnd(body, index + anchor.length());
        return spliceEvidenceAt(body, insertAt, normalizedCaption, token);
      }
    }
    String trimmed = body.stripTrailing();
    return spliceEvidenceAt(trimmed, trimmed.length(), normalizedCaption, token);
  }

  /**
   * 若锚点落在句中，把插入点推到本句句末标点之后；遇段落空行则停在空行前。
   *
   * @param body     全文
   * @param insertAt 锚点结束后的下标
   * @return 校正后的插入下标
   */
  private static int snapInsertAfterSentenceEnd(String body, int insertAt) {
    if (insertAt <= 0 || insertAt >= body.length()) {
      return Math.min(Math.max(insertAt, 0), body.length());
    }
    // 锚点已含句末时不再跨入下一句
    if (isSentenceEndChar(body.charAt(insertAt - 1))) {
      return insertAt;
    }
    char next = body.charAt(insertAt);
    if (isSentenceEndChar(next)) {
      return insertAt + 1;
    }
    if (next == '\n') {
      return insertAt;
    }
    for (int index = insertAt; index < body.length(); index++) {
      if (index + 1 < body.length()
          && body.charAt(index) == '\n'
          && body.charAt(index + 1) == '\n') {
        return index;
      }
      char current = body.charAt(index);
      if (isSentenceEndChar(current)) {
        return index + 1;
      }
      if (current == '\n') {
        return index;
      }
    }
    return body.length();
  }

  /**
   * 在指定下标插入来源行（可复用）与佐证 token。
   *
   * @param body               原文
   * @param insertAt           插入下标
   * @param normalizedCaption  已归一 caption
   * @param token              佐证占位
   * @return 拼接后正文
   */
  private static String spliceEvidenceAt(
      String body, int insertAt, String normalizedCaption, String token) {
    int at = Math.min(Math.max(insertAt, 0), body.length());
    String after = body.substring(at);
    Matcher leadingSource = LEADING_SOURCE_LINE.matcher(after);
    boolean reuseSource = false;
    if (leadingSource.find()) {
      at += leadingSource.end();
      reuseSource = true;
    } else if (endsOnSourceLine(body, at)) {
      reuseSource = true;
    }
    String block = reuseSource
        ? "\n" + token + "\n"
        : "\n\n来源：" + normalizedCaption + "\n" + token + "\n";
    return body.substring(0, at) + block + body.substring(at);
  }

  /**
   * 判断插入点前一行是否已是「来源：…」。
   *
   * @param body     全文
   * @param insertAt 插入下标
   * @return 是否落在已有来源行末尾
   */
  private static boolean endsOnSourceLine(String body, int insertAt) {
    if (insertAt <= 0 || insertAt > body.length()) {
      return false;
    }
    String before = body.substring(0, insertAt);
    int lineStart = before.lastIndexOf('\n') + 1;
    String lastLine = StrUtil.trim(before.substring(lineStart));
    return lastLine.startsWith("来源：") || lastLine.startsWith("来源:");
  }

  /**
   * 是否中文句末标点。
   *
   * @param character 字符
   * @return 是句末则 true
   */
  private static boolean isSentenceEndChar(char character) {
    return SENTENCE_END_CHARS.indexOf(character) >= 0;
  }

  /**
   * 去掉裸外链与 Markdown http 链，保留「来源：文字」与本地 {@code /api/files|images} 图。
   *
   * @param markdown 正文
   * @return 去链后的正文
   */
  public static String stripExternalUrls(String markdown) {
    String text = StrUtil.nullToEmpty(markdown);
    // 来源行：保留简称，去掉 URL
    text = SOURCE_LINE_WITH_URL.matcher(text).replaceAll("$1");
    // [文字](https://…) → 文字（本地 /api 链不在此模式）
    Matcher linkMatcher = MARKDOWN_HTTP_LINK.matcher(text);
    StringBuffer linkBuffer = new StringBuffer();
    while (linkMatcher.find()) {
      String label = StrUtil.blankToDefault(linkMatcher.group(1), "");
      linkMatcher.appendReplacement(linkBuffer, Matcher.quoteReplacement(label));
    }
    linkMatcher.appendTail(linkBuffer);
    text = linkBuffer.toString();
    // 剥剩余裸 URL，但跳过已是 Markdown 图片里的 /api/ 路径（无 http）
    Matcher bareMatcher = BARE_HTTP_URL.matcher(text);
    StringBuffer bareBuffer = new StringBuffer();
    while (bareMatcher.find()) {
      bareMatcher.appendReplacement(bareBuffer, "");
    }
    bareMatcher.appendTail(bareBuffer);
    text = bareBuffer.toString();
    // 清理去链后多余空白
    text = text.replaceAll("[ \\t]+\\n", "\n");
    text = text.replaceAll("\\n{3,}", "\n\n");
    return text;
  }

  /**
   * 展开佐证占位并剥除外链（成文/公众号共用后处理）。
   *
   * @param markdown 正文
   * @return 处理后的正文
   */
  public static String finalizeEvidenceMarkdown(String markdown) {
    return stripExternalUrls(expandEvidenceTokens(markdown));
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
   * 将图片 Markdown 紧跟插入匹配提示词块之后。
   * <p>先清掉该提示词块后已有图片再挂入，避免「块外已有图」时因只检查块内而并列重复。
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
    PromptHit hit = findPromptHit(text, prompt);
    if (hit == null) {
      return text.strip() + "\n\n" + image + "\n";
    }
    // 提示词块 end 之后的改动不影响 hit 起止，可先清再挂
    String cleared = stripImageAfterPrompt(text, hit);
    return cleared.substring(0, hit.end()) + "\n\n" + image + cleared.substring(hit.end());
  }

  /**
   * 按围栏提示词正文查找提示词块；目标空白时取首个块。
   *
   * @param draft      原稿
   * @param promptText 提示词正文
   * @return 命中；无则 null
   */
  public static PromptHit findPromptHit(String draft, String promptText) {
    String target = normalizePromptText(promptText);
    for (PromptHit hit : findPrompts(draft)) {
      if (target.isBlank() || target.equals(normalizePromptText(hit.prompt()))) {
        return hit;
      }
    }
    return null;
  }

  /**
   * 校验原稿中每张配图引用是否紧跟在对应插图提示词块之后。
   * 佐证截屏（{@code /api/files/…}）不参与本校验，可插在正文任意位置。
   *
   * @param draft 配图回填后的原稿
   * @return 合规则为 true
   */
  public static boolean draftImagesFollowPrompts(String draft) {
    String text = StrUtil.nullToEmpty(draft);
    Matcher imageMatcher = Pattern.compile("(?m)^!\\[[^\\]]*\\]\\([^)]+\\)\\s*$").matcher(text);
    while (imageMatcher.find()) {
      String imageLine = imageMatcher.group();
      // 佐证截屏不是插图提示词配图，跳过紧跟校验
      if (isEvidenceImageMarkdown(imageLine)) {
        continue;
      }
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
   * 判断是否为佐证截屏 Markdown（本地 files API，非插图生成结果）。
   *
   * @param imageMarkdown 单行图片 Markdown
   * @return 佐证图为 true
   */
  public static boolean isEvidenceImageMarkdown(String imageMarkdown) {
    return StrUtil.contains(imageMarkdown, HotNewsEvidenceKeys.FILES_API_PREFIX);
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
