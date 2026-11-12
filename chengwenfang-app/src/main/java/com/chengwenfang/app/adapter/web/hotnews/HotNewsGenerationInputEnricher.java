package com.chengwenfang.app.adapter.web.hotnews;

import com.chengwenfang.job.facade.dto.job.HotNewsArticleGenerationJobInput;
import com.chengwenfang.mode.hotnews.facade.dto.request.HotNewsGenerationRunRequest;
import com.chengwenfang.prompt.facade.WorkflowFacade;
import com.chengwenfang.prompt.facade.enums.WorkflowFeatureBinding;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 热点成文入参补全：按功能绑定反查已启用工作流与 提示词包 快照。 */
@Component
@RequiredArgsConstructor
public class HotNewsGenerationInputEnricher {
  private final WorkflowFacade workflows;

  /**
   * 为生成任务补全 workflowId / promptPackSnapshot。
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
    List<Map<String, Object>> promptPackSnapshot = workflows.promptPackSnapshotForWorkflow(published.getId());
    if (!promptPackSnapshot.isEmpty()) {
      input.put("promptPackSnapshot", promptPackSnapshot);
    }
    return input;
  }
}
