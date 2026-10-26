package com.workbench.quality.domain.engine;

import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.quality.facade.enums.ReviewStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Component;

/** 热点文章审核引擎：规则校验与 AI 校验原语。 */
@Component
public class HotArticleReviewEngine {
  private static final Pattern H1 = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
  private static final Pattern H2 = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");
  private static final Pattern MARKDOWN_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)");
  private static final Pattern BROKEN_LINK = Pattern.compile("\\[[^]]*\\]\\([^)]*$", Pattern.MULTILINE);

  /**
   * 审核条目定义。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class ReviewItemDef {
    /** 条目编码。 */
    private final String code;
    /** 展示名称。 */
    private final String name;
    /** 分类。 */
    private final String category;
    /** 引擎类型（rule/ai）。 */
    private final String engine;
    /** 严重级别。 */
    private final String severity;
    /** 是否阻断。 */
    private final boolean blocking;
    /** 是否可修复。 */
    private final boolean repairable;
    /** 是否需要人工确认。 */
    private final boolean requiresHumanConfirmation;
  }

  private static final List<ReviewItemDef> CATALOG = List.of(
      def("ARTICLE_TITLE", "文章主标题", "文章结构", "rule", "error", true, true, false),
      def("ARTICLE_SECTIONS", "小标题有效", "文章结构", "rule", "error", true, true, false),
      def("ARTICLE_STRUCTURE", "段落结构完整", "文章结构", "rule", "error", true, false, false),
      def("ARTICLE_MIN_LENGTH", "文章长度", "文章结构", "rule", "error", true, true, false),
      def("ARTICLE_MARKDOWN_VALID", "Markdown 结构有效", "文章结构", "rule", "error", true, true, false),
      def("NO_EMPTY_BLOCK", "段落内容完整", "文章结构", "rule", "error", true, true, false),
      def("SOURCE_PRESENT", "选题来源完整", "事实与来源", "rule", "error", true, false, false),
      def("SOURCE_METADATA", "来源信息有效", "事实与来源", "rule", "error", true, true, false),
      def("SOURCE_DUPLICATE", "来源不重复", "事实与来源", "rule", "warning", false, true, false),
      def("NO_INTERNAL_LEAK", "内部信息不泄漏", "公开内容", "rule", "error", true, true, false),
      def("IMAGE_REFERENCE", "图片引用有效", "资源完整性", "rule", "error", true, true, false),
      def("LINK_REFERENCE_VALID", "正文链接有效", "资源完整性", "rule", "error", true, true, false),
      def("REVIEW_SNAPSHOT_COMPLETE", "审核快照完整", "版本完整性", "rule", "error", true, false, false),
      def("FACT_SUPPORT", "事实有来源依据", "事实与来源", "ai", "error", true, true, true));

  private static final Map<String, ReviewItemDef> BY_CODE = CATALOG.stream()
      .collect(Collectors.toMap(ReviewItemDef::code, def -> def, (existing, replacement) -> existing, LinkedHashMap::new));

  private static ReviewItemDef def(
      String code, String name, String category, String engine, String severity,
      boolean blocking, boolean repairable, boolean human) {
    return new ReviewItemDef(code, name, category, engine, severity, blocking, repairable, human);
  }

  /**
   * 审核项目录（规则 + AI）。
   *
   * @return 不可变目录
   */
  public List<ReviewItemDef> catalog() {
    return CATALOG;
  }

  /**
   * 按规则码取目录定义。
   *
   * @param code 规则码
   * @return 定义；未知码为 null
   */
  public ReviewItemDef definition(String code) {
    return code == null ? null : BY_CODE.get(code);
  }

  /** 解析热点文章 Markdown 结构（标题与小节）。 */
  public Map<String, Object> parseMarkdown(String content) {
    String text = content == null ? "" : content;
    List<Map<String, Object>> sections = new ArrayList<>();
    Matcher matcher = H2.matcher(text);
    List<int[]> spans = new ArrayList<>();
    List<String> headings = new ArrayList<>();
    while (matcher.find()) {
      spans.add(new int[] {matcher.start(), matcher.end()});
      headings.add(matcher.group(1).trim());
    }
    for (int index = 0; index < spans.size(); index++) {
      int contentStart = spans.get(index)[1];
      int contentEnd = index + 1 < spans.size() ? spans.get(index + 1)[0] : text.length();
      Map<String, Object> section = new LinkedHashMap<>();
      section.put("heading", headings.get(index));
      section.put("start", spans.get(index)[0]);
      section.put("end", contentEnd);
      section.put("content", text.substring(contentStart, contentEnd).strip());
      sections.add(section);
    }
    long titleCount = H1.matcher(text).results().count();
    Map<String, Object> parsed = new LinkedHashMap<>();
    parsed.put("titleCount", titleCount);
    parsed.put("sections", sections);
    return parsed;
  }

  /** 执行确定性规则审核，返回条目列表。 */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> runRuleReview(Map<String, Object> snapshot) {
    String content = String.valueOf(snapshot.getOrDefault("content", ""));
    Map<String, Object> parsed = parseMarkdown(content);
    List<Map<String, Object>> sections = (List<Map<String, Object>>) parsed.get("sections");
    List<Map<String, Object>> results = new ArrayList<>();

    List<String> titleMatches = H1.matcher(content).results().map(matchResult -> matchResult.group(1).trim()).toList();
    boolean titleCountOk = ((Number) parsed.get("titleCount")).longValue() == 1;
    boolean titleValid = titleMatches.size() == 1
        && titleMatches.get(0).indexOf("审核") < 0
        && titleMatches.get(0).indexOf("提示词") < 0
        && titleMatches.get(0).indexOf("素材池") < 0;
    add(results, "ARTICLE_TITLE", titleCountOk && titleValid,
        titleCountOk ? "一级标题内容有效。" : "必须存在且只能存在一个一级标题。");

    List<String> names = sections.stream().map(section -> String.valueOf(section.get("heading"))).toList();
    boolean sectionsUnique = names.size() == Set.copyOf(names).size();
    boolean sectionsNamed = names.stream().allMatch(name -> !name.isBlank());
    boolean sectionsLength = names.stream().allMatch(name -> name.length() >= 1 && name.length() <= 32);
    add(results, "ARTICLE_SECTIONS", sectionsNamed && sectionsUnique && sectionsLength,
        "二级标题不能为空、不得重复，长度 1 到 32 个字符。");
    add(results, "ARTICLE_STRUCTURE", !names.isEmpty(), "文章已解析出可审核的段落。");
    add(results, "ARTICLE_MIN_LENGTH", content.strip().length() >= 900,
        "当前正文长度为 " + content.strip().length() + "。");
    add(results, "NO_EMPTY_BLOCK",
        sections.stream().allMatch(section -> String.valueOf(section.get("content")).strip().length() > 0),
        "未发现空段落。");
    int fenceCount = 0;
    for (int idx = 0; (idx = content.indexOf("```", idx)) >= 0; idx += 3) {
      fenceCount++;
    }
    add(results, "ARTICLE_MARKDOWN_VALID", fenceCount % 2 == 0, "Markdown 代码块结构有效。");

    Object rawSources = snapshot.getOrDefault("sources", List.of());
    List<Map<String, Object>> sources = new ArrayList<>();
    if (rawSources instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          Map<String, Object> row = new LinkedHashMap<>();
          map.forEach((key, value) -> row.put(String.valueOf(key), value));
          sources.add(row);
        }
      }
    }
    add(results, "SOURCE_PRESENT", !sources.isEmpty(),
        sources.isEmpty() ? "选题研究快照中没有来源。" : "审核快照已绑定 " + sources.size() + " 条来源。");
    List<Map<String, Object>> publishSources = sources.stream()
        .filter(item -> item.get("sourceItemId") != null || "evidence".equals(item.get("role")))
        .toList();
    if (publishSources.isEmpty()) {
      publishSources = sources;
    }
    boolean namesOk = publishSources.stream()
        .allMatch(source -> hasText(source.get("name")) || hasText(source.get("sourceName")));
    boolean titlesOk = publishSources.stream()
        .allMatch(source -> hasText(source.get("title")) || hasText(source.get("sourceTitle")));
    List<String> urls = publishSources.stream()
        .map(source -> String.valueOf(Objects.requireNonNullElse(source.get("url"),
            Objects.requireNonNullElse(source.get("sourceUrl"), ""))))
        .toList();
    List<String> urlsWithValue = urls.stream().filter(url -> !url.isBlank()).toList();
    boolean urlsPresent = !sources.isEmpty() && urls.stream().noneMatch(String::isBlank);
    boolean urlsValid = urlsWithValue.stream()
        .allMatch(url -> url.startsWith("http://") || url.startsWith("https://"));
    add(results, "SOURCE_METADATA", sources.isEmpty() || (namesOk && titlesOk && urlsPresent && urlsValid),
        "来源名称、标题与可访问链接完整。");
    add(results, "SOURCE_DUPLICATE", urlsWithValue.size() == Set.copyOf(urlsWithValue).size(),
        "来源 URL 不重复。");

    String[] forbidden = {"素材池", "可信度评分", "审核阻断", "插图提示词", "推荐写作角度", "生成过程"};
    String leaked = null;
    for (String term : forbidden) {
      if (content.contains(term)) {
        leaked = term;
        break;
      }
    }
    boolean generationMeta = content.contains("提示词") || content.contains("修复过程")
        || content.contains("生成过程") || content.contains("审核过程") || content.contains("AI 审核结果");
    boolean imagePromptLeak = content.contains("图片提示词") || content.toLowerCase().contains("image prompt");
    add(results, "NO_INTERNAL_LEAK", leaked == null && !generationMeta && !imagePromptLeak,
        leaked != null ? "发现内部术语：" + leaked
            : (generationMeta || imagePromptLeak ? "发现生成元信息或图片提示词。" : "未发现内部术语、生成元信息或图片提示词。"));

    List<String> imageUrls = MARKDOWN_IMAGE.matcher(content).results()
        .map(matchResult -> matchResult.group(1).strip())
        .filter(url -> !url.isBlank())
        .toList();
    add(results, "IMAGE_REFERENCE", imageUrls.size() == Set.copyOf(imageUrls).size(),
        "图片 URL 不重复引用。");
    add(results, "LINK_REFERENCE_VALID", !BROKEN_LINK.matcher(content).find(), "正文链接格式有效。");
    add(results, "REVIEW_SNAPSHOT_COMPLETE",
        snapshot.containsKey("content") && snapshot.containsKey("contentHash")
            && snapshot.containsKey("sources") && snapshot.containsKey("standardVersion"),
        "审核快照字段完整。");

    Set<String> present = results.stream().map(result -> String.valueOf(result.get("code"))).collect(Collectors.toSet());
    for (ReviewItemDef definition : CATALOG) {
      if ("rule".equals(definition.engine()) && !present.contains(definition.code())) {
        add(results, definition.code(), true, "该固定规则在当前文章上未发现问题。");
      }
    }
    return results;
  }

  /** 校验 AI 审核结果条目格式与可修复性。 */
  public List<Map<String, Object>> validateAiResults(
      Object value, String content, List<Map<String, Object>> sources) {
    if (!(value instanceof Map<?, ?> map) || !(map.get("items") instanceof List<?> items)) {
      throw new BusinessException(ErrorCodes.HOT_REVIEW_AI_INVALID, "AI 审核结果不是合法结构化数据。", 502);
    }
    List<Map<String, Object>> output = new ArrayList<>();
    for (Object rawObj : items) {
      if (!(rawObj instanceof Map<?, ?> raw) || !BY_CODE.containsKey(String.valueOf(raw.get("code")))) {
        throw new BusinessException(ErrorCodes.HOT_REVIEW_AI_INVALID, "AI 返回了未知审核项。", 502);
      }
      ReviewItemDef definition = BY_CODE.get(String.valueOf(raw.get("code")));
      Object locationsObj = raw.get("locations") == null ? List.of() : raw.get("locations");
      Object evidenceObj = raw.get("evidence") == null ? List.of() : raw.get("evidence");
      if (!(locationsObj instanceof List<?> locations) || !(evidenceObj instanceof List<?> evidence)) {
        throw new BusinessException(ErrorCodes.HOT_REVIEW_AI_INVALID, "AI 审核定位或来源证据格式错误。", 502);
      }
      if (content != null) {
        for (Object locationObj : locations) {
          if (!(locationObj instanceof Map<?, ?> location)) {
            throw new BusinessException(ErrorCodes.HOT_REVIEW_AI_INVALID, "AI 返回了非法原文定位。", 502);
          }
          String quote = String.valueOf(Objects.requireNonNullElse(location.get("quote"), ""));
          Object startObj = location.get("start");
          Object endObj = location.get("end");
          if (quote.isBlank()
              || !content.contains(quote)
              || !(startObj instanceof Number start)
              || !(endObj instanceof Number end)
              || start.intValue() < 0
              || end.intValue() < start.intValue()
              || !content.substring(start.intValue(), end.intValue()).equals(quote)) {
            throw new BusinessException(ErrorCodes.HOT_REVIEW_AI_INVALID, "AI 返回的原文定位与当前文章不一致。", 502);
          }
        }
      }
      if (sources != null) {
        Set<String> sourceUrls = sources.stream()
            .map(item -> String.valueOf(Objects.requireNonNullElse(item.get("url"),
                Objects.requireNonNullElse(item.get("sourceUrl"), ""))))
            .collect(Collectors.toSet());
        for (Object evidenceItemObj : evidence) {
          if (!(evidenceItemObj instanceof Map<?, ?> evidenceItem)
              || !sourceUrls.contains(String.valueOf(Objects.requireNonNullElse(evidenceItem.get("sourceUrl"), "")))) {
            throw new BusinessException(ErrorCodes.HOT_REVIEW_AI_INVALID, "AI 返回了不在审核快照中的来源。", 502);
          }
        }
      }
      Map<String, Object> item = new LinkedHashMap<>();
      raw.forEach((key, entryValue) -> item.put(String.valueOf(key), entryValue));
      item.put("engine", "ai");
      item.put("category", definition.category());
      item.put("severity", definition.severity());
      item.put("blocking", definition.blocking());
      item.put("repairable", definition.repairable());
      item.put("requiresHumanConfirmation", definition.requiresHumanConfirmation());
      output.add(item);
    }
    return output;
  }

  /** 判断当前审核状态是否允许人工通过。 */
  public boolean canApprove(String reviewStatus) {
    return ReviewStatus.from(reviewStatus) != ReviewStatus.FAILED;
  }

  /** 不允许通过时抛出业务异常。 */
  public void assertCanApprove(String reviewStatus) {
    if (!canApprove(reviewStatus)) {
      throw new BusinessException(ErrorCodes.HOT_ARTICLE_REVIEW_BLOCKED, "未通过的审核不能批准", 409);
    }
  }

  private void add(List<Map<String, Object>> results, String code, boolean passed, String detail) {
    ReviewItemDef definition = BY_CODE.get(code);
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("code", code);
    item.put("engine", "rule");
    item.put("category", definition.category());
    item.put("status", passed ? ReviewStatus.PASSED.getValue() : ReviewStatus.FAILED.getValue());
    item.put("severity", definition.severity());
    item.put("blocking", definition.blocking());
    item.put("title", definition.name());
    item.put("detail", detail);
    item.put("locations", List.of());
    item.put("evidence", List.of());
    item.put("repairable", definition.repairable());
    item.put("requiresHumanConfirmation", definition.requiresHumanConfirmation());
    results.add(item);
  }

  private static boolean hasText(Object value) {
    return value != null && !String.valueOf(value).isBlank();
  }
}
