package com.chengwenfang.mode.course.application.course;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.mode.course.application.generation.course.CourseGraphState;
import com.chengwenfang.quality.facade.AuditFacade;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 课程成文图：原稿组装、压缩、守卫与审校重试。
 */
@Slf4j
@Service
public class CourseDraftReviewer {
  /** 可修复错误码前缀。 */
  private static final List<String> REPAIRABLE_PREFIXES = List.of(
      "COURSE_BODY_TOO_LONG",
      "COURSE_IMAGE_COUNT_INVALID",
      "GENERATED_DRAFT_INCOMPLETE",
      "GENERATED_CONTENT_REQUESTED_MATERIALS",
      "GENERATED_CONTENT_INVALID",
      "GENERATED_CONTENT_REJECTED",
      "WECHAT_CONTENT_NOT_EQUIVALENT");

  /** 分段起草。 */
  private final CourseSectionDrafter sectionDrafter;
  /** 上下文与进度。 */
  private final CourseGraphContextLoader contextLoader;
  /** 校验器。 */
  private final CourseGenerationValidators validators;

  /**
   * @param sectionDrafter 分段起草
   * @param contextLoader  上下文加载
   * @param audit          审核门面
   */
  public CourseDraftReviewer(
      CourseSectionDrafter sectionDrafter,
      CourseGraphContextLoader contextLoader,
      AuditFacade audit) {
    this.sectionDrafter = sectionDrafter;
    this.contextLoader = contextLoader;
    this.validators = new CourseGenerationValidators(audit);
  }

  /**
   * 组装原稿、压缩、守卫与校验；失败时最多 3 轮整稿重生成。
   *
   * @param state 图状态
   */
  public void reviewDraft(CourseGraphState state) {
    if (Boolean.TRUE.equals(state.getResumeImagesOnly())) {
      return;
    }
    contextLoader.markProgress(state, CourseWorkflowNodeType.REVIEW_DRAFT);
    CourseGenerationContext ctx = contextLoader.resolveContext(state);
    String repairFeedback = null;
    IllegalStateException lastError = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        if (attempt > 1) {
          state.setRepairFeedback(repairFeedback);
          state.setDraftParts(new ArrayList<>());
          for (CourseSectionDrafter.DraftSection section : CourseSectionDrafter.DRAFT_SECTIONS) {
            sectionDrafter.generateDraftSection(state, section.typeKey(), List.of());
          }
        }
        String draft = assembleDraft(state, ctx, attempt > 1);
        draft = applyInlinePromptGuards(draft, ctx);
        validators.validateDraft(draft, ctx.maxBodyChars());
        int inlineCount = CourseMarkdownUtil.countInlinePrompts(draft);
        if (inlineCount < ctx.minImages() || inlineCount > ctx.maxImages()) {
          throw new IllegalStateException(
              "COURSE_IMAGE_COUNT_INVALID: 正文插图提示词 " + inlineCount
                  + " 不在 [" + ctx.minImages() + "," + ctx.maxImages() + "]");
        }
        state.setDraft(draft);
        state.setRepairFeedback(null);
        contextLoader.persistTextArtifact(
            state.getJobId(),
            CourseWorkflowNodeType.REVIEW_DRAFT.getValue(),
            Map.of("chars", draft.length()),
            "course_draft",
            draft,
            Map.of("chars", draft.length()));
        return;
      } catch (IllegalStateException ex) {
        lastError = ex;
        if (!isRepairable(ex) || attempt == 3) {
          throw ex;
        }
        repairFeedback = repairMessage(attempt, ex.getMessage());
        state.setRepairFeedback(repairFeedback);
      }
    }
    if (lastError != null) {
      throw lastError;
    }
  }

  /**
   * 组装或复用完整原稿。
   *
   * @param state           图状态
   * @param ctx             上下文
   * @param forceRegenerate 是否强制重装
   * @return 原稿
   */
  private String assembleDraft(CourseGraphState state, CourseGenerationContext ctx, boolean forceRegenerate) {
    List<String> draftParts = state.getDraftParts() == null ? List.of() : state.getDraftParts();
    String savedDraft = forceRegenerate
        ? null
        : contextLoader.workflows().latestSucceededArtifactText(
            state.getJobId(),
            CourseWorkflowNodeType.REVIEW_DRAFT.getValue(),
            "course_draft");
    boolean allPresent = draftParts.size() >= CourseSectionDrafter.DRAFT_SECTIONS.size();
    String draft;
    if (allPresent && StrUtil.isNotBlank(savedDraft) && !forceRegenerate) {
      draft = cleanModelDraft(savedDraft.strip());
    } else {
      draft = ("# " + ctx.title() + "\n\n" + String.join("\n\n", draftParts)).strip() + "\n";
      draft = validators.normalizeFormulaBlocks(draft);
      draft = maybeCompressDraft(state, ctx, draft);
      draft = cleanModelDraft(draft);
    }
    return draft;
  }

  /**
   * 插图提示词数量守卫与分布。
   *
   * @param draft 原稿
   * @param ctx   上下文
   * @return 调整后原稿
   */
  private String applyInlinePromptGuards(String draft, CourseGenerationContext ctx) {
    String output = CourseMarkdownUtil.ensureInlinePromptCount(
        draft, ctx.minImages(), ctx.title(), List.of());
    output = CourseMarkdownUtil.limitInlinePrompts(output, ctx.maxImages());
    return CourseMarkdownUtil.distributeInlinePrompts(output);
  }

  /**
   * 超限时定向压缩原稿。
   *
   * @param state 图状态
   * @param ctx   上下文
   * @param draft 原稿
   * @return 压缩后原稿
   */
  private String maybeCompressDraft(CourseGraphState state, CourseGenerationContext ctx, String draft) {
    if (CourseMarkdownUtil.lessonBodyCharCount(draft) <= ctx.maxBodyChars()) {
      return cleanModelDraft(draft);
    }
    int bodyCharsBefore = CourseMarkdownUtil.lessonBodyCharCount(draft);
    contextLoader.reportJobStep(
        state.getJobId(),
        "compress_draft",
        50,
        "完整原稿为 " + bodyCharsBefore + " 字，正在按全文 " + ctx.maxBodyChars() + " 字上限定向压缩");
    log.info(
        "课程原稿超限，开始压缩 beforeChars={} maxBodyChars={}",
        bodyCharsBefore,
        ctx.maxBodyChars());
    List<CourseMarkdownUtil.PromptHit> requiredBlocks = CourseMarkdownUtil.findPrompts(draft).stream()
        .filter(hit -> hit.prompt() != null && !hit.prompt().isBlank())
        .toList();
    String current = draft;
    IllegalStateException lastError = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      int bodyChars = CourseMarkdownUtil.lessonBodyCharCount(current);
      int target = CourseMarkdownUtil.compressionTarget(bodyChars, ctx.maxBodyChars());
      int requiredReduction = Math.max(0, bodyChars - target);
      String userPrompt = """
          请编辑下面的完整课程原稿，将去除插图位置、插图提示词和图片引用后的正文压缩到 %d 字以内。

          当前正文为 %d 字，本轮必须至少删除或合并 %d 字。输出前请自行复核篇幅，不能仅改写措辞而不缩短正文。

          课程边界：
          %s

          严格要求：
          - 只做删减、合并和措辞精简，不新增知识、步骤或案例。
          - 保留课程目标、必要概念、Windows 与 macOS 的操作差异、命令、预期结果、失败处理、本课小结和下节课预告。
          - 保留全部插图位置与提示词，尤其是“操作截图”标记。
          - 保留 Markdown 标题结构和可运行代码，不破坏代码缩进。
          - 删除重复解释、重复提醒、冗余铺垫和超出本课边界的扩展。
          - 只输出压缩后的完整 Markdown 原稿。

          需要压缩的原稿：
          %s
          """.formatted(target, bodyChars, requiredReduction, ctx.renderPlan(), current);
      String compressed = sectionDrafter.completeText(
          null, userPrompt, ModelCallScenes.DRAFT_COMPRESS);
      compressed = validators.normalizeFormulaBlocks(compressed);
      compressed = CourseMarkdownUtil.restoreMissingPromptBlocks(compressed, requiredBlocks);
      compressed = cleanModelDraft(compressed);
      try {
        validators.validateDraft(compressed, ctx.maxBodyChars());
        return compressed;
      } catch (IllegalStateException ex) {
        lastError = ex;
        current = compressed;
      }
    }
    if (lastError != null) {
      throw lastError;
    }
    validators.validateDraft(current, ctx.maxBodyChars());
    return current;
  }

  /**
   * 定稿出口：规范化插图提示词为权威标准块，再清推理腔与畸形说明。
   *
   * @param draft 模型原稿
   * @return 可继续 ensure/审校的正文
   */
  private static String cleanModelDraft(String draft) {
    String text = draft == null ? "" : draft;
    text = CourseMarkdownUtil.sanitizeFullDraft(text);
    text = CourseMarkdownUtil.canonicalizeIllustrationPrompts(text);
    text = CourseMarkdownUtil.stripEmptyPromptBlocks(text);
    text = CourseMarkdownUtil.stripMalformedIllustrationNotes(text);
    text = CourseMarkdownUtil.stripInlineReasoningParagraphs(text);
    text = CourseMarkdownUtil.preferLastCompleteDraft(text);
    return CourseMarkdownUtil.sanitizeFullDraft(text);
  }

  /**
   * 判断异常是否可修复重试。
   *
   * @param ex 非法状态异常
   * @return true 表示可修复
   */
  static boolean isRepairable(IllegalStateException ex) {
    String message = ex.getMessage() == null ? "" : ex.getMessage();
    for (String prefix : REPAIRABLE_PREFIXES) {
      if (message.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 组装修复反馈文案。
   *
   * @param attempt       轮次
   * @param errorMessage  错误信息
   * @return 反馈
   */
  static String repairMessage(int attempt, String errorMessage) {
    String codeMessage = errorMessage == null ? "" : errorMessage;
    String action;
    if (codeMessage.startsWith("COURSE_BODY_TOO_LONG")) {
      action = "请删除重复解释、冗余铺垫和提前展开的后续课程内容，在保留当前课核心知识的前提下压缩正文。";
    } else if (codeMessage.startsWith("COURSE_IMAGE_COUNT_INVALID")) {
      action = "请把插图改到本次配图档位区间内，封面不计入该数量。";
    } else if (codeMessage.startsWith("GENERATED_DRAFT_INCOMPLETE")
        || codeMessage.startsWith("COURSE_CONTENT_STRUCTURE_INVALID")) {
      action = "请补回本课小结与下节课预告，且各出现一次，顺序正确。";
    } else {
      action = "请在保留现有正确内容的基础上补足缺失内容。";
    }
    return "第 " + attempt + " 轮检查未通过。" + codeMessage + "。" + action;
  }
}
