package com.workbench.mode.course.application.course;

import com.workbench.mode.course.application.prompt.CoursePrompts;

import cn.hutool.core.util.StrUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.ai.facade.dto.request.TextCompleteRequest;
import com.workbench.mode.course.application.generation.course.CourseGraphState;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.CourseWorkflowNodeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;

/**
 * 课程成文图：按分段生成原稿章节。
 */
@Service
@RequiredArgsConstructor
public class CourseSectionDrafter {
  /** 课程文本调用最少超时（秒）。 */
  private static final int TEXT_MIN_TIMEOUT_SECONDS = 180;

  /** 固定分段顺序。 */
  static final List<DraftSection> DRAFT_SECTIONS = List.of(
      new DraftSection(CourseWorkflowNodeType.DRAFT_OPENING, "开场与本课目标",
          "建立具体场景、承接上一课、明确本课目标。"),
      new DraftSection(CourseWorkflowNodeType.DRAFT_CORE, "核心原理",
          "解释概念、术语、公式与边界。"),
      new DraftSection(CourseWorkflowNodeType.DRAFT_EXAMPLE, "案例与推导",
          "生成例子、计算、推导；如需插图，必须使用权威格式：**插图位置：主题** + ```text 围栏（禁止括号/裸提示词行/引用块）。至少给出与配图档位匹配数量的正文插图提示词。"),
      new DraftSection(CourseWorkflowNodeType.DRAFT_SUMMARY, "本课小结",
          "收束当前课程，不引入新知识。标题必须为「本课小结」。"),
      new DraftSection(CourseWorkflowNodeType.DRAFT_PREVIEW, "下节课预告",
          "按路线预告下一课，不提前展开。标题必须为「下节课预告」。"));

  /** Skill 门面。 */
  private final SkillFacade skills;
  /** 文本 AI。 */
  private final AiTextFacade ai;
  /** 上下文与进度协作。 */
  private final CourseGraphContextLoader contextLoader;

  /**
   * 生成单个草稿分段并追加到 {@code draftParts}。
   *
   * @param state         图状态
   * @param typeKey       分段 typeKey
   * @param skillBindings 节点绑定；为空时从快照节点读取
   */
  public void generateDraftSection(
      CourseGraphState state,
      String typeKey,
      List<SkillPromptBindingView> skillBindings) {
    if (Boolean.TRUE.equals(state.getResumeImagesOnly())) {
      return;
    }
    DraftSection section = findSection(typeKey);
    if (section == null) {
      throw new IllegalArgumentException("未知草稿分段：" + typeKey);
    }
    CourseGenerationContext ctx = contextLoader.resolveContext(state);
    Map<String, Object> snapshot = state.getWorkflowSnapshot();
    Map<String, Object> node = CourseGraphContextLoader.findNodeByTypeKey(snapshot, section.typeKey());
    List<SkillPromptBindingView> bindings = (skillBindings == null || skillBindings.isEmpty())
        ? CourseGraphContextLoader.skillBindingsOf(node)
        : skillBindings;
    List<String> draftParts = state.getDraftParts() == null
        ? new ArrayList<>()
        : new ArrayList<>(state.getDraftParts());
    String previousTail = draftParts.isEmpty()
        ? ""
        : CourseGraphContextLoader.tail(draftParts.get(draftParts.size() - 1), 1600);
    int usedBody = draftParts.stream().mapToInt(CourseMarkdownUtil::lessonBodyCharCount).sum();
    int[] budget = ctx.sectionBudget(section.typeKey(), usedBody);
    String repairFeedback = state.getRepairFeedback();
    boolean forceRegenerate = StrUtil.isNotBlank(repairFeedback);
    String nodeKey = node == null
        ? section.typeKey()
        : CourseGraphContextLoader.str(node.getOrDefault("key", section.typeKey()));
    Map<String, Object> sectionInput = sectionInputSummary(
        ctx, section, previousTail, repairFeedback, budget, usedBody, forceRegenerate);
    contextLoader.markProgress(state, section.nodeType());
    String reused = forceRegenerate
        ? null
        : reusableSection(state.getJobId(), nodeKey, section, sectionInput, ctx);
    String text;
    if (reused != null) {
      text = reused;
    } else {
      // 用传入绑定覆盖节点绑定（图执行器传入节点级绑定）
      Map<String, Object> effectiveNode = node == null ? new LinkedHashMap<>() : new LinkedHashMap<>(node);
      if (bindings != null && !bindings.isEmpty()) {
        effectiveNode.put("skillBindings", CourseGraphContextLoader.bindingsToMaps(bindings));
      }
      text = generateDraftSectionOnce(
          ctx, section, effectiveNode, previousTail, repairFeedback, budget);
    }
    draftParts.add(text.strip());
    state.setDraftParts(draftParts);
    // 落分段产物，供同 Job 复用与轨迹页查看
    contextLoader.persistTextArtifact(
        state.getJobId(),
        nodeKey,
        sectionInput,
        "draft_section",
        text.strip(),
        Map.of("section", section.typeKey(), "chars", text.strip().length()));
  }

  /**
   * 单次分段生成并清洗。
   *
   * @param ctx            上下文
   * @param section        分段定义
   * @param node           有效节点（含绑定）
   * @param previousTail   上一章末尾
   * @param repairFeedback 修复反馈
   * @param budget         字数预算
   * @return 清洗后分段正文
   */
  private String generateDraftSectionOnce(
      CourseGenerationContext ctx,
      DraftSection section,
      Map<String, Object> node,
      String previousTail,
      String repairFeedback,
      int[] budget) {
    String sectionFeedback = StrUtil.isBlank(repairFeedback) ? "无，这是首次生成" : repairFeedback;
    String raw = generateDraftSectionText(
        ctx, section, node, previousTail, sectionFeedback, "", budget);
    String cleaned = CourseMarkdownUtil.sanitizeDraftSection(
        raw, section.typeKey(), sectionHeading(ctx, section));
    return CourseMarkdownUtil.stripInlineReasoningParagraphs(cleaned);
  }

  /**
   * 组装分段 user prompt 并调用文本模型。
   *
   * @param ctx               上下文
   * @param section           分段
   * @param node              节点
   * @param previousTail      上一章末尾
   * @param sectionFeedback   反馈
   * @param previousCandidate 上一版候选
   * @param budget            预算
   * @return 模型原文
   */
  private String generateDraftSectionText(
      CourseGenerationContext ctx,
      DraftSection section,
      Map<String, Object> node,
      String previousTail,
      String sectionFeedback,
      String previousCandidate,
      int[] budget) {
    String skillPrompt = skills.composePromptFromBindings(
        CourseGraphContextLoader.skillBindingsOf(node));
    String heading = sectionHeading(ctx, section);
    String goal = sectionGoal(ctx, section, budget);
    int minWords = CourseMarkdownUtil.minBodyChars(ctx.wordCountOption());
    String previousCandidateLine = StrUtil.isBlank(previousCandidate) ? "无" : previousCandidate;
    String userPrompt = """
        请只生成课程原稿中的一个章节，不要输出其他章节，也不要解释过程。
        课程：%s｜%s %s
        本节标题：%s
        本节任务：%s
        上一课：%s
        下一课：%s

        课程边界计划：
        %s

        本系列路线：
        %s

        前一已完成章节末尾（仅用于自然衔接，不要重复）：
        %s

        本节上轮检查反馈：%s

        需要压缩的上一版正文（为空表示首次生成）：
        %s

        输出要求：
        - 第一行必须是“## %s”。
        - 只写本节，知识准确、自然连贯，面向只有初中数学基础、同时没有 AI 与编程经验的读者。
        - 本节去除插图位置、插图提示词和图片引用后的实际正文目标为 %d～%d 字；这是根据全文剩余预算动态计算出的建议范围，章节比例只是参考，必要时可以合理使用剩余额度，但全文最终不得超过 %d 字。
        - 上方存在上一版正文时，不得重新构思或扩写，必须直接编辑该版本：保留必要步骤、命令、预期结果和故障处理，合并重复说明并删除枝节，完整输出压缩后的本节。
        - 全文所有章节合并后，去除插图位置、插图提示词和图片引用的实际正文应符合 %d～%d 字范围；严格遵守本节任务中的篇幅预算，避免重复解释。
        - 涉及代码时，不得默认读者了解 Python、命令行、开发环境、依赖安装、类、函数、变量、循环或缩进；首次出现代码必须交代语言、用途、运行位置、准备步骤、执行顺序、预期输出和常见错误。
        - 代码必须放在带语言标识的 Markdown 代码块中，保持可复制运行的原始缩进、下划线和多行结构；代码本身不产生可见输出时，补充最小验证方式。
        - 除“本课小结”和“下节课预告”外的章节不得提前输出这两个标题。
        - 全文生成 %d～%d 个正文插图需求，不含封面；核心原理或例子章节需要插图时，必须使用权威格式：**插图位置：主题** + ```text 围栏（禁止括号/裸提示词行/引用块）。需要展示真实软件、网页、安装步骤、菜单点击、终端或代码实际运行结果时，必须标记为“**插图位置：操作截图｜具体画面**”，后续只放默认占位图，不调用图片模型；其余概念图、流程图、结构图和对比图使用普通“**插图位置：…**”，由图片模型生成。
        - 不要求用户提供任何素材。
        """.formatted(
        ctx.seriesName(),
        ctx.lessonNo() == null ? "" : ("第 " + ctx.lessonNo() + " 课"),
        ctx.title(),
        heading,
        goal,
        ctx.neighborLine(true),
        ctx.neighborLine(false),
        ctx.renderPlan(),
        ctx.renderRoute().isBlank() ? "（无）" : ctx.renderRoute(),
        previousTail.isBlank() ? "无" : previousTail,
        sectionFeedback,
        previousCandidateLine,
        heading,
        budget[0],
        budget[1],
        ctx.maxBodyChars(),
        Math.max(1, minWords),
        ctx.maxBodyChars(),
        ctx.minImages(),
        ctx.maxImages());
    return completeText(skillPrompt, userPrompt, section.typeKey());
  }

  /**
   * 分段标题。
   *
   * @param ctx     上下文
   * @param section 分段
   * @return 标题文案
   */
  static String sectionHeading(CourseGenerationContext ctx, DraftSection section) {
    if (CourseWorkflowNodeType.DRAFT_CORE.matches(section.typeKey())) {
      return ctx.title() + "的核心原理";
    }
    if (CourseWorkflowNodeType.DRAFT_EXAMPLE.matches(section.typeKey())) {
      return "它如何工作：具体例子与推导";
    }
    return section.label();
  }

  /**
   * 分段写作目标。
   *
   * @param ctx     上下文
   * @param section 分段
   * @param budget  预算
   * @return 目标说明
   */
  private static String sectionGoal(CourseGenerationContext ctx, DraftSection section, int[] budget) {
    if (CourseWorkflowNodeType.DRAFT_OPENING.matches(section.typeKey())) {
      return "从具体问题或生活场景建立直觉，并明确本课目标；正文控制在 " + budget[0] + "～" + budget[1] + " 字。";
    }
    if (CourseWorkflowNodeType.DRAFT_CORE.matches(section.typeKey())) {
      return "完整解释本课必须覆盖的概念、术语、边界和必要公式；正文控制在 " + budget[0] + "～" + budget[1] + " 字。";
    }
    if (CourseWorkflowNodeType.DRAFT_EXAMPLE.matches(section.typeKey())) {
      return "用零基础读者可跟随的例子、计算或推导巩固核心原理；全文共安排 "
          + ctx.minImages() + "～" + ctx.maxImages()
          + " 个正文插图提示词，不含封面；不计插图提示词，本节正文控制在 "
          + budget[0] + "～" + budget[1] + " 字。";
    }
    if (CourseWorkflowNodeType.DRAFT_SUMMARY.matches(section.typeKey())) {
      return "准确收束本课知识，不引入新知识；正文控制在 " + budget[0] + "～" + budget[1] + " 字。";
    }
    if (CourseWorkflowNodeType.DRAFT_PREVIEW.matches(section.typeKey())) {
      return "只根据学习路线自然预告下一课，不提前展开下一课正文；正文控制在 "
          + budget[0] + "～" + budget[1] + " 字。";
    }
    return section.instruction();
  }

  /**
   * 分段复用哈希输入摘要。
   *
   * @param ctx             上下文
   * @param section         分段
   * @param previousTail    上一章末尾
   * @param repairFeedback  修复反馈
   * @param budget          预算
   * @param usedBodyChars   已用正文字数
   * @param forceRegenerate 是否强制重生成
   * @return 输入摘要
   */
  private static Map<String, Object> sectionInputSummary(
      CourseGenerationContext ctx,
      DraftSection section,
      String previousTail,
      String repairFeedback,
      int[] budget,
      int usedBodyChars,
      boolean forceRegenerate) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("section", section.typeKey());
    input.put("heading", sectionHeading(ctx, section));
    input.put("previousTail", previousTail);
    input.put("repair", repairFeedback == null ? "" : repairFeedback);
    input.put("budgetMin", budget[0]);
    input.put("budgetMax", budget[1]);
    input.put("sectionBodyMax", budget[1]);
    input.put("sectionHardMax", ctx.sectionHardMax(section.typeKey(), usedBodyChars));
    input.put("wordOption", ctx.wordCountOption());
    input.put("imageOption", ctx.imageCountOption());
    input.put("force", forceRegenerate);
    return input;
  }

  /**
   * 文本补全（系统提示 + 用户提示）。
   *
   * @param skillPrompt Skill 拼装提示
   * @param userPrompt  用户提示
   * @param scene       调用场景
   * @return 模型文本
   */
  String completeText(String skillPrompt, String userPrompt, String scene) {
    String systemPrompt = CoursePrompts.systemPrompt(skillPrompt);
    var result = ai.completeText(TextCompleteRequest.builder()
        .prompt(userPrompt.strip())
        .systemPrompt(systemPrompt)
        .scene(scene)
        .timeoutSeconds(TEXT_MIN_TIMEOUT_SECONDS)
        .build());
    if (StrUtil.isBlank(result.getText())) {
      throw new IllegalStateException("GENERATED_CONTENT_INVALID: 文本生成返回为空，scene=" + scene);
    }
    String text = result.getText().strip();
    if (text.regionMatches(true, 0, "<html", 0, 5)) {
      throw new IllegalStateException("GENERATED_CONTENT_INVALID: 文本生成返回 HTML，scene=" + scene);
    }
    return text;
  }

  /**
   * 尝试复用同 Job 已成功分段产物。
   *
   * @param jobId        任务 ID
   * @param nodeKey      节点键
   * @param section      分段
   * @param inputSummary 输入摘要
   * @param ctx          上下文
   * @return 可复用正文；无则 null
   */
  private String reusableSection(
      String jobId,
      String nodeKey,
      DraftSection section,
      Map<String, Object> inputSummary,
      CourseGenerationContext ctx) {
    String saved = contextLoader.workflows().reusableArtifactText(
        jobId, nodeKey, "draft_section", inputSummary);
    if (StrUtil.isBlank(saved)) {
      return null;
    }
    String cleaned = CourseMarkdownUtil.sanitizeDraftSection(
        saved, section.typeKey(), sectionHeading(ctx, section));
    return StrUtil.isBlank(cleaned) ? null : cleaned;
  }

  /**
   * 按 typeKey 查找分段定义。
   *
   * @param typeKey 节点 typeKey
   * @return 分段；未找到为 null
   */
  static DraftSection findSection(String typeKey) {
    for (DraftSection section : DRAFT_SECTIONS) {
      if (section.nodeType().matches(typeKey)) {
        return section;
      }
    }
    return null;
  }

  /**
   * 草稿分段定义。
   */
  @Getter
  @Accessors(fluent = true)
  @AllArgsConstructor
  static final class DraftSection {
    /** 节点枚举。 */
    private final CourseWorkflowNodeType nodeType;
    /** 中文段落名。 */
    private final String label;
    /** 写作要求。 */
    private final String instruction;

    /**
     * @return 节点 typeKey
     */
    String typeKey() {
      return nodeType.getValue();
    }
  }
}
