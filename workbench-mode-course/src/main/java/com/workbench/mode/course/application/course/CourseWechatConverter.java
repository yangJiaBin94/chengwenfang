package com.workbench.mode.course.application.course;

import cn.hutool.core.util.StrUtil;
import com.workbench.ai.facade.AiTextFacade;
import com.workbench.mode.course.application.generation.course.CourseGraphState;
import com.workbench.quality.facade.AuditFacade;
import com.workbench.skill.facade.SkillFacade;
import com.workbench.skill.facade.constant.CourseWorkflowKeys;
import com.workbench.skill.facade.dto.view.SkillPromptBindingView;
import com.workbench.skill.facade.enums.CourseWorkflowNodeType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 课程成文图：公众号等量转换（包装 {@link WechatChunkConverter}）。
 */
@Service
public class CourseWechatConverter {
  /** 分块转换器。 */
  private final WechatChunkConverter wechatConverter;
  /** 上下文与进度。 */
  private final CourseGraphContextLoader contextLoader;
  /** 校验器。 */
  private final CourseGenerationValidators validators;

  /**
   * @param ai            文本 AI
   * @param skills        Skill 门面
   * @param contextLoader 上下文加载
   * @param audit         审核门面
   */
  public CourseWechatConverter(
      AiTextFacade ai,
      SkillFacade skills,
      CourseGraphContextLoader contextLoader,
      AuditFacade audit) {
    this.wechatConverter = new WechatChunkConverter(ai, skills);
    this.contextLoader = contextLoader;
    this.validators = new CourseGenerationValidators(audit);
  }

  /**
   * 公众号等量转换（内含最多 3 次可修复重试）。
   *
   * @param state         图状态
   * @param skillBindings 节点绑定
   */
  public void convertWechat(CourseGraphState state, List<SkillPromptBindingView> skillBindings) {
    if (Boolean.TRUE.equals(state.getResumeImagesOnly())) {
      return;
    }
    contextLoader.markProgress(state, CourseWorkflowNodeType.WECHAT_CONVERSION);
    CourseGenerationContext ctx = contextLoader.resolveContext(state);
    String draft = state.getDraft();
    Map<String, Object> wechatNode = CourseGraphContextLoader.findNodeByTypeKey(
        state.getWorkflowSnapshot(), CourseWorkflowNodeType.WECHAT_CONVERSION.getValue());
    if (skillBindings != null && !skillBindings.isEmpty()) {
      Map<String, Object> node = wechatNode == null ? new LinkedHashMap<>() : new LinkedHashMap<>(wechatNode);
      node.put("skillBindings", CourseGraphContextLoader.bindingsToMaps(skillBindings));
      wechatNode = node;
    }
    String wechatRepair = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        Map<String, Object> wechatInput = new LinkedHashMap<>();
        wechatInput.put("draft", draft);
        wechatInput.put("repair", wechatRepair == null ? "" : wechatRepair);
        String reusedWechat = contextLoader.workflows().reusableArtifactText(
            state.getJobId(),
            CourseWorkflowNodeType.WECHAT_CONVERSION.getProgressStepKey(),
            "assembled_wechat",
            wechatInput);
        String wechat;
        if (StrUtil.isNotBlank(reusedWechat)) {
          wechat = reusedWechat.strip();
        } else {
          WechatChunkConverter.Result wechatResult = wechatConverter.convert(
              draft,
              ctx,
              wechatNode,
              (index, total, stepName) -> contextLoader.reportJobStep(
                  state.getJobId(),
                  stepName,
                  55 + Math.round(index * 15f / Math.max(1, total)),
                  "正在转换公众号文章（第 " + index + "/" + total + " 段）"),
              wechatRepair);
          wechat = wechatResult.wechat();
        }
        // 第 8 步只做转换；字数与等量审校挂到第 9 步进度，失败时 UI 落在「审校公众号文章」
        contextLoader.reportJobStep(
            state.getJobId(),
            CourseWorkflowKeys.PROGRESS_REVIEW_WECHAT,
            72,
            "审校公众号文章字数与等量性");
        int wechatBody = CourseMarkdownUtil.lessonBodyCharCount(wechat);
        if (wechatBody > ctx.maxBodyChars()) {
          throw new IllegalStateException(
              "COURSE_BODY_TOO_LONG: 公众号文章 正文 " + wechatBody + " 超过 " + ctx.maxBodyChars());
        }
        String tokenized = CourseMarkdownUtil.prepareWechatSource(draft).prepared();
        validators.rejectKnowledgeGaps(wechat, tokenized);
        validators.assertContentManifest(draft, wechat);
        state.setWechat(wechat);
        // 产物 nodeKey 用进度别名 generate_wechat，与续跑/复用查询一致
        contextLoader.persistTextArtifact(
            state.getJobId(),
            CourseWorkflowNodeType.WECHAT_CONVERSION.getProgressStepKey(),
            wechatInput,
            "assembled_wechat",
            wechat,
            Map.of("chars", wechat.length()));
        return;
      } catch (IllegalStateException ex) {
        if (!CourseDraftReviewer.isRepairable(ex) || attempt == 3) {
          throw ex;
        }
        wechatRepair = CourseDraftReviewer.repairMessage(attempt, ex.getMessage());
      }
    }
  }
}
