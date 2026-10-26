package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.application.hotnews.TopicAiWritingScoreService;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按主题量表 AI 评分后标记 Top10。
 * <p>评分过程逐条上报进度并续租，避免界面长时间停在「等待执行」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreTop10RadarNodeHandler implements RadarCollectNodeHandler {
  /** 热点门面。 */
  private final HotNewsFacade hotNews;
  /** 写作价值 AI 评分。 */
  private final TopicAiWritingScoreService writingScoreService;
  /** 任务进度上报。 */
  private final JobFacade jobs;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.SCORE_TOP10;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    String jobId = StrUtil.blankToDefault(
        stringVal(lightState.get(RadarCollectGraphStateKeys.JOB_ID)),
        session.getFetchRunId());
    String stepKey = HotNewsRadarCollectNodeType.SCORE_TOP10.getValue();
    reportProgress(jobId, stepKey, 91, "正在按主题量表 AI 评分…");
    RadarCollectLeaseSupport.renew(lightState);

    // 先按主题量表 AI 打写作价值，再排序标记 Top10
    int scored = writingScoreService.scoreRadarBatch(
        session.getThemeId(),
        session.getRadarBatchId(),
        (done, total) -> {
          RadarCollectLeaseSupport.renew(lightState);
          int progress = 91 + Math.round(done * 6f / Math.max(1, total));
          reportProgress(
              jobId,
              stepKey,
              Math.min(97, progress),
              "AI 写作评分 " + done + "/" + total);
        });
    int top10 = hotNews.markRadarTop10(session.getThemeId(), session.getRadarBatchId());
    session.setTop10Count(top10);
    lightState.put(RadarCollectGraphStateKeys.TOP10_COUNT, top10);
    // 雷达主流程末节点：批次标完成
    hotNews.completeRadarBatch(session.getRadarBatchId());
    String note = "评分完成 " + scored + " 条，已标记 Top10：" + top10 + " 条";
    lightState.put(RadarCollectGraphStateKeys.SCORE_TOP10_NOTE, note);
    reportProgress(jobId, stepKey, 98, note);
    log.info(
        "雷达 AI 评分与 Top10 完成 themeId={} scored={} top10={}",
        session.getThemeId(),
        scored,
        top10);
  }

  /**
   * 上报 Job 进度。
   *
   * @param jobId    任务 ID
   * @param stepKey  节点 typeKey
   * @param progress 进度
   * @param message  文案
   */
  private void reportProgress(String jobId, String stepKey, int progress, String message) {
    if (StrUtil.isBlank(jobId)) {
      return;
    }
    try {
      jobs.updateProgress(jobId, stepKey, Math.min(99, Math.max(1, progress)), message);
    } catch (Exception exception) {
      log.warn("雷达评分进度上报失败 jobId={} err={}", jobId, exception.getMessage());
    }
  }

  /**
   * @param value 任意对象
   * @return 字符串或 null
   */
  private static String stringVal(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
