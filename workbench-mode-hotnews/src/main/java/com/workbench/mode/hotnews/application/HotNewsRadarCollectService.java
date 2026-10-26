package com.workbench.mode.hotnews.application;

import com.workbench.job.facade.dto.view.JobView;
import com.workbench.mode.hotnews.domain.entity.TopicClusterEntity;
import com.workbench.mode.hotnews.facade.dto.request.RadarSurvivorPersistRequest;
import com.workbench.mode.hotnews.facade.dto.request.TopicWritingScoreApplyRequest;
import com.workbench.mode.hotnews.facade.dto.view.RadarPersistClusterResult;
import com.workbench.sourcing.facade.dto.request.QuerySiteBlockUpsertRequest;
import com.workbench.sourcing.facade.dto.view.QuerySiteBlockView;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 雷达采集批次门面：落库幸存信源、成簇摘要、Top10 排名与搜索词×网站拉黑。
 * <p>薄门面：对外保持原公开方法签名，内部委派批次/评分/拉黑协作类。
 */
@Service
@RequiredArgsConstructor
public class HotNewsRadarCollectService {

  /** 雷达批次。 */
  private final RadarBatchService batches;
  /** 写作分与 Top10。 */
  private final RadarScoreUpdater scores;
  /** 搜索词×网站拉黑。 */
  private final QuerySiteBlockService querySiteBlocks;

  /**
   * 确保雷达批次行存在（幂等插入）。
   *
   * @param batchId 批次 ID
   * @param themeId 主题 ID
   * @param jobId   触发 Job ID，可空
   */
  @Transactional
  public void ensureRadarBatch(String batchId, String themeId, String jobId) {
    batches.ensureRadarBatch(batchId, themeId, jobId);
  }

  /**
   * 将雷达批次标为已完成。
   *
   * @param batchId 批次 ID
   */
  @Transactional
  public void completeRadarBatch(String batchId) {
    batches.completeRadarBatch(batchId);
  }

  /**
   * 将雷达批次标为失败。
   *
   * @param batchId 批次 ID
   */
  @Transactional
  public void failRadarBatch(String batchId) {
    batches.failRadarBatch(batchId);
  }

  /**
   * 落库雷达幸存者并触发选题聚合，回写批次 ID 与成员速览。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @param survivors    幸存者列表
   * @return 持久化与成簇结果
   */
  @Transactional
  public RadarPersistClusterResult persistAndClusterRadarSurvivors(
      String themeId, String radarBatchId, List<RadarSurvivorPersistRequest> survivors) {
    return batches.persistAndClusterRadarSurvivors(themeId, radarBatchId, survivors);
  }

  /**
   * 写回雷达成簇摘要（简体、截断）。
   *
   * @param summaryByTopicId 选题 ID → 摘要
   */
  @Transactional
  public void updateRadarClusterSummaries(Map<String, String> summaryByTopicId) {
    scores.updateRadarClusterSummaries(summaryByTopicId);
  }

  /**
   * 列出本雷达批次下未过期选题实体（视图映射由门面完成）。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @return 选题实体列表
   */
  public List<TopicClusterEntity> listRadarBatchClusters(String themeId, String radarBatchId) {
    return batches.listRadarBatchClusters(themeId, radarBatchId);
  }

  /**
   * 写回 AI 写作价值评分。
   *
   * @param requests 评分结果
   * @return 成功条数
   */
  @Transactional
  public int applyWritingScores(List<TopicWritingScoreApplyRequest> requests) {
    return scores.applyWritingScores(requests);
  }

  /**
   * 按写作分降序写 radar_rank（不含日限额裁剪，由门面随后调用）。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 批次 ID
   * @return min(日限额, 本批次选题数)
   */
  @Transactional
  public int markRadarTop10Ranks(String themeId, String radarBatchId) {
    return scores.markRadarTop10Ranks(themeId, radarBatchId);
  }

  /**
   * 列出主题下近期抓取运行对应的 Job 视图。
   *
   * @param themeId 主题 ID
   * @return Job 列表（最多 100 条运行）
   */
  public List<JobView> listRadarCollectRuns(String themeId) {
    return batches.listRadarCollectRuns(themeId);
  }

  /**
   * 列出主题下生效中的搜索词 × 网站拉黑。
   *
   * @param themeId 主题 ID
   * @return 拉黑列表
   */
  public List<QuerySiteBlockView> listActiveQuerySiteBlocks(String themeId) {
    return querySiteBlocks.listActiveQuerySiteBlocks(themeId);
  }

  /**
   * 批量 upsert 搜索词 × 网站拉黑。
   *
   * @param requests upsert 请求
   * @return 写入后的视图
   */
  public List<QuerySiteBlockView> upsertQuerySiteBlocks(List<QuerySiteBlockUpsertRequest> requests) {
    return querySiteBlocks.upsertQuerySiteBlocks(requests);
  }
}
