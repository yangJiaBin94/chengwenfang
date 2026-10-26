package com.chengwenfang.mode.course.application.course;

import com.chengwenfang.mode.course.application.prompt.CoursePrompts;

import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 分块将课程原稿转为公众号文章（对齐原 ContentGenerationWorkflow）。
 * <p>先把插图提示词换成 token，再按字数切块调用文本模型；
 * 模型压缩过多或明显扩写时回退确定性转换。
 */
public class WechatChunkConverter {
  /** 文本补全最少超时（秒）。 */
  private static final int TEXT_MIN_TIMEOUT_SECONDS = 180;

  /** 文本补全门面。 */
  private final AiTextFacade ai;
  /** Skill 提示词组合。 */
  private final SkillFacade skills;

  /**
   * @param ai     文本 AI
   * @param skills Skill 门面
   */
  public WechatChunkConverter(AiTextFacade ai, SkillFacade skills) {
    this.ai = ai;
    this.skills = skills;
  }

  /**
   * 将原稿分块转换为公众号文章并回调进度。
   *
   * @param draft          课程原稿 Markdown
   * @param ctx            生成上下文（页眉字段来源）
   * @param wechatNode     工作流中 wechat_conversion 节点（取 skillBindings）
   * @param progress       分块进度回调；可为 null
   * @param repairFeedback 上轮终检/等价性反馈；可为 null
   * @return 带页眉的公众号全文、图片 token 列表与分块数
   * @throws IllegalStateException 转换后缺少图片 token 时
   */
  public Result convert(
      String draft,
      CourseGenerationContext ctx,
      Map<String, Object> wechatNode,
      ProgressSink progress,
      String repairFeedback) {
    CourseMarkdownUtil.WechatSource source = CourseMarkdownUtil.prepareWechatSource(draft);
    List<String> chunks = CourseMarkdownUtil.splitMarkdown(source.prepared(), CourseMarkdownUtil.WECHAT_CHUNK_LIMIT);
    String skillPrompt = skills.composePromptFromBindings(skillBindings(wechatNode));
    List<String> outputs = new ArrayList<>();
    int total = Math.max(1, chunks.size());
    String feedback = repairFeedback == null || repairFeedback.isBlank()
        ? "无，这是首次转换"
        : repairFeedback;
    for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
      int index = chunkIndex + 1;
      String chunk = chunks.get(chunkIndex);
      if (progress != null) {
        progress.onChunk(index, total, "wechat_chunk:" + index);
      }
      String positionRule = index == 1
          ? "这是第一段，请生成适合公众号阅读的主标题；课程信息区和封面由成文坊统一组装，不要自行生成。"
          : "这是续写段落，不要新增主标题，不要重复前文。";
      String userPrompt = """
          请将下面课程原稿的第 %d/%d 段转换为公众号 Markdown。%s
          只调整排版和表达节奏，不删减也不扩写知识点、例子、公式或图片引用。
          原稿本段共 %d 个字符，转换结果不得少于原稿本段的 75%%，也不得超过原稿本段的 135%%。
          全文去除插图提示词与图片引用后的正文不得超过本次课程上限 %d 字；宁可保持原稿篇幅，也不要补充新解释。
          必须原样保留 [[COURSE_COVER]] / [[COURSE_IMAGE_N]] 占位，不要删除、改写或解释占位符。
          禁止输出「插图位置」「提示词」、```text 生图围栏、Midjourney 参数（如 --v 6）、Apple Keynote/深海蓝等生图描述段落。
          删除课程编号、水平分隔线、Markdown 表格；非图片用途的 python/bash 等代码围栏可删除，表格改为列表。
          只输出本段转换结果，不解释过程。

          上轮检查反馈：%s
          原稿分段：
          %s
          """.formatted(
          index, total, positionRule, chunk.length(), ctx.maxBodyChars(), feedback, chunk);
      String converted = complete(skillPrompt, userPrompt, "wechat_chunk_" + index);
      if (!CourseMarkdownUtil.wechatChunkComplete(chunk, converted)) {
        converted = CourseMarkdownUtil.deterministicWechatChunk(chunk);
      }
      outputs.add(converted.strip());
    }
    // 对齐 Python：终端章节规范化 → 缺 token 抛错 → 清生图残留 → 独立命令行规范化 → 页眉
    String merged = CourseMarkdownUtil.normalizeTerminalSections(String.join("\n\n", outputs));
    List<String> missing = source.imageTokens().stream().filter(token -> !merged.contains(token)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "WECHAT_CONTENT_NOT_EQUIVALENT: 公众号版本缺少 " + missing.size() + " 个图片占位符");
    }
    // 转换模型可能拆掉围栏留下裸提示词，此处确定性清掉
    String stripped = CourseImagePipeline.stripImageProductionResidue(merged);
    String normalized = CourseMarkdownUtil.normalizeStandaloneCodeLines(stripped);
    String withHeader = buildWechatHeader(normalized, ctx);
    return new Result(withHeader, source.imageTokens(), chunks.size());
  }

  /**
   * 兼容旧调用：无修复反馈。
   */
  public Result convert(
      String draft,
      CourseGenerationContext ctx,
      Map<String, Object> wechatNode,
      ProgressSink progress) {
    return convert(draft, ctx, wechatNode, progress, null);
  }

  private String complete(String skillPrompt, String userPrompt, String scene) {
    String systemPrompt = CoursePrompts.systemPrompt(skillPrompt);
    var result = ai.completeText(TextCompleteRequest.builder()
        .prompt(userPrompt.strip())
        .systemPrompt(systemPrompt)
        .scene(scene)
        .timeoutSeconds(TEXT_MIN_TIMEOUT_SECONDS)
        .build());
    if (result.getText() == null || result.getText().isBlank()) {
      throw new IllegalStateException("GENERATED_CONTENT_INVALID: 文本生成返回为空，scene=" + scene);
    }
    String text = result.getText().strip();
    if (text.regionMatches(true, 0, "<html", 0, 5)) {
      throw new IllegalStateException("GENERATED_CONTENT_INVALID: 文本生成返回 HTML，scene=" + scene);
    }
    return text;
  }

  /**
   * 为公众号正文组装页眉（系列/难度/阅读时长/摘要）并插入封面 token。
   */
  public static String buildWechatHeader(String content, CourseGenerationContext ctx) {
    String stripped = (content == null ? "" : content).strip()
        .replace(CourseMarkdownUtil.COURSE_COVER_TOKEN, "");
    Pattern titlePattern = Pattern.compile("^#\\s+(.+?)\\s*\\n");
    Matcher titleMatch = titlePattern.matcher(stripped);
    String title;
    String body;
    if (titleMatch.find()) {
      title = titleMatch.group(1).strip();
      body = stripped.substring(titleMatch.end()).stripLeading();
    } else {
      title = ctx.title();
      body = stripped;
    }
    if (Pattern.compile("^>\\s*系列[:：]").matcher(body).find()) {
      body = body.replaceFirst("^(?:>.*(?:\\n|$))+\\s*", "");
    }
    String seriesName = ctx.seriesName() == null ? "未命名系列" : ctx.seriesName();
    if (!seriesName.toUpperCase().startsWith("AI")) {
      seriesName = "AI " + seriesName;
    }
    String difficulty = ctx.difficulty() == null || ctx.difficulty().isBlank()
        ? "零基础"
        : ctx.difficulty();
    String coverage = ctx.summary().isBlank() ? ctx.title() : ctx.summary();
    int readable = body.replaceAll("\\s+|[#>*_`\\[\\]()\\\\]", "").length();
    int readingMinutes = Math.max(5, Math.round(readable / 750f));
    String summary = "本课围绕" + ctx.title() + "，讲清" + coverage + "。";
    if (ctx.followingTitle() != null && !ctx.followingTitle().isBlank()) {
      summary += "读完后可以继续学习" + ctx.followingTitle() + "。";
    }
    return """
        # %s

        > 系列：%s｜难度：%s｜预计阅读：%d 分钟
        >
        > 本课覆盖：%s
        >
        > 摘要：%s

        %s

        %s
        """.formatted(
            title,
            seriesName,
            difficulty,
            readingMinutes,
            coverage,
            summary,
            CourseMarkdownUtil.COURSE_COVER_TOKEN,
            body).strip();
  }

  @SuppressWarnings("unchecked")
  private static List<SkillPromptBindingView> skillBindings(Map<String, Object> node) {
    if (node == null || !(node.get("skillBindings") instanceof List<?> list)) {
      return List.of();
    }
    List<SkillPromptBindingView> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> m = (Map<String, Object>) map;
        Object priority = m.get("priority");
        out.add(SkillPromptBindingView.builder()
            .skillId(m.get("skillId") == null ? null : String.valueOf(m.get("skillId")))
            .priority(priority instanceof Number number ? number.intValue() : 100)
            .enabled(m.get("enabled") == null || Boolean.TRUE.equals(m.get("enabled")))
            .build());
      }
    }
    return out;
  }

  @FunctionalInterface
  public interface ProgressSink {
    void onChunk(int index, int total, String step);
  }

  /**
   * 分块转换结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Result {
    /** 公众号正文。 */
    private final String wechat;
    /** 内文图片 token。 */
    private final List<String> imageTokens;
    /** 分块数。 */
    private final int chunkCount;
  }
}
