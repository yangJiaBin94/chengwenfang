package com.chengwenfang.mode.course.application.course;

import java.util.List;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 课程 Markdown 共用辅助：正文字数、插图位解析、公众号分块与确定性转换等。
 * <p>无状态工具类，禁止实例化。
 */
public final class CourseMarkdownUtil {
  /** 封面占位 token，配图阶段替换为真实 Markdown 图片。 */
  public static final String COURSE_COVER_TOKEN = "[[COURSE_COVER]]";
  /** 公众号分块转换最低保留率（转换结果长度 / 源段长度）。 */
  public static final double WECHAT_CHUNK_MIN_PRESERVATION = 0.75;
  /**
   * 公众号分块最大膨胀率。原版仅有 75% 下限；当前文本模型常把短稿扩到数倍，
   * 触发 COURSE_BODY_TOO_LONG。超过则回退确定性转换，避免 1.2 万字级扩写。
   */
  public static final double WECHAT_CHUNK_MAX_EXPANSION = 1.35;
  /** 未识别字数档位时的默认正文上限。 */
  public static final int DEFAULT_MAX_LESSON_BODY_CHARS = 5000;
  /** 公众号转换单块字符上限。 */
  public static final int WECHAT_CHUNK_LIMIT = 2400;

  /**
   * 插图提示词块：匹配含「插图/提示词/prompt」的标题或加粗行，以及随后的 fenced prompt。
   */
  public static final Pattern PROMPT_BLOCK_JAVA = Pattern.compile(
      "(?<marker>(?:^|\\n)(?!\\s*!\\[)(?:\\s*#{1,6}\\s+|\\s*\\*\\*)"
          + "[^\\n]*(?:插图|提示词|prompt)[^\\n]*\\n+"
          + "(?:(?!\\s*```)[^\\n]*\\n+){0,4}"
          + "```[^\\n]*\\n(?<prompt>.*?)\\n```)",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  public static final Pattern FENCED_BLOCK_JAVA = Pattern.compile(
      "(?is)(?:```|~~~)[^\\n]*\\R.*?\\R(?:```|~~~)");

  /** 工具类不可实例化。 */
  private CourseMarkdownUtil() {}

  /**
   * 判断提示词块是否为封面图（marker/prompt 含封面语义）。
   *
   * @param marker 标题/加粗标记行
   * @param prompt fenced 内提示词正文
   * @return true 表示封面提示词
   */
  public static boolean isCoverPrompt(String marker, String prompt) {    return CourseMarkdownImages.isCoverPrompt(marker, prompt);
  }

  /**
   * 统计课程「正文字数」：去掉提示词块、全部代码/提示词围栏、图片与链接语法、占位符、
   * 标题前缀与强调符后的非空白字符数。
   *
   * @param content Markdown 全文；null 视为空
   * @return 正文字符数
   */
  public static int lessonBodyCharCount(String content) {    return CourseMarkdownFormulas.lessonBodyCharCount(content);
  }

  /**
   * 按二级标题优先、再按段落将 Markdown 切分为不超过 limit 的块。
   *
   * @param content 源 Markdown
   * @param limit   单块最大字符数
   * @return 非空块列表；空输入返回单元素空串列表
   */
  public static List<String> splitMarkdown(String content, int limit) {    return CourseMarkdownSections.splitMarkdown(content, limit);
  }

  /**
   * 扫描全文中的插图提示词块。
   *
   * @param content Markdown；null 视为空
   * @return 命中列表（含 marker、prompt 与起止下标）
   */
  public static List<PromptHit> findPrompts(String content) {    return CourseMarkdownImages.findPrompts(content);
  }

  /**
   * 为公众号转换准备源文：封面提示词删除，内文提示词替换为 {@code [[COURSE_IMAGE_N]]}。
   *
   * @param draft 课程原稿
   * @return 替换后的正文与 token 顺序列表
   */
  public static WechatSource prepareWechatSource(String draft) {    return CourseMarkdownImages.prepareWechatSource(draft);
  }

  /**
   * 判断单块公众号转换是否合格：保留率 ∈ [75%, 135%]，且源中图片 token 均保留。
   */
  public static boolean wechatChunkComplete(String source, String converted) {    return CourseMarkdownFormulas.wechatChunkComplete(source, converted);
  }

  /**
   * 确定性公众号块转换：去分隔线/代码围栏/表格线，表格改列表，清洗一级标题课次编号。
   * <p>进入 fenced 块后整段丢弃至闭合围栏，避免只删 {@code ```} 行却把提示词正文留成段落。
   *
   * @param source 源 Markdown 块
   * @return 转换后文本
   */
  public static String deterministicWechatChunk(String source) {    return CourseMarkdownImages.deterministicWechatChunk(source);
  }

  /**
   * 规范化终端章节：重复的「本课小结」保留首个，重复的「下节课预告」保留末个。
   *
   * @param content 合并后的公众号正文
   * @return 去重后的正文
   */
  public static String normalizeTerminalSections(String content) {    return CourseMarkdownSections.normalizeTerminalSections(content);
  }

  /**
   * 将独立一行的 shell/Python 命令反引号改写为缩进代码行（对齐 Python
   * {@code _normalize_standalone_code_lines}）。
   */
  public static String normalizeStandaloneCodeLines(String content) {    return CourseMarkdownNormalizer.normalizeStandaloneCodeLines(content);
  }

  /**
   * 将字数档位解析为正文上限。
   *
   * @param wordCountOption 档位键，如 {@code 2000-4000}
   * @return 上限字符数
   */
  public static int maxBodyChars(String wordCountOption) {    return CourseMarkdownFormulas.maxBodyChars(wordCountOption);
  }

  /**
   * 将字数档位解析为正文下限；未识别档位为 0。
   *
   * @param wordCountOption 档位键
   * @return 下限字符数
   */
  public static int minBodyChars(String wordCountOption) {    return CourseMarkdownFormulas.minBodyChars(wordCountOption);
  }

  /**
   * 原版 Python 不对分段做「任务复述」硬拒收；越界的小结/预告标题由
   * {@link #sanitizeDraftSection} 裁掉。此方法保留为兼容入口，恒返回 null。
   *
   * @param content    已清洗的本节 Markdown
   * @param sectionKey {@code draft_opening} 等
   * @return 始终为 null
   */
  public static String sectionRejectionReason(String content, String sectionKey) {    return CourseMarkdownSections.sectionRejectionReason(content, sectionKey);
  }

  /** 去掉围栏代码后再做正文扫描，避免代码示例里的词误伤。 */
  public static String stripFencedCode(String text) {    return CourseMarkdownNormalizer.stripFencedCode(text);
  }

  /**
   * 将配图档位解析为 [min, max] 插图数量区间。
   *
   * @param imageCountOption 档位键，如 {@code 3-5}
   * @return 长度为 2 的数组：下限、上限
   */
  public static int[] imageBounds(String imageCountOption) {    return CourseMarkdownImages.imageBounds(imageCountOption);
  }

  /**
   * 清洗分段原稿：丢掉复述提示词的前言，补上本节标题，并裁掉不该出现的小结/预告。
   *
   * @param content    模型原文
   * @param sectionKey 段落 typeKey，如 {@code draft_core}
   * @param heading    本节标题，如「开场与本课目标」
   * @return 可拼进原稿的 Markdown
   */
  public static String sanitizeDraftSection(String content, String sectionKey, String heading) {    return CourseMarkdownSections.sanitizeDraftSection(content, sectionKey, heading);
  }

  /**
   * 去掉全文标题前的推理前言，并裁掉「下节课预告」之后的思维链尾巴。
   * <p>原版 Python 压缩后不做专用裁剪；当前模型常在预告后继续自检字数，
   * 若不裁掉，终检 {@code content.count("本课小结") != 1} 会误杀。
   *
   * @param content Markdown 全文
   * @return 可提交的课程原稿
   */
  public static String sanitizeFullDraft(String content) {    return CourseMarkdownNormalizer.sanitizeFullDraft(content);
  }

  /**
   * 保留最后一个「下节课预告」标题及其正常正文，裁掉其后的推理/自检尾巴。
   */
  public static String stripTrailingReasoningAfterPreview(String content) {    return CourseMarkdownNormalizer.stripTrailingReasoningAfterPreview(content);
  }

  /**
   * 压缩目标字数：在上限下留安全余量，并按超标比例收紧。
   *
   * @param bodyChars    当前正文字数
   * @param maxBodyChars 正文上限
   * @return 本轮压缩目标（至少 1）
   */
  public static int compressionTarget(int bodyChars, int maxBodyChars) {    return CourseMarkdownFormulas.compressionTarget(bodyChars, maxBodyChars);
  }

  /**
   * 压缩丢失的插图提示词块插回「本课小结」之前。
   *
   * @param content  压缩后正文
   * @param required 压缩前收集的提示词块
   * @return 回填后的正文
   */
  public static String restoreMissingPromptBlocks(String content, List<PromptHit> required) {    return CourseMarkdownImages.restoreMissingPromptBlocks(content, required);
  }

  /**
   * 从二级标题抽取可用于补插图的具体题材，避免一律回落到课名。
   *
   * @param content     原稿
   * @param courseTitle 课名（用于排除）
   * @return 去重后的题材列表；可能为空
   */
  public static List<String> extractIllustrationSubjects(String content, String courseTitle) {    return CourseMarkdownImages.extractIllustrationSubjects(content, courseTitle);
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
      String content, int minimum, String courseTitle, List<String> examples) {    return CourseMarkdownImages.ensureInlinePromptCount(content, minimum, courseTitle, examples);
  }

  /**
   * 删除超出上限的内文插图提示词块（保留封面）。
   *
   * @param content 原稿
   * @param maximum 内文插图上限
   * @return 裁剪后的正文
   */
  public static String limitInlinePrompts(String content, int maximum) {    return CourseMarkdownImages.limitInlinePrompts(content, maximum);
  }

  /**
   * 将内文插图提示词按标题锚点分散，降低堆叠。
   *
   * @param content 原稿
   * @return 重排后的正文；不足 2 个内文块则原样
   */
  public static String distributeInlinePrompts(String content) {    return CourseMarkdownImages.distributeInlinePrompts(content);
  }

  /**
   * 统计内文插图提示词数量（不含封面）。
   *
   * @param content 原稿
   * @return 内文提示词块数
   */
  public static int countInlinePrompts(String content) {    return CourseMarkdownImages.countInlinePrompts(content);
  }

  /**
   * 素材索要标记命中次数（对齐原版 {@code MATERIAL_REQUEST_MARKERS}）。
   *
   * @param content 正文
   * @return 命中条数
   */
  public static int countMaterialRequestMarkers(String content) {    return CourseMarkdownImages.countMaterialRequestMarkers(content);
  }

  /** 非重叠子串计数。 */
  public static long countOccurrences(String text, String needle) {    return CourseMarkdownFormulas.countOccurrences(text, needle);
  }

  /**
   * 删除提示词正文为空的插图块（含封面空块）。
   */
  public static String stripEmptyPromptBlocks(String content) {    return CourseMarkdownNormalizer.stripEmptyPromptBlocks(content);
  }

  /**
   * 剔除夹在正文中的推理腔段落（压缩/分段模型自言自语）。
   * <p>按 {@code \\R\\R+} 分段，兼容 CRLF 原稿。
   */
  public static String stripInlineReasoningParagraphs(String content) {    return CourseMarkdownNormalizer.stripInlineReasoningParagraphs(content);
  }

  /**
   * 压缩脏稿常见「半截草稿 + 推理 + 完整草稿」：若有多个一级标题，保留最后一份含小结/预告的完整稿。
   */
  public static String preferLastCompleteDraft(String content) {    return CourseMarkdownNormalizer.preferLastCompleteDraft(content);
  }

  /**
   * 将脏插图写法收成权威标准块（{@code **插图位置** + {@code ```text}）。
   *
   * @param content 原稿
   * @return 规范化后正文
   */
  public static String canonicalizeIllustrationPrompts(String content) {    return CourseMarkdownImages.canonicalizeIllustrationPrompts(content);
  }

  /**
   * 删除无标记的裸生图提示词段落。
   *
   * @param content Markdown
   * @return 清理后正文
   */
  public static String stripBareImagePromptParagraphs(String content) {    return CourseMarkdownImages.stripBareImagePromptParagraphs(content);
  }

  /**
   * 公众号是否仍含裸生图提示词残留。
   *
   * @param wechat 公众号正文
   * @return 有泄漏则为 true
   */
  public static boolean hasBareImagePromptLeak(String wechat) {    return CourseMarkdownImages.hasBareImagePromptLeak(wechat);
  }

  /**
   * 清除非正式「插图位置 / 提示词」行（无加粗/标题的裸行；不碰标准 **插图位置** + 围栏块）。
   */
  public static String stripMalformedIllustrationNotes(String content) {    return CourseMarkdownImages.stripMalformedIllustrationNotes(content);
  }

  /**
   * 压缩/拼装后的可发布原稿出口：规范化提示词、去空提示词、去推理腔、对齐插图数量。
   */
  public static String finalizePublishableDraft(
      String content, int minImages, int maxImages, String courseTitle) {    return CourseMarkdownNormalizer.finalizePublishableDraft(content, minImages, maxImages, courseTitle);
  }

  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class PromptHit {
    /** 标题/加粗行。 */
    private final String marker;
    /** 围栏内提示词。 */
    private final String prompt;
    /** 在原文中的起始下标。 */
    private final int start;
    /** 结束下标（不含）。 */
    private final int end;
  }

  /**
   * 公众号转换源：已替换 token 的正文与 token 列表。
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
