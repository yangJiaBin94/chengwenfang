package com.chengwenfang.mode.hotnews.application.hotnews;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.chengwenfang.knowledge.facade.KnowledgeFacade;
import com.chengwenfang.knowledge.facade.dto.request.RegisterKnowledgeDocumentRequest;
import com.chengwenfang.knowledge.facade.enums.KnowledgeMediaKind;
import com.chengwenfang.knowledge.facade.enums.KnowledgeSourceKind;
import com.chengwenfang.mode.hotnews.domain.TopicMathUtil;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.chengwenfang.mode.hotnews.facade.HotNewsFacade;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import com.chengwenfang.sourcing.facade.enums.VideoParseStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 热点素材登记知识库：雷达 Top10、已确认选题、资料池条目。
 * <p>只增改不级联删除；资料池整池替换删库项时知识库历史保留。
 */
@Slf4j
@Service
public class HotNewsKnowledgeIngestService {
  /** 知识库门面。 */
  private final KnowledgeFacade knowledgeFacade;
  /** 热点门面（选题/信源只读；懒加载打断与选题/调研门面环）。 */
  private final HotNewsFacade hotNews;
  /** 选题簇 Mapper。 */
  private final TopicClusterMapper topicMapper;
  /** 资料池 Mapper。 */
  private final TopicResearchItemMapper researchItemMapper;

  /**
   * @param knowledgeFacade   知识库门面
   * @param hotNews           热点门面（懒加载）
   * @param topicMapper       选题 Mapper
   * @param researchItemMapper 资料池 Mapper
   */
  public HotNewsKnowledgeIngestService(
      KnowledgeFacade knowledgeFacade,
      @Lazy HotNewsFacade hotNews,
      TopicClusterMapper topicMapper,
      TopicResearchItemMapper researchItemMapper) {
    this.knowledgeFacade = knowledgeFacade;
    this.hotNews = hotNews;
    this.topicMapper = topicMapper;
    this.researchItemMapper = researchItemMapper;
  }

  /**
   * 将本批次 radar_rank 1..10 的选题 upsert 到知识库。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 雷达批次 ID
   * @return 成功登记条数
   */
  public int ingestTop10(String themeId, String radarBatchId) {
    if (StrUtil.isBlank(themeId) || StrUtil.isBlank(radarBatchId)) {
      return 0;
    }
    List<HotNewsTopicView> topics = hotNews.listRadarBatchTopics(themeId, radarBatchId);
    List<HotNewsTopicView> top10 = selectTop10(topics);
    int written = 0;
    for (HotNewsTopicView topic : top10) {
      if (ingestRadarTopic(themeId, radarBatchId, topic)) {
        written++;
      }
    }
    log.info(
        "雷达 Top10 已登记知识库 themeId={} batch={} candidates={} written={}",
        themeId,
        radarBatchId,
        top10.size(),
        written);
    return written;
  }

  /**
   * 选题首次确认：登记选题本身、成员信源摘要，以及当前资料池条目。
   *
   * @param topicId 选题 ID
   * @return 成功登记条数（选题计 1 + 资料条目）
   */
  public int ingestOnTopicConfirmed(String topicId) {
    if (StrUtil.isBlank(topicId)) {
      return 0;
    }
    TopicClusterEntity cluster = topicMapper.selectById(topicId);
    if (cluster == null || StrUtil.isBlank(cluster.getThemeId())) {
      return 0;
    }
    HotNewsTopicView topic = hotNews.getTopic(topicId);
    int written = 0;
    if (ingestRadarTopic(cluster.getThemeId(), cluster.getRadarBatchId(), topic)) {
      written++;
    }
    List<TopicResearchItemEntity> items = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .eq(TopicResearchItemEntity::getTopicId, topicId));
    for (TopicResearchItemEntity item : items) {
      if (ingestResearchItem(cluster.getThemeId(), item)) {
        written++;
      }
    }
    log.info("已确认选题素材已登记知识库 topicId={} written={}", topicId, written);
    return written;
  }

  /**
   * 批量登记资料池条目（补充/手动添加后调用；失败单条跳过）。
   *
   * @param themeId 主题 ID
   * @param items   刚写入的资料条目
   * @return 成功条数
   */
  public int ingestResearchItems(String themeId, List<TopicResearchItemEntity> items) {
    if (StrUtil.isBlank(themeId) || CollUtil.isEmpty(items)) {
      return 0;
    }
    int written = 0;
    for (TopicResearchItemEntity item : items) {
      if (ingestResearchItem(themeId, item)) {
        written++;
      }
    }
    return written;
  }

  /**
   * 按条目 ID 批量登记（事务外调用，避免拉长资料池事务）。
   *
   * @param themeId 主题 ID
   * @param itemIds 资料条目 ID
   * @return 成功条数
   */
  public int ingestResearchItemIds(String themeId, List<String> itemIds) {
    if (StrUtil.isBlank(themeId) || CollUtil.isEmpty(itemIds)) {
      return 0;
    }
    List<TopicResearchItemEntity> items = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .in(TopicResearchItemEntity::getId, itemIds));
    return ingestResearchItems(themeId, items);
  }

  /**
   * 事务提交后再登记资料池条目；无活跃事务时立即执行。
   *
   * @param themeId 主题 ID
   * @param itemIds 资料条目 ID
   */
  public void scheduleIngestResearchItemIds(String themeId, List<String> itemIds) {
    if (StrUtil.isBlank(themeId) || CollUtil.isEmpty(itemIds)) {
      return;
    }
    List<String> snapshot = List.copyOf(itemIds);
    runAfterCommit(() -> ingestResearchItemIds(themeId, snapshot));
  }

  /**
   * 事务提交后再按最新摘要刷新知识库；无事务时立即执行。
   *
   * @param itemId 资料条目 ID
   */
  public void scheduleRefreshResearchItemKnowledge(String itemId) {
    if (StrUtil.isBlank(itemId)) {
      return;
    }
    String snapshotId = itemId;
    runAfterCommit(() -> {
      TopicResearchItemEntity item = researchItemMapper.selectById(snapshotId);
      refreshResearchItemKnowledge(item);
    });
  }

  /**
   * 视频解析成功后按最新摘要刷新知识库（失败跳过）。
   *
   * @param item 资料池条目（须已带最新 summary / status）
   */
  public void refreshResearchItemKnowledge(TopicResearchItemEntity item) {
    if (item == null || StrUtil.isBlank(item.getId()) || StrUtil.isBlank(item.getTopicId())) {
      return;
    }
    if (!VideoParseStatus.SUCCEEDED.getValue().equals(item.getVideoParseStatus())) {
      return;
    }
    if (StrUtil.isBlank(item.getSummary())) {
      return;
    }
    TopicClusterEntity topic = topicMapper.selectById(item.getTopicId());
    if (topic == null || StrUtil.isBlank(topic.getThemeId())) {
      return;
    }
    ingestResearchItem(topic.getThemeId(), item);
  }

  /**
   * 有活跃事务则提交后执行，否则立即执行。
   *
   * @param task 任务
   */
  private static void runAfterCommit(Runnable task) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      task.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        task.run();
      }
    });
  }

  /**
   * 选出本批次最终 Top10。
   *
   * @param topics 批次选题
   * @return 至多 {@link TopicMathUtil#DAILY_TOPIC_LIMIT} 条
   */
  static List<HotNewsTopicView> selectTop10(List<HotNewsTopicView> topics) {
    if (CollUtil.isEmpty(topics)) {
      return List.of();
    }
    List<HotNewsTopicView> ranked = new ArrayList<>();
    for (HotNewsTopicView topic : topics) {
      if (topic == null || StrUtil.isBlank(topic.getId())) {
        continue;
      }
      Integer rank = topic.getRadarRank();
      if (rank != null && rank >= 1 && rank <= TopicMathUtil.DAILY_TOPIC_LIMIT) {
        ranked.add(topic);
      }
    }
    if (!ranked.isEmpty()) {
      ranked.sort(Comparator
          .comparingInt((HotNewsTopicView topic) ->
              topic.getRadarRank() == null ? Integer.MAX_VALUE : topic.getRadarRank())
          .thenComparing(HotNewsTopicView::getId, Comparator.nullsLast(String::compareTo)));
      return ranked;
    }
    List<HotNewsTopicView> byScore = new ArrayList<>(topics);
    byScore.removeIf(topic -> topic == null || StrUtil.isBlank(topic.getId()));
    byScore.sort(Comparator
        .comparingInt((HotNewsTopicView topic) ->
            topic.getWritingScore() == null ? 0 : topic.getWritingScore())
        .reversed()
        .thenComparing(HotNewsTopicView::getId, Comparator.nullsLast(String::compareTo)));
    if (byScore.size() > TopicMathUtil.DAILY_TOPIC_LIMIT) {
      return new ArrayList<>(byScore.subList(0, TopicMathUtil.DAILY_TOPIC_LIMIT));
    }
    return byScore;
  }

  /**
   * 组装雷达选题可检索正文。
   *
   * @param topic   选题
   * @param sources 成员信源
   * @return 正文
   */
  static String buildTopicRawText(HotNewsTopicView topic, List<HotNewsTopicSourceView> sources) {
    if (topic == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    String title = StrUtil.trim(StrUtil.nullToEmpty(topic.getTitle()));
    if (StrUtil.isNotBlank(title)) {
      builder.append("# ").append(title).append('\n');
    }
    String summary = StrUtil.trim(StrUtil.nullToEmpty(topic.getSummary()));
    if (StrUtil.isNotBlank(summary)) {
      builder.append(summary).append('\n');
    }
    if (CollUtil.isNotEmpty(sources)) {
      builder.append("\n## 信源\n");
      for (HotNewsTopicSourceView source : sources) {
        if (source == null) {
          continue;
        }
        String sourceTitle = StrUtil.trim(StrUtil.nullToEmpty(source.getTitle()));
        String sourceSummary = StrUtil.trim(StrUtil.nullToEmpty(source.getSummary()));
        String url = StrUtil.trim(StrUtil.nullToEmpty(source.getUrl()));
        if (StrUtil.isBlank(sourceTitle) && StrUtil.isBlank(sourceSummary) && StrUtil.isBlank(url)) {
          continue;
        }
        builder.append("- ");
        builder.append(StrUtil.blankToDefault(sourceTitle, "未命名来源"));
        if (StrUtil.isNotBlank(url)) {
          builder.append("\n  ").append(url);
        }
        if (StrUtil.isNotBlank(sourceSummary)) {
          builder.append("\n  ").append(sourceSummary);
        }
        builder.append('\n');
      }
    }
    return builder.toString().trim();
  }

  /**
   * 组装资料池条目正文。
   *
   * @param item 资料条目
   * @return 正文
   */
  static String buildResearchRawText(TopicResearchItemEntity item) {
    if (item == null) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    String title = StrUtil.trim(StrUtil.nullToEmpty(item.getTitle()));
    if (StrUtil.isNotBlank(title)) {
      builder.append("# ").append(title).append('\n');
    }
    String url = StrUtil.trim(StrUtil.nullToEmpty(item.getUrl()));
    if (StrUtil.isNotBlank(url)) {
      builder.append(url).append('\n');
    }
    String summary = StrUtil.trim(StrUtil.nullToEmpty(item.getSummary()));
    if (StrUtil.isNotBlank(summary)) {
      builder.append(summary).append('\n');
    }
    return builder.toString().trim();
  }

  private boolean ingestRadarTopic(String themeId, String radarBatchId, HotNewsTopicView topic) {
    if (topic == null || StrUtil.isBlank(topic.getId()) || StrUtil.isBlank(themeId)) {
      return false;
    }
    try {
      List<HotNewsTopicSourceView> sources = hotNews.listTopicSources(topic.getId());
      String rawText = buildTopicRawText(topic, sources);
      if (StrUtil.isBlank(rawText) || rawText.length() < 8) {
        log.info("热点选题跳过空正文 topicId={}", topic.getId());
        return false;
      }
      knowledgeFacade.upsertSourceDocument(RegisterKnowledgeDocumentRequest.builder()
          .sourceKind(KnowledgeSourceKind.RADAR_TOPIC)
          .sourceRefId(topic.getId())
          .mediaKind(KnowledgeMediaKind.PLAIN_TEXT)
          .title(StrUtil.blankToDefault(topic.getTitle(), topic.getId()))
          .themeId(themeId)
          .rawText(rawText)
          .deepLink(firstSourceUrl(sources))
          .contentHash(DigestUtil.sha256Hex(rawText))
          .sourceVersionStamp(StrUtil.blankToDefault(radarBatchId, topic.getId()))
          .build());
      return true;
    } catch (Exception exception) {
      log.warn("热点选题入库知识库失败 topicId={} err={}", topic.getId(), exception.getMessage());
      return false;
    }
  }

  private boolean ingestResearchItem(String themeId, TopicResearchItemEntity item) {
    if (item == null || StrUtil.isBlank(item.getId()) || StrUtil.isBlank(themeId)) {
      return false;
    }
    try {
      String rawText = buildResearchRawText(item);
      if (StrUtil.isBlank(rawText) || rawText.length() < 8) {
        log.info("资料池条目跳过空正文 itemId={}", item.getId());
        return false;
      }
      String deepLink = StrUtil.trim(StrUtil.nullToEmpty(item.getUrl()));
      knowledgeFacade.upsertSourceDocument(RegisterKnowledgeDocumentRequest.builder()
          .sourceKind(KnowledgeSourceKind.RESEARCH_ITEM)
          .sourceRefId(item.getId())
          .mediaKind(KnowledgeMediaKind.PLAIN_TEXT)
          .title(StrUtil.blankToDefault(item.getTitle(), item.getId()))
          .themeId(themeId)
          .rawText(rawText)
          .deepLink(StrUtil.blankToDefault(deepLink, null))
          .contentHash(DigestUtil.sha256Hex(rawText))
          .sourceVersionStamp(StrUtil.blankToDefault(item.getRunId(), item.getId()))
          .build());
      return true;
    } catch (Exception exception) {
      log.warn("资料池条目入库知识库失败 itemId={} err={}", item.getId(), exception.getMessage());
      return false;
    }
  }

  private static String firstSourceUrl(List<HotNewsTopicSourceView> sources) {
    if (CollUtil.isEmpty(sources)) {
      return null;
    }
    for (HotNewsTopicSourceView source : sources) {
      if (source != null && StrUtil.isNotBlank(source.getUrl())) {
        return source.getUrl().trim();
      }
    }
    return null;
  }
}
