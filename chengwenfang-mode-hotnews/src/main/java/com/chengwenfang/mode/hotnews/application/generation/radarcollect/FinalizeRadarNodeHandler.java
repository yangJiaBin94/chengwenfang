package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.skill.facade.dto.view.SkillPromptBindingView;
import com.chengwenfang.skill.facade.enums.HotNewsRadarCollectNodeType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 关闭采集 run 并清理内存会话中的原文。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeRadarNodeHandler implements RadarCollectNodeHandler {
  private final HotNewsFacade hotNews;

  @Override
  public HotNewsRadarCollectNodeType supports() {
    return HotNewsRadarCollectNodeType.FINALIZE;
  }

  @Override
  public void execute(
      RadarCollectSessionStore.Session session,
      Map<String, Object> lightState,
      List<SkillPromptBindingView> skillBindings,
      String nodeKey,
      String nodeName) {
    int total = session.getCandidates().size();
    int inserted = session.getInsertedCount();
    int discarded = total - session.survivors().size();
    for (RadarCollectCandidate candidate : session.getCandidates()) {
      if (candidate != null) {
        candidate.setRawText(null);
      }
    }
    hotNews.finishRun(
        session.getFetchRunId(),
        total,
        inserted,
        discarded,
        List.of(),
        null);
    lightState.put(RadarCollectGraphStateKeys.CANDIDATE_COUNT, total);
    lightState.put(RadarCollectGraphStateKeys.INSERTED_COUNT, inserted);
    lightState.put(RadarCollectGraphStateKeys.TOPIC_COUNT, session.getTopicCount());
    lightState.put(RadarCollectGraphStateKeys.TOP10_COUNT, session.getTop10Count());
    log.info("雷达采集收尾完成 total={} inserted={} topics={} top10={}",
        total, inserted, session.getTopicCount(), session.getTop10Count());
  }
}
