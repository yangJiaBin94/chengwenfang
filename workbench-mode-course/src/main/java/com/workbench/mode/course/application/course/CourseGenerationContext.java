package com.workbench.mode.course.application.course;

import cn.hutool.core.util.StrUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 单次正式课程生成的已解析上下文：标题、系列路线、字数/配图档位、前后课信息与文章壳 ID。
 * <p>由 {@link CourseGenerationContextBuilder} / {@link #fromMaps} 组装，供编排器与校验器只读使用。
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public final class CourseGenerationContext {
  /** 课程主键。 */
  private final String courseId;
  /** 课程标题。 */
  private final String title;
  /** 课程摘要。 */
  private final String summary;
  /** 系列主键。 */
  private final String seriesId;
  /** 系列名称。 */
  private final String seriesName;
  /** 课次编号。 */
  private final Integer lessonNo;
  /** 难度标签。 */
  private final String difficulty;
  /** 字数档位。 */
  private final String wordCountOption;
  /** 配图档位。 */
  private final String imageCountOption;
  /** 正文上限字符数。 */
  private final int maxBodyChars;
  /** 最少插图数。 */
  private final int minImages;
  /** 最多插图数。 */
  private final int maxImages;
  /** 上一课标题。 */
  private final String previousTitle;
  /** 上一课摘要。 */
  private final String previousSummary;
  /** 下一课标题。 */
  private final String followingTitle;
  /** 下一课摘要。 */
  private final String followingSummary;
  /** 系列路线行。 */
  private final List<Map<String, Object>> seriesRoute;
  /** 草稿文章 id。 */
  private final String draftArticleId;
  /** 公众号文章 id。 */
  private final String wechatArticleId;

  /**
   * 将系列路线渲染为 Markdown 列表；当前课目前缀为 {@code *}，其余为 {@code -}。
   *
   * @return 多行 Markdown；无路线时返回占位文案
   */
  public String renderRoute() {
    // 空路线给模型一个明确「无」信号，避免臆造课次
    if (seriesRoute == null || seriesRoute.isEmpty()) {
      return "- （无系列路线）";
    }
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> row : seriesRoute) {
      // 用课程 id 标记当前课，便于提示词里对齐位置
      boolean current = courseId.equals(String.valueOf(row.get("id")));
      sb.append(current ? "* " : "- ");
      sb.append("第 ").append(row.getOrDefault("lessonNo", "?")).append(" 课 ");
      sb.append(row.getOrDefault("title", ""));
      Object sum = row.get("summary");
      if (sum != null && !String.valueOf(sum).isBlank()) {
        sb.append(" — ").append(sum);
      }
      sb.append('\n');
    }
    return sb.toString().strip();
  }

  /**
   * 按已写正文收缩本节上限。比例来自原版：开场 12–16%、原理 32–38%、案例 34–40%、小结 8–11%、预告 3–5%。
   *
   * @param sectionKey     段落 typeKey
   * @param usedBodyChars  已完成章节的正文字数
   * @return [本节下限, 写入提示的动态上限]
   */
  public int[] sectionBudget(String sectionKey, int usedBodyChars) {
    int minWords = CourseMarkdownUtil.minBodyChars(wordCountOption);
    int target = Math.max(1400, Math.round((minWords + maxBodyChars) / 2f));
    int[] ratio = switch (sectionKey) {
      case "draft_opening" -> new int[]{12, 16};
      case "draft_core" -> new int[]{32, 38};
      case "draft_example" -> new int[]{34, 40};
      case "draft_summary" -> new int[]{8, 11};
      case "draft_preview" -> new int[]{3, 5};
      default -> new int[]{10, 15};
    };
    int sectionMin = Math.max(1, Math.round(target * ratio[0] / 100f));
    int sectionMax = Math.max(sectionMin, Math.round(target * ratio[1] / 100f));
    int dynamicMax = Math.max(
        sectionMin,
        maxBodyChars - Math.max(0, usedBodyChars) - remainingSectionMins(sectionKey, target) - remainingReserve());
    return new int[]{sectionMin, Math.min(sectionMax, dynamicMax)};
  }

  /**
   * 本节硬上限元数据（对齐 Python {@code section_hard_max}）。
   * 原版只写入节点 input，不据此拒收分段。
   */
  public int sectionHardMax(String sectionKey, int usedBodyChars) {
    int minWords = CourseMarkdownUtil.minBodyChars(wordCountOption);
    int target = Math.max(1400, Math.round((minWords + maxBodyChars) / 2f));
    int[] ratio = switch (sectionKey) {
      case "draft_opening" -> new int[]{12, 16};
      case "draft_core" -> new int[]{32, 38};
      case "draft_example" -> new int[]{34, 40};
      case "draft_summary" -> new int[]{8, 11};
      case "draft_preview" -> new int[]{3, 5};
      default -> new int[]{10, 15};
    };
    int sectionMin = Math.max(1, Math.round(target * ratio[0] / 100f));
    int dynamicMax = Math.max(
        sectionMin,
        maxBodyChars - Math.max(0, usedBodyChars) - remainingSectionMins(sectionKey, target) - remainingReserve());
    return dynamicMax + Math.max(24, Math.round(dynamicMax * 0.01f));
  }

  /** 确定性课程边界，写入分段与压缩提示，不另调模型。 */
  public String renderPlan() {
    String goal = summary == null || summary.isBlank() ? "理解" + title + "的核心概念" : summary;
    String mustCover = bullet(title) + (summary == null || summary.isBlank() || summary.equals(title) ? "" : "\n" + bullet(summary));
    if (mustCover.isBlank()) {
      mustCover = "- 无";
    }
    String mustNot = followingTitle == null || followingTitle.isBlank() ? "- 无" : bullet(followingTitle);
    String previous = previousTitle == null || previousTitle.isBlank() ? "无" : "承接上一课《" + previousTitle + "》";
    String next = followingTitle == null || followingTitle.isBlank() ? "无" : "为下一课《" + followingTitle + "》做准备";
    return """
        课程目标：%s
        必须覆盖：
        %s
        禁止提前展开：
        %s
        必须使用的公式：
        - 无
        必须使用的案例：
        - 无
        上一课衔接：%s
        下一课衔接：%s
        建议章节：
        - 开场与目标
        - 核心概念
        - 具体例子
        - 本课小结
        - 下节课预告
        """.formatted(goal, mustCover.strip(), mustNot, previous, next).strip();
  }

  /** 有下一课却写不出禁止展开项时，在调用模型前失败。 */
  public void requirePlanBoundary() {
    if (followingTitle != null && !followingTitle.isBlank() && !renderPlan().contains(followingTitle)) {
      throw new IllegalStateException("COURSE_PLAN_BOUNDARY_MISSING: 课程生成计划缺少下一课边界");
    }
  }

  /** 上一课或下一课的提示行；没有邻居时标明系列首课或末课。 */
  public String neighborLine(boolean previous) {
    if (previous) {
      if (previousTitle == null || previousTitle.isBlank()) {
        return "无，本课是当前系列第一课";
      }
      return previousTitle + "：" + (previousSummary == null ? "" : previousSummary);
    }
    if (followingTitle == null || followingTitle.isBlank()) {
      return "无，本课是当前系列最后一课";
    }
    return followingTitle + "：" + (followingSummary == null ? "" : followingSummary);
  }

  private int remainingReserve() {
    return Math.max(80, Math.round(maxBodyChars * 0.02f));
  }

  private int remainingSectionMins(String sectionKey, int target) {
    List<String> order = List.of(
        "draft_opening", "draft_core", "draft_example", "draft_summary", "draft_preview");
    boolean after = false;
    int sum = 0;
    for (String key : order) {
      if (after) {
        int[] ratio = switch (key) {
          case "draft_opening" -> new int[]{12};
          case "draft_core" -> new int[]{32};
          case "draft_example" -> new int[]{34};
          case "draft_summary" -> new int[]{8};
          case "draft_preview" -> new int[]{3};
          default -> new int[]{10};
        };
        sum += Math.max(1, Math.round(target * ratio[0] / 100f));
      }
      if (key.equals(sectionKey)) {
        after = true;
      }
    }
    return sum;
  }

  private static String bullet(String value) {
    return value == null || value.isBlank() ? "" : "- " + value;
  }

  /**
   * 按段落类型（opening/core/…）给出本节目标字数提示，写入分段生成 prompt。
   *
   * @param sectionKey 段落 typeKey，如 {@code draft_opening}、{@code draft_core}
   * @return 中文提示串，含本节约估字数与整课上限
   */
  public String sectionBudgetHint(String sectionKey) {
    return sectionBudgetHint(sectionKey, 0);
  }

  /**
   * 按已写字数给出本节目标字数提示。
   *
   * @param sectionKey    段落 typeKey
   * @param usedBodyChars 已完成章节正文字数
   * @return 中文提示串
   */
  public String sectionBudgetHint(String sectionKey, int usedBodyChars) {
    int[] budget = sectionBudget(sectionKey, usedBodyChars);
    return "本节目标字数约 " + budget[0] + "～" + budget[1] + "（整课正文上限 " + maxBodyChars + "）";
  }

  /**
   * 导出编排器用的精简摘要（字数档、配图上下限等），写入节点运行产物。
   *
   * @return 有序 Map，键稳定便于快照对比
   */
  public Map<String, Object> toSummaryMap() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("courseId", courseId);
    m.put("title", title);
    m.put("seriesName", seriesName);
    m.put("lessonNo", lessonNo);
    m.put("wordCountOption", wordCountOption);
    m.put("imageCountOption", imageCountOption);
    m.put("maxBodyChars", maxBodyChars);
    m.put("imageBounds", List.of(minImages, maxImages));
    return m;
  }

  /**
   * 从课程/设置/壳数据与前后课信息组装上下文。
   *
   * @param course      课程主数据 Map（含 id/title/summary/series 等）
   * @param settings    生成设置（wordCountOption、imageCountOption）
   * @param shell       课程壳（draft/wechat 文章 id、title）
   * @param seriesRoute 系列课次路线列表
   * @param previous    上一课邻居；可为 null
   * @param following   下一课邻居；可为 null
   * @return 不可变语义的上下文
   */
  public static CourseGenerationContext fromMaps(
      Map<String, Object> course,
      Map<String, Object> settings,
      Map<String, Object> shell,
      List<Map<String, Object>> seriesRoute,
      Map<String, Object> previous,
      Map<String, Object> following) {
    // Job 输入未覆盖时使用默认字数/配图档位
    String word = firstNonNullLiteral(
        string(settings.get("wordCountOption")),
        "2000-4000");
    String images = firstNonNullLiteral(
        string(settings.get("imageCountOption")),
        "3-5");
    // 档位字符串解析为正文上限与插图区间
    int[] bounds = CourseMarkdownUtil.imageBounds(images);
    Object meta = course.get("metadata");
    String difficulty = "零基础";
    if (meta instanceof Map<?, ?> map && map.get("difficulty") != null) {
      difficulty = String.valueOf(map.get("difficulty"));
    }
    return new CourseGenerationContext(
        string(course.get("id")),
        firstNonNullLiteral(string(course.get("title")), string(shell.get("title")), string(course.get("id"))),
        scrubNullLiteral(string(course.get("summary"))),
        string(course.get("seriesId")),
        firstNonNullLiteral(string(course.get("series")), "未命名系列"),
        course.get("lessonNo") instanceof Number number ? number.intValue() : null,
        difficulty,
        word,
        images,
        CourseMarkdownUtil.maxBodyChars(word),
        bounds[0],
        bounds[1],
        previous == null ? "" : scrubNullLiteral(string(previous.get("title"))),
        previous == null ? "" : scrubNullLiteral(string(previous.get("summary"))),
        following == null ? "" : scrubNullLiteral(string(following.get("title"))),
        following == null ? "" : scrubNullLiteral(string(following.get("summary"))),
        seriesRoute == null ? List.of() : List.copyOf(seriesRoute),
        string(shell.get("draftArticleId")),
        string(shell.get("wechatArticleId")));
  }

  /**
   * 将任意值转为字符串；null 保持 null。
   *
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String string(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  /**
   * 将 null / 字面量 {@code "null"} 规范为空串，便于提示词拼接。
   *
   * @param value 可能为空的字符串
   * @return 非 null 字符串
   */
  private static String scrubNullLiteral(String value) {
    String normalized = StrUtil.nullToEmpty(value);
    return "null".equals(normalized) ? "" : normalized;
  }

  /**
   * 返回第一个非空且非字面量 {@code "null"} 的候选。
   *
   * @param values 候选值，按优先级排列
   * @return 首个有效值；皆无效时返回空串
   */
  private static String firstNonNullLiteral(String... values) {
    for (String candidate : values) {
      if (StrUtil.isNotBlank(candidate) && !"null".equals(candidate)) {
        return candidate;
      }
    }
    return "";
  }
}
