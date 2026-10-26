package com.chengwenfang.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsErrorCodes;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsWorkflowNodeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 校验产物并落库热点文章版本。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommitVersionNodeHandler implements HotNewsNodeHandler {
  /** 热点门面。 */
  private final HotNewsFacade hotNews;

  @Override
  public HotNewsWorkflowNodeType supports() {
    return HotNewsWorkflowNodeType.COMMIT_VERSION;
  }

  @Override
  public void execute(
      HotNewsGraphState state,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String draft = state.getDraft();
    String wechat = state.getWechat();
    if (StrUtil.isBlank(draft) || StrUtil.isBlank(wechat)) {
      throw new BusinessException(
          HotNewsErrorCodes.COMMIT_INCOMPLETE,
          "草稿或公众号正文为空，无法保存版本",
          422);
    }
    if (!Boolean.TRUE.equals(state.getFinalValidationPassed())) {
      throw new BusinessException(
          HotNewsErrorCodes.FINAL_VALIDATION_FAILED,
          "最终完整性检查未通过，无法保存版本",
          422);
    }
    // 正式提交版本（门面内 sole write 到 content_documents）
    HotNewsArticleView saved = hotNews.commitHotNewsArticle(state.getArticleId(), draft, wechat);
    state.setCommitted(true);
    state.setCommittedArticle(saved);
  }
}
