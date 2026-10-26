package com.workbench.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.workbench.common.ThemeTitleRelevance;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 按 Job 存放雷达采集会话（含内存候选与原文），Job 结束后清理。
 */
@Component
public class RadarCollectSessionStore {
  /** jobId → 会话。 */
  private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

  /**
   * 获取或创建会话。
   *
   * @param jobId Job ID
   * @return 会话
   */
  public Session getOrCreate(String jobId) {
    if (StrUtil.isBlank(jobId)) {
      throw new IllegalArgumentException("jobId 不能为空");
    }
    return sessions.computeIfAbsent(jobId, ignored -> new Session());
  }

  /**
   * 读取会话；不存在则 null。
   *
   * @param jobId Job ID
   * @return 会话或 null
   */
  public Session get(String jobId) {
    if (StrUtil.isBlank(jobId)) {
      return null;
    }
    return sessions.get(jobId);
  }

  /**
   * 清理会话并丢弃原文。
   *
   * @param jobId Job ID
   */
  public void clear(String jobId) {
    if (StrUtil.isBlank(jobId)) {
      return;
    }
    Session removed = sessions.remove(jobId);
    if (removed != null) {
      removed.candidates.clear();
    }
  }

  /** 单次采集会话。 */
  public static final class Session {
    /** 主题 ID。 */
    private String themeId;
    /** 主题相关性信号（标题/速览过滤用）。 */
    private ThemeTitleRelevance.Profile themeRelevanceProfile =
        new ThemeTitleRelevance.Profile("", List.of(), List.of());
    /** 采集 run ID。 */
    private String fetchRunId;
    /** 批次 ID。 */
    private String radarBatchId;
    /** 文章素材配额。 */
    private int maxArticlePerFetch = RadarCollectQuotaDefaults.MAX_ARTICLE_PER_FETCH;
    /** 视频素材配额。 */
    private int maxVideoPerFetch = RadarCollectQuotaDefaults.MAX_VIDEO_PER_FETCH;
    /** 回溯小时。 */
    private int hours = 24;
    /** 渠道 ID。 */
    private final List<String> channelIds = new ArrayList<>();
    /** 内存候选。 */
    private final List<RadarCollectCandidate> candidates = new ArrayList<>();
    /** Skill 摘要。 */
    private String skillSnapshotSummary;
    /** 落库新增。 */
    private int insertedCount;
    /** 选题数。 */
    private int topicCount;
    /** Top10 数。 */
    private int top10Count;

    public String getThemeId() {
      return themeId;
    }

    public void setThemeId(String themeId) {
      this.themeId = themeId;
    }

    public ThemeTitleRelevance.Profile getThemeRelevanceProfile() {
      return themeRelevanceProfile;
    }

    public void setThemeRelevanceProfile(ThemeTitleRelevance.Profile themeRelevanceProfile) {
      this.themeRelevanceProfile = themeRelevanceProfile == null
          ? new ThemeTitleRelevance.Profile("", List.of(), List.of())
          : themeRelevanceProfile.normalized();
    }

    public String getFetchRunId() {
      return fetchRunId;
    }

    public void setFetchRunId(String fetchRunId) {
      this.fetchRunId = fetchRunId;
    }

    public String getRadarBatchId() {
      return radarBatchId;
    }

    public void setRadarBatchId(String radarBatchId) {
      this.radarBatchId = radarBatchId;
    }

    public int getMaxArticlePerFetch() {
      return maxArticlePerFetch;
    }

    public void setMaxArticlePerFetch(int maxArticlePerFetch) {
      this.maxArticlePerFetch = maxArticlePerFetch;
    }

    public int getMaxVideoPerFetch() {
      return maxVideoPerFetch;
    }

    public void setMaxVideoPerFetch(int maxVideoPerFetch) {
      this.maxVideoPerFetch = maxVideoPerFetch;
    }

    public int getHours() {
      return hours;
    }

    public void setHours(int hours) {
      this.hours = hours;
    }

    public List<String> getChannelIds() {
      return channelIds;
    }

    public List<RadarCollectCandidate> getCandidates() {
      return candidates;
    }

    public String getSkillSnapshotSummary() {
      return skillSnapshotSummary;
    }

    public void setSkillSnapshotSummary(String skillSnapshotSummary) {
      this.skillSnapshotSummary = skillSnapshotSummary;
    }

    public int getInsertedCount() {
      return insertedCount;
    }

    public void setInsertedCount(int insertedCount) {
      this.insertedCount = insertedCount;
    }

    public int getTopicCount() {
      return topicCount;
    }

    public void setTopicCount(int topicCount) {
      this.topicCount = topicCount;
    }

    public int getTop10Count() {
      return top10Count;
    }

    public void setTop10Count(int top10Count) {
      this.top10Count = top10Count;
    }

    /**
     * 未弃用候选。
     *
     * @return 幸存者列表
     */
    public List<RadarCollectCandidate> survivors() {
      List<RadarCollectCandidate> out = new ArrayList<>();
      for (RadarCollectCandidate candidate : candidates) {
        if (candidate != null && !candidate.isDiscarded()) {
          out.add(candidate);
        }
      }
      return out;
    }
  }
}
