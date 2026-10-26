package com.workbench.quality.domain.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

/** 课程/公众号文章确定性审核引擎（核心规则集）。 */
@Component
public class CourseAuditEngine {
  private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
  private static final Pattern MARKDOWN_LINK = Pattern.compile("(?<!!)\\[([^\\]]*)\\]\\((?:[^()]|\\([^)]*\\))*\\)");
  private static final Pattern OBSIDIAN_IMAGE = Pattern.compile("!\\[\\[([^\\]|]+)(?:\\|[^\\]]+)?\\]\\]");
  private static final Pattern AUTOLINK = Pattern.compile("<https?://[^>]+>", Pattern.CASE_INSENSITIVE);
  private static final Pattern URL = Pattern.compile("https?://[^\\s<>)\\]]+", Pattern.CASE_INSENSITIVE);
  private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]*`");
  private static final Pattern FENCED_CODE = Pattern.compile("```[\\s\\S]*?```");

  /**
   * 审核输入上下文。
   */
  @Getter
  @Accessors(fluent = true)
  public static final class AuditContext {
    /** 公众号正文。 */
    private final String article;
    /** 课程原稿。 */
    private final String draft;
    /** 图片资产列表。 */
    private final List<Map<String, Object>> imageAssets;
    /** 公开图片路径。 */
    private final List<String> imagePublicPaths;
    /** 当前版本是否已保存。 */
    private final boolean currentVersionSaved;
    /** 文章文件路径。 */
    private final String articleFilePath;

    /**
     * @param article             公众号正文
     * @param draft               课程原稿
     * @param imageAssets         图片资产列表
     * @param imagePublicPaths    公开图片路径
     * @param currentVersionSaved 当前版本是否已保存
     * @param articleFilePath     文章文件路径
     */
    public AuditContext(
        String article,
        String draft,
        List<Map<String, Object>> imageAssets,
        List<String> imagePublicPaths,
        boolean currentVersionSaved,
        String articleFilePath) {
      this.article = article == null ? "" : article;
      this.draft = draft == null ? "" : draft;
      this.imageAssets = imageAssets == null ? List.of() : imageAssets;
      this.imagePublicPaths = imagePublicPaths == null ? List.of() : imagePublicPaths;
      this.currentVersionSaved = currentVersionSaved;
      this.articleFilePath = articleFilePath;
    }
  }

  /**
   * 单条审核检查项。
   */
  @Getter
  @Accessors(fluent = true)
  public static final class AuditCheck {
    /** 检查编码。 */
    private final String code;
    /** 标题。 */
    private final String title;
    /** 详情。 */
    private final String detail;
    /** 状态（pass/warn/fail）。 */
    private final String status;
    /** 是否可由文本 AI 修复。 */
    private final boolean fixableByTextAi;
    /** 问题位置。 */
    private final String location;
    /** 修复建议。 */
    private final String suggestion;

    /**
     * @param code            检查编码
     * @param title           标题
     * @param detail          详情
     * @param status          状态
     * @param fixableByTextAi 是否可由文本 AI 修复
     * @param location        问题位置
     * @param suggestion      修复建议
     */
    public AuditCheck(
        String code,
        String title,
        String detail,
        String status,
        boolean fixableByTextAi,
        String location,
        String suggestion) {
      this.code = code;
      this.title = title;
      this.detail = detail;
      this.status = status;
      this.fixableByTextAi = fixableByTextAi;
      this.location = location;
      this.suggestion = suggestion;
    }

    /**
     * @return 是否阻断（fail）
     */
    public boolean blocking() {
      return "fail".equals(status);
    }
  }

  /**
   * 审核汇总结果。
   */
  @Getter
  @Accessors(fluent = true)
  public static final class AuditResult {
    /** 总分。 */
    private final int score;
    /** 通过数。 */
    private final int passed;
    /** 警告数。 */
    private final int warnings;
    /** 失败数。 */
    private final int failed;
    /** 检查项列表。 */
    private final List<AuditCheck> items;

    /**
     * @param score    总分
     * @param passed   通过数
     * @param warnings 警告数
     * @param failed   失败数
     * @param items    检查项列表
     */
    public AuditResult(int score, int passed, int warnings, int failed, List<AuditCheck> items) {
      this.score = score;
      this.passed = passed;
      this.warnings = warnings;
      this.failed = failed;
      this.items = items;
    }
  }

  /** 对课程或公众号正文执行核心规则审核。 */
  public AuditResult run(AuditContext context) {
    String article = context.article();
    List<Map<String, Object>> assets = context.imageAssets();
    boolean summaryOk = hasSummaryBlock(article);
    List<String> forbidden = forbiddenSymbols(article);
    int summaryIndex = indexOfAny(article, "本课小结", "小结");
    int previewIndex = indexOfAny(article, "下节课预告", "下一篇预告");
    boolean orderOk = summaryIndex >= 0 && previewIndex >= 0 && summaryIndex < previewIndex;
    int articleImageCount = imageCount(article);
    int syncedCount = syncedImageCount(context);
    boolean metadataComplete = !assets.isEmpty() && assets.stream().allMatch(asset ->
        has(asset, "name") && has(asset, "path") && has(asset, "articleId")
            && (context.articleFilePath() == null || context.articleFilePath().isBlank() || has(asset, "articlePath")));

    List<AuditCheck> checks = new ArrayList<>();
    checks.add(check("ARTICLE_EXISTS", "公众号文章已生成",
        article.isEmpty() ? "当前课程还没有公众号文章。" : "当前文章 " + article.length() + " 字符。",
        article.isEmpty() ? "fail" : "pass", false, null,
        article.isEmpty() ? "先生成公众号文章。" : null));
    checks.add(check("OPENING_SUMMARY_COMPLETE", "开头摘要信息完整",
        summaryOk ? "已识别系列、覆盖范围和课程摘要。" : "开头 10 行未同时识别到系列、本课覆盖和课程摘要。",
        summaryOk ? "pass" : "warn", true, summaryOk ? null : "文章开头 10 行",
        summaryOk ? null : "补充系列、本课覆盖和课程摘要。"));
    checks.add(check("FORBIDDEN_SYMBOL", "正文禁用符号检查",
        forbidden.isEmpty() ? "未发现 //、-> 或 ——。" : "发现 " + String.join("、", forbidden) + "，需要替换为公众号规范写法。",
        forbidden.isEmpty() ? "pass" : "fail", true,
        forbidden.isEmpty() ? null : forbiddenLocation(article),
        forbidden.isEmpty() ? null : "改为中文可读标点或文字说明。"));
    checks.add(check("CODE_FENCE", "代码块检查",
        article.contains("```") ? "发现 triple-backtick code fence，公众号排版规范不建议保留。" : "未发现 triple-backtick code fence。",
        article.contains("```") ? "warn" : "pass", true, null,
        article.contains("```") ? "改为普通段落、列表或行内代码。" : null));
    checks.add(check("SUMMARY_PREVIEW_ORDER", "小结与预告顺序",
        orderOk ? "小结位于下节课预告前。" : "未确认“小结在下节课预告前”的顺序。",
        orderOk ? "pass" : "warn", true, null,
        orderOk ? null : "补全小结和预告，并将小结放在预告之前。"));
    String assetsStatus = !assets.isEmpty() ? "pass" : (syncedCount > 0 ? "warn" : "fail");
    checks.add(check("IMAGE_ASSETS_PRESENT", "配套图片资产",
        !assets.isEmpty()
            ? "已保存 " + assets.size() + " 条结构化图片资产，包含名称、路径和所属文章。"
            : "当前从页面同步到 " + syncedCount + " 张图片，但缺少结构化 imageAssets。",
        assetsStatus, false, null,
        !assets.isEmpty() ? null : "补充或重新生成结构化图片资产。"));
    checks.add(check("IMAGE_METADATA_COMPLETE", "图片名称和路径完整",
        metadataComplete ? "所有结构化图片资产字段完整。"
            : (context.articleFilePath() != null && !context.articleFilePath().isBlank()
                ? "存在图片资产缺少名称、路径、所属文章 ID 或文章文件路径。"
                : "存在图片资产缺少名称、路径或所属文章 ID。"),
        metadataComplete ? "pass" : "warn", false, null,
        metadataComplete ? null : "补齐图片名称、路径和所属文章 ID。"));
    checks.add(check("ARTICLE_IMAGE_REFERENCE", "文章图片引用",
        articleImageCount > 0 ? "文章 " + articleImageCount + " 张图片引用。" : "文章中未识别到图片引用。",
        articleImageCount > 0 ? "pass" : "warn", !assets.isEmpty(), null,
        articleImageCount > 0 ? null : "仅在已有图片资产时，将对应 Markdown 图片引用补入文章。"));
    checks.add(check("MDNICE_MARKDOWN_READY", "mdnice 发布稿",
        article.isBlank() ? "尚未生成可复制到 mdnice 的发布稿。" : "已生成 " + article.strip().length() + " 字符的 mdnice Markdown。",
        article.isBlank() ? "fail" : "pass", false, null,
        article.isBlank() ? "先生成公众号文章。" : null));
    checks.add(check("CURRENT_VERSION_SAVED", "当前文章版本已保存",
        context.currentVersionSaved() ? "当前正文已绑定数据库版本。" : "当前正文没有对应的数据库版本。",
        context.currentVersionSaved() ? "pass" : "fail", false, null,
        context.currentVersionSaved() ? null : "先保存当前文章版本再继续审核。"));

    int failed = (int) checks.stream().filter(check -> "fail".equals(check.status())).count();
    int warnings = (int) checks.stream().filter(check -> "warn".equals(check.status())).count();
    int passed = (int) checks.stream().filter(check -> "pass".equals(check.status())).count();
    int score = Math.max(0, 100 - failed * 18 - warnings * 7);
    return new AuditResult(score, passed, warnings, failed, checks);
  }

  private static AuditCheck check(
      String code, String title, String detail, String status, boolean fixable, String location, String suggestion) {
    return new AuditCheck(code, title, detail, status, fixable, location, suggestion);
  }

  String visibleText(String content) {
    String text = FENCED_CODE.matcher(content).replaceAll("");
    text = INLINE_CODE.matcher(text).replaceAll("");
    text = MARKDOWN_IMAGE.matcher(text).replaceAll("$1");
    text = MARKDOWN_LINK.matcher(text).replaceAll("$1");
    text = OBSIDIAN_IMAGE.matcher(text).replaceAll("$1");
    text = AUTOLINK.matcher(text).replaceAll("");
    return URL.matcher(text).replaceAll("");
  }

  List<String> forbiddenSymbols(String content) {
    String visible = visibleText(content);
    List<String> found = new ArrayList<>();
    for (String symbol : List.of("//", "->", "——")) {
      if (visible.contains(symbol)) {
        found.add(symbol);
      }
    }
    return found;
  }

  String forbiddenLocation(String content) {
    String heading = "正文";
    boolean inFence = false;
    String[] lines = content.split("\\R", -1);
    for (int index = 0; index < lines.length; index++) {
      String line = lines[index];
      if (line.stripLeading().startsWith("```")) {
        inFence = !inFence;
        continue;
      }
      if (inFence) {
        continue;
      }
      Matcher headingMatch = Pattern.compile("^#{1,6}\\s+(.+)$").matcher(line.strip());
      if (headingMatch.matches()) {
        heading = headingMatch.group(1).strip();
      }
      if (!forbiddenSymbols(line).isEmpty()) {
        return heading + "（第 " + (index + 1) + " 行）";
      }
    }
    return null;
  }

  private boolean hasSummaryBlock(String content) {
    String firstLines = String.join("\n", content.strip().split("\\R", -1));
    String[] parts = firstLines.split("\\R", -1);
    StringBuilder sb = new StringBuilder();
    for (int index = 0; index < Math.min(10, parts.length); index++) {
      if (index > 0) {
        sb.append('\n');
      }
      sb.append(parts[index]);
    }
    String head = sb.toString();
    return Pattern.compile("^>\\s*系列[:：]", Pattern.MULTILINE).matcher(head).find()
        && head.contains("本课覆盖")
        && (head.contains("摘要") || head.contains("学完"));
  }

  private int imageCount(String content) {
    String normalized = OBSIDIAN_IMAGE.matcher(content).replaceAll("![$1](asset)");
    return (int) MARKDOWN_IMAGE.matcher(normalized).results().count();
  }

  private int syncedImageCount(AuditContext context) {
    java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
    for (Map<String, Object> asset : context.imageAssets()) {
      if (asset.get("path") != null) {
        paths.add(String.valueOf(asset.get("path")));
      }
    }
    if (paths.isEmpty()) {
      paths.addAll(context.imagePublicPaths());
    }
    for (String content : List.of(context.draft(), context.article())) {
      String normalized = OBSIDIAN_IMAGE.matcher(content).replaceAll("![$1](asset)");
      Matcher matcher = MARKDOWN_IMAGE.matcher(normalized);
      while (matcher.find()) {
        paths.add(matcher.group(2));
      }
    }
    return paths.size();
  }

  private static int indexOfAny(String text, String... values) {
    int best = -1;
    for (String value : values) {
      int idx = text.indexOf(value);
      if (idx >= 0 && (best < 0 || idx < best)) {
        best = idx;
      }
    }
    return best;
  }

  private static boolean has(Map<String, Object> asset, String key) {
    Object value = asset.get(key);
    return value != null && !String.valueOf(value).isBlank();
  }

  /** 将审核结果转为 API 响应结构。 */
  public Map<String, Object> toApiResult(AuditResult result) {
    List<Map<String, Object>> issues = result.items().stream()
        .filter(item -> !"pass".equals(item.status()))
        .map(item -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("code", item.code());
          m.put("title", item.title());
          m.put("severity", item.status());
          m.put("message", item.detail());
          m.put("blocking", item.blocking());
          m.put("fixableByTextAi", item.fixableByTextAi());
          m.put("location", item.location());
          m.put("suggestion", item.suggestion());
          return m;
        })
        .toList();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("score", result.score());
    out.put("blocking", result.failed() > 0);
    out.put("passed", result.passed());
    out.put("warnings", result.warnings());
    out.put("failed", result.failed());
    out.put("issues", issues);
    out.put("items", result.items().stream().map(item -> {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("code", item.code());
      m.put("title", item.title());
      m.put("detail", item.detail());
      m.put("status", item.status());
      m.put("fixableByTextAi", item.fixableByTextAi());
      m.put("location", item.location());
      m.put("suggestion", item.suggestion());
      m.put("blocking", item.blocking());
      return m;
    }).toList());
    return out;
  }
}
