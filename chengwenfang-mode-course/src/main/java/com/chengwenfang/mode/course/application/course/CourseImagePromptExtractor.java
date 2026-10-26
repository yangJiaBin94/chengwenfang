package com.chengwenfang.mode.course.application.course;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课程配图：从原稿提取插图提示词并构建生成计划。
 */
public final class CourseImagePromptExtractor {
  /** 识别「需真实截图」类占位，这类不走 AI 生图。 */
  private static final Pattern SCREENSHOT_MARKER =
      Pattern.compile("操作截图|真实截图|页面截图|界面截图", Pattern.CASE_INSENSITIVE);
  /** 截图语义提示词模式。 */
  private static final Pattern SCREENSHOT_PROMPT = Pattern.compile(
      "(?:真实|实际).{0,8}(?:软件|网页|网站|页面|界面|终端|控制台|运行结果).{0,8}截图|"
          + "(?:截取|截图).{0,16}(?:软件|网页|网站|页面|界面|菜单|按钮|终端|控制台|运行结果)|"
          + "(?:安装|配置|登录|创建项目|选择解释器|运行代码).{0,16}(?:页面|界面|步骤|截图)",
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /** 工具类不可实例化。 */
  private CourseImagePromptExtractor() {}

  /**
   * 根据提示词命中构建封面+内文配图计划。
   *
   * @param prompts 提示词命中
   * @param ctx     课程上下文
   * @return 计划项列表
   */
  static List<CourseImagePipeline.ImagePlanItem> buildPlan(
      List<CourseMarkdownUtil.PromptHit> prompts, CourseGenerationContext ctx) {
    CourseMarkdownUtil.PromptHit cover = prompts.stream()
        .filter(prompt -> CourseMarkdownUtil.isCoverPrompt(prompt.marker(), prompt.prompt()))
        .findFirst()
        .orElse(null);
    String coverPrompt = cover == null ? coverPrompt(ctx) : cover.prompt();
    List<CourseImagePipeline.ImagePlanItem> plan = new ArrayList<>();
    plan.add(new CourseImagePipeline.ImagePlanItem(
        1, null, "cover",
        cover == null ? "" : cover.marker(),
        coverPrompt,
        CourseMarkdownUtil.COURSE_COVER_TOKEN,
        "2400x1024",
        "image_generation"));
    int inlineNo = 0;
    for (CourseMarkdownUtil.PromptHit hit : prompts) {
      if (CourseMarkdownUtil.isCoverPrompt(hit.marker(), hit.prompt())) {
        continue;
      }
      inlineNo++;
      boolean screenshot = requiresScreenshot(hit.marker(), hit.prompt());
      plan.add(new CourseImagePipeline.ImagePlanItem(
          inlineNo + 1,
          inlineNo,
          "inline",
          hit.marker(),
          hit.prompt(),
          "[[COURSE_IMAGE_" + inlineNo + "]]",
          "2048x1152",
          screenshot ? "screenshot_placeholder" : "image_generation"));
    }
    return plan;
  }

  /**
   * 默认封面提示词。
   *
   * @param ctx 课程上下文
   * @return 封面提示词
   */
  static String coverPrompt(CourseGenerationContext ctx) {
    String coverage = ctx.summary().isBlank() ? ctx.title() : ctx.summary();
    return "课程《" + ctx.title() + "》的公众号横版封面，核心内容为：" + coverage + "。"
        + "使用一个清晰的中心视觉隐喻表达本课核心结论，不堆叠正文公式；"
        + "画面适合在移动端首屏识别，标题文字必须完整位于中央 1:1 转发卡片安全区内，"
        + "四周留出安全边距，不能靠近边缘或被卡片裁切；使用 2.35:1 横向构图。";
  }

  /**
   * 提示词前 500 + 后 500，压空白后最多 700 字。
   *
   * @param content 全文
   * @param prompt  目标提示词
   * @return 相邻正文摘要
   */
  static String imageArticleContext(String content, String prompt) {
    if (content == null || prompt == null) {
      return "";
    }
    Matcher matcher = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(content);
    while (matcher.find()) {
      if (!prompt.strip().equals(matcher.group("prompt").strip())) {
        continue;
      }
      String before = content.substring(Math.max(0, matcher.start() - 500), matcher.start());
      String after = content.substring(
          matcher.end(), Math.min(content.length(), matcher.end() + 500));
      String joined = CourseMarkdownUtil.PROMPT_BLOCK_JAVA.matcher(before + "\n" + after)
          .replaceAll(" ");
      joined = joined.replaceAll("(?s)```.*?```|!\\[[^]]*\\]\\([^)]*\\)", " ");
      String collapsed = joined.replaceAll("\\s+", " ").strip();
      return collapsed.substring(0, Math.min(700, collapsed.length()));
    }
    return "";
  }

  /**
   * 是否需要真实截图占位（不调图片模型）。
   *
   * @param marker 标记行
   * @param prompt 提示词
   * @return true 表示截图占位
   */
  static boolean requiresScreenshot(String marker, String prompt) {
    return SCREENSHOT_MARKER.matcher(marker == null ? "" : marker).find()
        || SCREENSHOT_PROMPT.matcher(prompt == null ? "" : prompt).find();
  }
}
