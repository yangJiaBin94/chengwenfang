package com.chengwenfang.mode.course.application.course;

import com.chengwenfang.ai.facade.AiImageFacade;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.infra.support.StorageService;
import com.chengwenfang.skill.facade.SkillFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 正式课程配图门面：计划、生成封面/内文图并回写占位符（无 fixture）。
 */
public class CourseImagePipeline {
  /** 图片生成协作。 */
  private final CourseImageGenerator generator;

  /**
   * @param images  图像 AI
   * @param content 内容门面
   * @param storage 本地存储
   * @param skills  Skill 门面
   */
  public CourseImagePipeline(
      AiImageFacade images,
      ContentFacade content,
      StorageService storage,
      SkillFacade skills) {
    this.generator = new CourseImageGenerator(images, content, storage, skills);
  }

  /**
   * 生成封面与内文图：截图类写入占位，概念图调模型；原稿保留提示词并追加图片引用。
   *
   * @param ctx      课程生成上下文
   * @param draft    原稿
   * @param wechat   公众号正文
   * @param snapshot 工作流快照
   * @param jobId    Job ID
   * @param progress 进度回调；可为 null
   * @return 更新后的正文与配图结果
   */
  public Result generate(
      CourseGenerationContext ctx,
      String draft,
      String wechat,
      Map<String, Object> snapshot,
      String jobId,
      ProgressSink progress) throws Exception {
    return generate(ctx, draft, wechat, snapshot, jobId, progress, null);
  }

  /**
   * 按 kind 过滤生成配图（{@code cover} / {@code inline}）；{@code null} 表示全部。
   *
   * @param ctx        课程生成上下文
   * @param draft      原稿
   * @param wechat     公众号正文
   * @param snapshot   工作流快照
   * @param jobId      Job ID
   * @param progress   进度回调；可为 null
   * @param kindFilter 仅处理该 kind；null 处理全部
   * @return 更新后的正文与本轮配图结果
   */
  public Result generate(
      CourseGenerationContext ctx,
      String draft,
      String wechat,
      Map<String, Object> snapshot,
      String jobId,
      ProgressSink progress,
      String kindFilter) throws Exception {
    List<CourseMarkdownUtil.PromptHit> prompts = CourseMarkdownUtil.findPrompts(draft);
    List<ImagePlanItem> plan = CourseImagePromptExtractor.buildPlan(prompts, ctx);
    if (kindFilter != null && !kindFilter.isBlank()) {
      String filter = kindFilter.trim();
      plan = plan.stream().filter(item -> filter.equals(item.kind())).toList();
    }
    List<Map<String, Object>> results = new ArrayList<>();
    String workingDraft = draft == null ? "" : draft;
    String workingWechat = wechat == null ? "" : wechat;
    int planTotal = Math.max(1, plan.size());
    for (ImagePlanItem item : plan) {
      if (progress != null) {
        progress.onImage(item.kind(), item.sequenceNo(), planTotal);
      }
      Map<String, Object> row = generator.generateOne(ctx, item, snapshot, jobId, workingDraft);
      results.add(row);
      CourseImageComposer.ApplyResult applied =
          CourseImageComposer.applyImageResult(ctx, item, row, workingDraft, workingWechat);
      workingDraft = applied.draft();
      workingWechat = applied.wechat();
    }
    // 仅封面时不做全文组版（组版需封面+插图齐全，留给 inline 或全量调用）
    boolean coverOnly = kindFilter != null && "cover".equals(kindFilter.trim());
    if (coverOnly) {
      return new Result(workingDraft, workingWechat, results);
    }
    // apply 后对残留 token 做确定性扫尾，再 compose 组版
    workingWechat = replaceLeftoverImageTokens(workingWechat, results);
    String finalWechat = composeWechatImages(workingWechat, results);
    return new Result(workingDraft, finalWechat, results);
  }

  /**
   * checklist I3：将转换模型留下的 token 标题/裸 token 换成图片 markdown。
   *
   * @param wechat       公众号正文
   * @param imageResults 配图结果
   * @return 替换后正文
   */
  static String replaceLeftoverImageTokens(String wechat, List<Map<String, Object>> imageResults) {
    return CourseImageComposer.replaceLeftoverImageTokens(wechat, imageResults);
  }

  /**
   * 默认封面提示词。
   *
   * @param ctx 课程上下文
   * @return 封面提示词
   */
  static String coverPrompt(CourseGenerationContext ctx) {
    return CourseImagePromptExtractor.coverPrompt(ctx);
  }

  /**
   * 按序号强制轮换构图/色板/隐喻，压低同课雷同。
   *
   * @param item    计划项
   * @param isCover 是否封面
   * @return 差异化说明
   */
  static String diversityBrief(ImagePlanItem item, boolean isCover) {
    return CourseImageGenerator.diversityBrief(item, isCover);
  }

  /**
   * 内文图去掉技能里「全课统一 Keynote」类句子，避免覆盖差异化指令。
   *
   * @param skillPrompt 技能提示
   * @param isCover     是否封面
   * @return 裁剪后提示
   */
  static String trimSkillPrompt(String skillPrompt, boolean isCover) {
    return CourseImageGenerator.trimSkillPrompt(skillPrompt, isCover);
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
    return CourseImageGenerator.sanitizeInlinePrompt(prompt, courseTitle, isCover);
  }

  /**
   * 提示词前 500 + 后 500，压空白后最多 700 字。
   *
   * @param content 全文
   * @param prompt  目标提示词
   * @return 相邻正文摘要
   */
  static String imageArticleContext(String content, String prompt) {
    return CourseImagePromptExtractor.imageArticleContext(content, prompt);
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
    return CourseImageComposer.insertGeneratedCoverPrompt(content, prompt, imageMarkdown);
  }

  /**
   * checklist I4：组版并清理生产残留。
   *
   * @param content      公众号正文
   * @param imageResults 配图结果
   * @return 组版后正文
   */
  static String composeWechatImages(String content, List<Map<String, Object>> imageResults) {
    return CourseImageComposer.composeWechatImages(content, imageResults);
  }

  /**
   * 去掉插图提示词围栏块。
   *
   * @param content 正文
   * @return 清理后正文
   */
  static String stripPromptBlocks(String content) {
    return CourseImageComposer.stripPromptBlocks(content);
  }

  /**
   * checklist I4：清除非正式插图生产说明。
   *
   * @param content 正文
   * @return 清理后正文
   */
  static String stripImageProductionResidue(String content) {
    return CourseImageComposer.stripImageProductionResidue(content);
  }

  /**
   * 是否需要真实截图占位（不调图片模型）。
   *
   * @param marker 标记行
   * @param prompt 提示词
   * @return true 表示截图占位
   */
  static boolean requiresScreenshot(String marker, String prompt) {
    return CourseImagePromptExtractor.requiresScreenshot(marker, prompt);
  }

  /**
   * 单张配图计划项。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class ImagePlanItem {
    /** 全局序号。 */
    private final int sequenceNo;
    /** 内文序号；封面为 null。 */
    private final Integer inlineNo;
    /** 图片种类。 */
    private final String kind;
    /** 原文标记。 */
    private final String marker;
    /** 生成提示词。 */
    private final String prompt;
    /** 占位 token。 */
    private final String token;
    /** 尺寸规格。 */
    private final String size;
    /** 生产方式。 */
    private final String productionMode;
  }

  /**
   * 配图流水线结果。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  public static final class Result {
    /** 更新后的原稿。 */
    private final String draft;
    /** 更新后的公众号正文。 */
    private final String wechat;
    /** 生成图片元数据。 */
    private final List<Map<String, Object>> images;
  }

  /**
   * 配图进度回调。
   */
  @FunctionalInterface
  public interface ProgressSink {
    /**
     * 单张配图进度。
     *
     * @param kind       cover/inline
     * @param sequenceNo 序号
     * @param total      计划总数
     */
    void onImage(String kind, int sequenceNo, int total);
  }
}
