package com.chengwenfang.mode.course.application.course;

import com.chengwenfang.ai.facade.AiImageFacade;
import com.chengwenfang.mode.course.application.generation.course.CourseGraphState;
import com.chengwenfang.content.facade.ContentFacade;
import com.chengwenfang.infra.support.StorageService;
import com.chengwenfang.quality.facade.AuditFacade;
import com.chengwenfang.skill.facade.SkillFacade;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.CourseWorkflowNodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 课程成文图节点业务支持门面：委派上下文加载、分段起草、原稿审校、公众号转换与版本提交。
 */
@Service
public class CourseArticleGraphSupport {
  /** 上下文加载。 */
  private final CourseGraphContextLoader contextLoader;
  /** 分段起草。 */
  private final CourseSectionDrafter sectionDrafter;
  /** 原稿审校。 */
  private final CourseDraftReviewer draftReviewer;
  /** 公众号转换。 */
  private final CourseWechatConverter wechatConverter;
  /** 版本提交。 */
  private final CourseVersionCommitter versionCommitter;
  /** 校验器。 */
  private final CourseGenerationValidators validators;
  /** 配图管线。 */
  private final CourseImagePipeline imagePipeline;

  /**
   * @param contextLoader    上下文加载
   * @param sectionDrafter   分段起草
   * @param draftReviewer    原稿审校
   * @param wechatConverter  公众号转换
   * @param versionCommitter 版本提交
   * @param content          内容门面
   * @param images           图像 AI
   * @param storage          本地存储
   * @param skills           Skill 门面
   * @param audit            审核门面
   */
  public CourseArticleGraphSupport(
      CourseGraphContextLoader contextLoader,
      CourseSectionDrafter sectionDrafter,
      CourseDraftReviewer draftReviewer,
      CourseWechatConverter wechatConverter,
      CourseVersionCommitter versionCommitter,
      ContentFacade content,
      AiImageFacade images,
      StorageService storage,
      SkillFacade skills,
      AuditFacade audit) {
    this.contextLoader = contextLoader;
    this.sectionDrafter = sectionDrafter;
    this.draftReviewer = draftReviewer;
    this.wechatConverter = wechatConverter;
    this.versionCommitter = versionCommitter;
    this.validators = new CourseGenerationValidators(audit);
    this.imagePipeline = new CourseImagePipeline(images, content, storage, skills);
  }

  /**
   * 加载已发布工作流快照与课程上下文；续跑配图时回填原稿/公众号。
   *
   * @param state         图状态
   * @param skillBindings 未使用（节点签名对齐）
   */
  public void snapshotContext(CourseGraphState state, List<SkillPromptBindingView> skillBindings) {
    contextLoader.snapshotContext(state, skillBindings);
  }

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
    sectionDrafter.generateDraftSection(state, typeKey, skillBindings);
  }

  /**
   * 组装原稿、压缩、守卫与校验；失败时最多 3 轮整稿重生成。
   *
   * @param state 图状态
   */
  public void reviewDraft(CourseGraphState state) {
    draftReviewer.reviewDraft(state);
  }

  /**
   * 公众号等量转换（内含最多 3 次可修复重试）。
   *
   * @param state         图状态
   * @param skillBindings 节点绑定
   */
  public void convertWechat(CourseGraphState state, List<SkillPromptBindingView> skillBindings) {
    wechatConverter.convertWechat(state, skillBindings);
  }

  /**
   * 生成课程封面；若封面与插图均已完成则跳过。
   *
   * @param state 图状态
   */
  public void generateCover(CourseGraphState state) throws Exception {
    if (Boolean.TRUE.equals(state.getCoverDone()) && Boolean.TRUE.equals(state.getInlineDone())) {
      return;
    }
    contextLoader.markProgress(state, CourseWorkflowNodeType.IMAGE_COVER);
    // 封面节点一次跑完全量配图，正文插图节点可跳过
    runImagePipeline(state, null);
    state.setCoverDone(true);
    state.setInlineDone(true);
  }

  /**
   * 生成正文插图；封面节点已完成后跳过。
   *
   * @param state 图状态
   */
  public void generateInline(CourseGraphState state) throws Exception {
    if (Boolean.TRUE.equals(state.getInlineDone())) {
      return;
    }
    contextLoader.markProgress(state, CourseWorkflowNodeType.IMAGE_INLINE);
    runImagePipeline(state, "inline");
    state.setInlineDone(true);
    state.setCoverDone(true);
  }

  /**
   * 公式引用块样式校验。
   *
   * @param state 图状态
   */
  public void validateFormulaStyle(CourseGraphState state) {
    contextLoader.markProgress(state, CourseWorkflowNodeType.FORMULA_STYLE_VALIDATION);
    validators.validateFormulaStyle(state.getDraft(), state.getWechat());
  }

  /**
   * 公式普通表达式（禁 LaTeX）校验。
   *
   * @param state 图状态
   */
  public void validateFormulaExpression(CourseGraphState state) {
    contextLoader.markProgress(state, CourseWorkflowNodeType.FORMULA_EXPRESSION_VALIDATION);
    validators.validateFormulaExpression(state.getDraft(), state.getWechat());
  }

  /**
   * 知识库一致性终检。
   *
   * @param state 图状态
   */
  public void validateFinal(CourseGraphState state) {
    contextLoader.markProgress(state, CourseWorkflowNodeType.FINAL_VALIDATION);
    CourseGenerationContext ctx = contextLoader.resolveContext(state);
    validators.validateFinal(state.getDraft(), state.getWechat(), ctx, state.getImages());
  }

  /**
   * 提交课程版本并同步预告；回写 draft/wechat/images 供 Job 输出。
   *
   * @param state 图状态
   */
  public void commitVersion(CourseGraphState state) {
    versionCommitter.commitVersion(state);
  }

  /**
   * 解析课程生成上下文。
   *
   * @param state 图状态
   * @return 上下文
   */
  public CourseGenerationContext resolveContext(CourseGraphState state) {
    return contextLoader.resolveContext(state);
  }

  /**
   * 执行配图管线并回写状态。
   *
   * @param state      图状态
   * @param kindFilter cover/inline/null
   */
  private void runImagePipeline(CourseGraphState state, String kindFilter) throws Exception {
    CourseGenerationContext ctx = contextLoader.resolveContext(state);
    CourseImagePipeline.Result imageResult = imagePipeline.generate(
        ctx,
        state.getDraft(),
        state.getWechat(),
        state.getWorkflowSnapshot(),
        state.getJobId(),
        (kind, sequenceNo, total) -> {
          String key = "cover".equals(kind)
              ? CourseWorkflowNodeType.IMAGE_COVER.getValue()
              : CourseWorkflowNodeType.IMAGE_INLINE.getValue();
          contextLoader.reportJobStep(
              state.getJobId(),
              key,
              75 + Math.round(sequenceNo * 10f / Math.max(1, total)),
              "正在生成第 " + sequenceNo + "/" + total + " 张配图");
        },
        kindFilter);
    List<Map<String, Object>> merged = new ArrayList<>();
    if (state.getImages() != null) {
      merged.addAll(state.getImages());
    }
    if (imageResult.images() != null) {
      merged.addAll(imageResult.images());
    }
    String wechat = imageResult.wechat();
    // 分次出图时用合并列表做最终组版
    if (kindFilter != null && "inline".equals(kindFilter) && !merged.isEmpty()) {
      wechat = CourseImagePipeline.replaceLeftoverImageTokens(wechat, merged);
      wechat = CourseImagePipeline.composeWechatImages(wechat, merged);
    }
    state.setDraft(imageResult.draft());
    state.setWechat(wechat);
    state.setImages(merged);
  }
}
