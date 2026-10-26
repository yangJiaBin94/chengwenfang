package com.workbench.mode.course.application.course;

import com.workbench.ai.facade.AiImageFacade;
import com.workbench.ai.facade.dto.request.ImageGenerateRequest;
import com.workbench.common.IdsUtil;
import com.workbench.content.facade.ContentFacade;
import com.workbench.content.facade.dto.request.PersistGeneratedImageRequest;
import com.workbench.content.facade.dto.view.GeneratedImageView;
import com.workbench.infra.support.StorageService;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 课程配图：调用图片模型或写入截图占位，并落库。
 */
public class CourseImageGenerator {
  /**
   * 基础画质约束（不锁死构图与主色）。
   * <p>同课多图的差异化由 {@link #diversityBrief} 按序号强制轮换。
   */
  private static final String IMAGE_QUALITY_BASELINE =
      "插画级清晰度，主体完整不裁切，无水印，不出现真实人物，"
          + "文字与关键主体位于画面安全区内并留白；一张图只表达一个主要结论，不超过三个信息区。";

  /** 同课多图轮换构图，避免左右对比+科技脑图千篇一律。 */
  private static final String[] LAYOUT_VARIANTS = {
      "左右对比分屏，左右信息区形态、密度与主色必须明显不同",
      "自上而下三步流程，步骤卡片形状与间距有变化，禁止对称三栏复刻",
      "单一中心隐喻特写，周围最多两个注释标签，留大量负空间",
      "斜向等轴测场景，物体沿对角线排布，禁止正面对称 HUD",
      "问题→机制→结果的环形阅读路径，中心放一个具体物件隐喻",
      "时间线或因果链横条，节点用不同几何形体区分"
  };

  /** 同课多图轮换主色，打破整课深蓝霓虹。 */
  private static final String[] PALETTE_VARIANTS = {
      "冷青与石墨灰为主，少量暖橙点缀",
      "暖琥珀与米白纸感背景，深棕文字区",
      "薄荷绿与雾蓝，轻量扁平+微立体混合",
      "暮紫与金色高光，暗背景但信息区干净",
      "高对比黑白加单一强调色（红或青绿）",
      "浅色日间工作台风格，柔和阴影，少霓虹"
  };

  /** 中心隐喻提示（按序号轮换，模型仍需贴合相邻正文语义）。 */
  private static final String[] METAPHOR_HINTS = {
      "用「开卷考试/书架」类生活隐喻",
      "用「漏斗过滤」表达筛选与检索",
      "用「地图路标」表达定位与导航",
      "用「拼图缺角」表达补全信息",
      "用「桥梁」表达外部资料接通模型",
      "用「放大镜对准一页纸」表达精准检索"
  };

  /** 截图占位图 URL。 */
  private static final String SCREENSHOT_PLACEHOLDER_URL = "/screenshot-placeholder.svg";

  /** 技能文案里强制统一 Keynote 风格的句子，内文图生成时剔除。 */
  private static final Pattern SKILL_UNIFORM_STYLE = Pattern.compile(
      "(?m)^.*(?:深海蓝|Apple Keynote|SaaS 仪表盘|科技网格|环形轨道|霓虹边缘光).*$\\n?");

  /** 图像 AI。 */
  private final AiImageFacade images;
  /** 内容门面。 */
  private final ContentFacade content;
  /** 本地存储。 */
  private final StorageService storage;
  /** Skill 门面。 */
  private final SkillFacade skills;

  /**
   * @param images  图像 AI
   * @param content 内容门面
   * @param storage 本地存储
   * @param skills  Skill 门面
   */
  public CourseImageGenerator(
      AiImageFacade images,
      ContentFacade content,
      StorageService storage,
      SkillFacade skills) {
    this.images = images;
    this.content = content;
    this.storage = storage;
    this.skills = skills;
  }

  /**
   * 按计划项生成一张图（截图占位或模型生图）。
   *
   * @param ctx          课程上下文
   * @param item         计划项
   * @param snapshot     工作流快照
   * @param jobId        任务 ID
   * @param workingDraft 当前原稿（取相邻正文）
   * @return 图片元数据行
   */
  Map<String, Object> generateOne(
      CourseGenerationContext ctx,
      CourseImagePipeline.ImagePlanItem item,
      Map<String, Object> snapshot,
      String jobId,
      String workingDraft) throws Exception {
    if ("screenshot_placeholder".equals(item.productionMode())) {
      return screenshotPlaceholderRow(ctx, item, jobId);
    }
    Map<String, Object> node = findNode(
        snapshot, "cover".equals(item.kind()) ? "image_cover" : "image_inline");
    String skillPrompt = skills.composePromptFromBindings(skillBindings(node));
    String adjacent = CourseImagePromptExtractor.imageArticleContext(workingDraft, item.prompt());
    String grounded = imageGenerationPrompt(ctx, item, skillPrompt, adjacent);
    var aiResult = images.generate(new ImageGenerateRequest(grounded, item.size(), false));
    String b64 = aiResult.getB64Json();
    if (b64 == null || b64.isBlank()) {
      throw new IllegalStateException("配图生成返回空 b64，kind=" + item.kind());
    }
    byte[] png = Base64.getDecoder().decode(b64);
    String imageId = IdsUtil.newId();
    Path file = storage.generatedImagesDir().resolve(imageId + ".png");
    Files.write(file, png);
    // 课程轨 documentId 与 wechat 文章 id 相同
    PersistGeneratedImageRequest fields = PersistGeneratedImageRequest.builder()
        .id(imageId)
        .articleId(ctx.wechatArticleId())
        .documentId(ctx.wechatArticleId())
        .kind(item.kind())
        .sequenceNo(item.sequenceNo())
        .prompt(grounded)
        .model(aiResult.getModel())
        .size(item.size())
        .outputFormat("png")
        .filePath("generated-images/" + imageId + ".png")
        .contentHash(sha256(png))
        .width(parseSize(item.size(), true))
        .height(parseSize(item.size(), false))
        .jobId(jobId)
        .build();
    GeneratedImageView saved = content.persistGeneratedImage(fields);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", saved.getId());
    row.put("articleId", saved.getArticleId());
    row.put("kind", item.kind());
    row.put("sequenceNo", item.sequenceNo());
    row.put("token", item.token());
    row.put("publicUrl", saved.getPublicUrl() == null ? "/api/images/" + imageId : saved.getPublicUrl());
    row.put("contentHash", saved.getContentHash());
    row.put("width", saved.getWidth());
    row.put("height", saved.getHeight());
    row.put("size", item.size());
    row.put("inlineNo", item.inlineNo());
    return row;
  }

  /**
   * 截图占位行。
   *
   * @param ctx   上下文
   * @param item  计划项
   * @param jobId 任务 ID
   * @return 元数据行
   */
  private Map<String, Object> screenshotPlaceholderRow(
      CourseGenerationContext ctx, CourseImagePipeline.ImagePlanItem item, String jobId) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", "screenshot-placeholder-" + ctx.courseId() + "-" + item.sequenceNo());
    row.put("articleId", ctx.wechatArticleId());
    row.put("kind", item.kind());
    row.put("sequenceNo", item.sequenceNo());
    row.put("token", item.token());
    row.put("publicUrl", SCREENSHOT_PLACEHOLDER_URL);
    row.put("contentHash", sha256(SCREENSHOT_PLACEHOLDER_URL.getBytes()));
    row.put("width", 2048);
    row.put("height", 1152);
    row.put("size", item.size());
    row.put("inlineNo", item.inlineNo());
    row.put("jobId", jobId);
    return row;
  }

  /**
   * 组装最终图像生成提示词。
   *
   * @param ctx         上下文
   * @param item        计划项
   * @param skillPrompt 技能提示
   * @param adjacent    相邻正文
   * @return 提示词
   */
  private String imageGenerationPrompt(
      CourseGenerationContext ctx,
      CourseImagePipeline.ImagePlanItem item,
      String skillPrompt,
      String adjacent) {
    boolean isCover = "cover".equals(item.kind());
    String marker = (item.marker() == null ? "" : item.marker()).replaceAll("[*#]", "").strip();
    String subject = marker.replaceFirst("^插图位置[:：]?", "").strip();
    if (subject.isBlank() || subject.matches("核心概念\\s*\\d+")) {
      subject = isCover
          ? ctx.title()
          : (adjacent == null || adjacent.isBlank()
              ? "本段核心概念"
              : adjacent.substring(0, Math.min(48, adjacent.length())).strip());
    }
    String contextLine = adjacent == null || adjacent.isBlank()
        ? (ctx.summary().isBlank() ? ctx.title() : ctx.summary())
        : adjacent;
    StringBuilder sb = new StringBuilder();
    // 差异化指令放最前：图像模型对开头约束更敏感
    sb.append(diversityBrief(item, isCover)).append("\n\n");
    String trimmedSkill = trimSkillPrompt(skillPrompt, isCover);
    if (!trimmedSkill.isBlank()) {
      sb.append(trimmedSkill).append("\n\n");
    }
    if (isCover) {
      sb.append("课程主题（封面可作标题）：").append(ctx.title()).append('\n');
    } else {
      sb.append("课程语境（禁止把课名画进画面）：本课概念图\n");
    }
    sb.append("插图位置与用途：").append(subject).append('\n');
    sb.append("插图相邻正文：").append(contextLine).append("\n\n");
    sb.append("原始视觉要求：").append(sanitizeInlinePrompt(item.prompt(), ctx.title(), isCover)).append("\n\n");
    if (isCover) {
      sb.append("这是封面图：标题文字必须完整位于中央 1:1 转发卡片安全区内。");
      sb.append("画面主体应表达本课核心结论。\n\n");
    } else {
      sb.append("这是正文插图，不是封面。禁止绘制课程全名、课次、「Lesson」等长标题条幅；");
      sb.append("如需文字，只用与相邻正文直接相关的短标签（每个≤8字）。");
      sb.append("画面应让读者一眼看懂相邻段落的关键关系，避免装饰性科技脑图堆砌。\n\n");
    }
    sb.append("画质底线：").append(IMAGE_QUALITY_BASELINE);
    return sb.toString();
  }

  /**
   * 按序号强制轮换构图/色板/隐喻，压低同课雷同。
   *
   * @param item    计划项
   * @param isCover 是否封面
   * @return 差异化说明
   */
  static String diversityBrief(CourseImagePipeline.ImagePlanItem item, boolean isCover) {
    int index = Math.max(0, item.sequenceNo() - 1);
    String layout = LAYOUT_VARIANTS[index % LAYOUT_VARIANTS.length];
    String palette = PALETTE_VARIANTS[index % PALETTE_VARIANTS.length];
    String metaphor = METAPHOR_HINTS[index % METAPHOR_HINTS.length];
    if (isCover) {
      return "【封面差异化】允许品牌感科技插画，但中心隐喻必须独特；"
          + "构图：" + layout + "；主色：" + palette + "。"
          + "禁止做成可复用的通用 AI 课封面模板。";
    }
    return "【第 " + item.sequenceNo() + " 张正文图 · 强制差异化】"
        + "构图必须是：" + layout + "。"
        + "主色倾向：" + palette + "。"
        + "隐喻参考（需贴合相邻正文，可替换为更贴切物件）：" + metaphor + "。"
        + "严禁与同课其他图共用几乎相同的布局、主色和中心符号；"
        + "严禁默认套用深海蓝 Keynote / 玻璃霓虹 / 对称三栏科技脑图。";
  }

  /**
   * 内文图去掉技能里「全课统一 Keynote」类句子，避免覆盖差异化指令。
   *
   * @param skillPrompt 技能提示
   * @param isCover     是否封面
   * @return 裁剪后提示
   */
  static String trimSkillPrompt(String skillPrompt, boolean isCover) {
    if (skillPrompt == null || skillPrompt.isBlank()) {
      return "";
    }
    if (isCover) {
      return skillPrompt.strip();
    }
    String cleaned = SKILL_UNIFORM_STYLE.matcher(skillPrompt).replaceAll("");
    cleaned = cleaned.replaceAll("(?m)^\\s*\\d+\\.\\s*$\\n?", "");
    cleaned = cleaned.replaceAll("\n{3,}", "\n\n").strip();
    // 技能过长会淹没差异化指令，内文只保留前 900 字规划要点
    if (cleaned.length() > 900) {
      cleaned = cleaned.substring(0, 900).strip() + "…";
    }
    return cleaned;
  }

  /**
   * 去掉内文提示词里要求画上课名/统一科技风的句子。
   *
   * @param prompt      原始提示
   * @param courseTitle 课名
   * @param isCover     是否封面
   * @return 清洗后提示
   */
  static String sanitizeInlinePrompt(String prompt, String courseTitle, boolean isCover) {
    if (prompt == null) {
      return "";
    }
    String text = prompt.strip();
    if (isCover) {
      return text;
    }
    if (courseTitle != null && !courseTitle.isBlank()) {
      text = text.replace(courseTitle, "该概念");
    }
    text = text.replaceAll("深海蓝[^，。；\n]{0,40}", "");
    text = text.replaceAll("Apple Keynote[^，。；\n]{0,20}", "");
    text = text.replaceAll("(?i)课程《[^》]+》", "");
    return text.replaceAll("\\s{2,}", " ").strip();
  }

  /**
   * 按 typeKey 查找快照节点。
   *
   * @param snapshot 快照
   * @param typeKey  节点键
   * @return 节点；未找到为 null
   */
  private static Map<String, Object> findNode(Map<String, Object> snapshot, String typeKey) {
    Object nodesRaw = snapshot == null ? null : snapshot.get("nodes");
    if (!(nodesRaw instanceof List<?> nodes)) {
      return null;
    }
    for (Object raw : nodes) {
      if (!(raw instanceof Map<?, ?> map)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> node = (Map<String, Object>) map;
      if (typeKey.equals(String.valueOf(node.get("typeKey")))
          || typeKey.equals(String.valueOf(node.get("key")))) {
        return node;
      }
    }
    return null;
  }

  /**
   * 解析节点 skillBindings。
   *
   * @param node 节点
   * @return 绑定列表
   */
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

  /**
   * 解析尺寸字符串。
   *
   * @param size  如 2048x1152
   * @param width true 取宽
   * @return 像素值
   */
  private static int parseSize(String size, boolean width) {
    if (size == null || !size.contains("x")) {
      return 0;
    }
    String[] parts = size.toLowerCase().split("x");
    try {
      return Integer.parseInt(parts[width ? 0 : 1].trim());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  /**
   * SHA-256 十六进制摘要。
   *
   * @param bytes 字节
   * @return hex；失败为空串
   */
  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception ignored) {
      return "";
    }
  }
}
