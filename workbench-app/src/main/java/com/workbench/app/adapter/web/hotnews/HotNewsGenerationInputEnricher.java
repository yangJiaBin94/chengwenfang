package com.workbench.app.adapter.web.hotnews;

import com.workbench.job.facade.dto.job.HotNewsArticleGenerationJobInput;
import com.workbench.mode.hotnews.facade.dto.request.HotNewsGenerationRunRequest;
import com.workbench.skill.facade.WorkflowFacade;
import com.workbench.skill.facade.enums.WorkflowFeatureBinding;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 热点成文入参补全：按功能绑定反查已启用工作流与 Skill 快照。 */
@Component
@RequiredArgsConstructor
public class HotNewsGenerationInputEnricher {
  private final WorkflowFacade workflows;

  /**
   * 为生成任务补全 workflowId / skillSnapshot。
   *
   * @param body 原始运行请求；可为 null
   * @return 写入 Job input 的类型化入参
   */
  public HotNewsArticleGenerationJobInput enrich(HotNewsGenerationRunRequest body) {
    HotNewsArticleGenerationJobInput input = new HotNewsArticleGenerationJobInput();
    if (body != null) {
      if (body.getDocumentId() != null && !body.getDocumentId().isBlank()) {
        input.put("documentId", body.getDocumentId());
      }
      if (body.getScope() != null) {
        input.put("scope", body.getScope());
      }
      if (body.getWordCountOption() != null) {
        input.put("wordCountOption", body.getWordCountOption());
      }
      if (body.getImageCountOption() != null) {
        input.put("imageCountOption", body.getImageCountOption());
      }
    }
    // 反查「生成热点公众号文章」可用工作流；停用则抛 workflow_unavailable
    var published = workflows.requireActivePublished(WorkflowFeatureBinding.HOT_NEWS_ARTICLE_GENERATION);
    input.put("workflowId", published.getId());
    if (published.getPublishedVersionId() != null) {
      input.put("workflowVersionId", published.getPublishedVersionId());
    }
    List<Map<String, Object>> skillSnapshot = workflows.skillSnapshotForWorkflow(published.getId());
    if (!skillSnapshot.isEmpty()) {
      input.put("skillSnapshot", skillSnapshot);
    }
    return input;
  }
}
