package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsTopicStatuses;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 选题状态：确认链路时间戳、每日雷达限额。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicStatusService {

  /** 选题簇 Mapper。 */
  private final TopicClusterMapper topicMapper;
  /** 选题查询（加载与视图）。 */
  private final TopicQueryService query;
  /** 选题聚合（日限额）。 */
  private final HotNewsTopicAggregateService aggregate;

  /**
   * 更新选题状态；status 空白时保持原状态。
   *
   * @param topicId 选题主键
   * @param status  新状态；null/空白则不改 status 字段
   * @return 更新后的选题视图
   */
  @Transactional
  public HotNewsTopicView setTopicStatus(String topicId, String status) {
    TopicClusterEntity topic = query.requireTopic(topicId);
    LocalDateTime now = LocalDateTime.now();
    // 空白 status 保留原值，避免误清空
    String nextStatus = StrUtil.isBlank(status) ? topic.getStatus() : status;
    topic.setStatus(nextStatus);
    // 首次进入已确认链路时记录确认时间，后续 writing/completed 不覆盖
    if (isConfirmedTopicStatus(nextStatus) && topic.getConfirmedAt() == null) {
      topic.setConfirmedAt(now);
    }
    topic.setUpdatedAt(now);
    topicMapper.updateById(topic);
    return query.topicView(topic);
  }

  /**
   * 按自然日执行每日雷达限额。
   *
   * @param themeId 主题 ID
   */
  public void applyDailyTopicLimit(String themeId) {
    aggregate.applyDailyTopicLimit(themeId);
  }

  /**
   * 是否属于已确认及之后的选题状态。
   *
   * @param status 状态
   * @return 匹配则为 true
   */
  private static boolean isConfirmedTopicStatus(String status) {
    return HotNewsTopicStatuses.isConfirmedLifecycle(status);
  }
}
