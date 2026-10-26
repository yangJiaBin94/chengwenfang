package com.workbench.app.adapter.web.hotnews;

import com.workbench.job.facade.dto.job.HotNewsFetchJobInput;
import com.workbench.skill.facade.WorkflowFacade;
import com.workbench.skill.facade.enums.WorkflowFeatureBinding;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 雷达采集入参补全：按功能绑定反查已启用工作流与 Skill 快照。 */
@Component
@RequiredArgsConstructor
public class HotNewsFetchInputEnricher {
  /** 工作流门面。 */
  private final WorkflowFacade workflows;

  /**
   * 为采集任务补全 workflowId / skillSnapshot。
   *
   * @param themeId    主题 ID
   * @param channelIds 渠道 ID
   * @param hours      回溯小时
   * @return Job 入参
   */
  public HotNewsFetchJobInput enrich(String themeId, List<String> channelIds, int hours) {
    HotNewsFetchJobInput input = new HotNewsFetchJobInput();
    input.put("themeId", themeId);
    input.put("channelIds", channelIds == null ? List.of() : channelIds);
    input.put("hours", hours);
    // 反查「热点雷达采集」可用工作流；停用则抛 workflow_unavailable
    var published = workflows.requireActivePublished(WorkflowFeatureBinding.HOT_NEWS_RADAR_COLLECT);
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
