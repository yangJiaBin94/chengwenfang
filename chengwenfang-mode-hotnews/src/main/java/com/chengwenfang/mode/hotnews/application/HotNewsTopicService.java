package com.chengwenfang.mode.hotnews.application;

import com.chengwenfang.common.PageQuery;
import com.chengwenfang.common.ThemeTitleRelevance;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicSourceEntity;
import com.chengwenfang.mode.hotnews.facade.dto.request.AiThemeSeedPersistRequest;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefSourceRow;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsEvidenceView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicPageView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicView;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点选题池：列表分页、状态、雷达筛选、证据与视图组装。
 * <p>薄门面：对外保持原公开方法签名，内部委派查询/状态/种子/来源简报协作类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsTopicService {

  /** 选题查询。 */
  private final TopicQueryService query;
  /** 选题状态。 */
  private final TopicStatusService status;
  /** AI 主题种子。 */
  private final TopicSeedService seed;
  /** 来源/简报/聚合委托。 */
  private final TopicBriefSourceService briefSource;

  /**
   * 列出选题（默认排除 expired）。
   *
   * @param themeId 主题 ID
   * @param limit   条数上限
   * @return 选题视图列表
   */
  public List<HotNewsTopicView> listTopics(String themeId, int limit) {
    return query.listTopics(themeId, limit);
  }

  /**
   * 按状态筛选选题列表；status 为空时排除 expired。按 writingScore、updatedAt 倒序。
   *
   * @param themeId 主题 ID；为 null 时不过滤主题
   * @param status  选题状态；空白则 ne expired，否则精确匹配
   * @param limit   期望条数，实际取 {@code max(1, min(limit, 100))}
   * @return 选题视图列表
   */
  @Transactional
  public List<HotNewsTopicView> listTopics(String themeId, String status, int limit) {
    return query.listTopics(themeId, status, limit);
  }

  /**
   * 分页列出热点雷达选题：先取雷达池（最新批次 TopN 或回退列表），再按筛选/关键字/排序切片。
   *
   * @param themeId   主题 ID
   * @param pageQuery 分页与关键字；null 时按默认页码页大小
   * @param filter    筛选词；空白默认 today
   * @param sortBy    排序词；空白默认 writing
   * @return 当前页与雷达池级候选/总数统计
   */
  @Transactional
  public HotNewsTopicPageView pageTopics(
      String themeId, PageQuery pageQuery, String filter, String sortBy) {
    return query.pageTopics(themeId, pageQuery, filter, sortBy);
  }

  /**
   * 列出已确认选题，按确认时间倒序，不截成当日雷达条数。
   *
   * @param themeId 主题 ID
   * @return 已确认 / 写作中 / 已完成选题
   */
  public List<HotNewsTopicView> listConfirmedTopics(String themeId) {
    return query.listConfirmedTopics(themeId);
  }

  /**
   * 批量汇总已选题资料池与关联信源的去重键，供雷达采集排除重复素材。
   *
   * @param themeId 主题 ID
   * @return 去重键集合；主题空白或无已选题时为空集
   */
  public Set<String> listConfirmedTopicMaterialDedupeKeys(String themeId) {
    return query.listConfirmedTopicMaterialDedupeKeys(themeId);
  }

  /**
   * 获取单个选题详情。
   *
   * @param topicId 选题主键
   * @return 选题视图
   */
  public HotNewsTopicView getTopic(String topicId) {
    return query.getTopic(topicId);
  }

  /**
   * 落库 AI 主题种子选题（问卷生成素材）；同批次同标题幂等。
   *
   * @param themeId      主题 ID
   * @param radarBatchId 雷达批次 ID
   * @param request      种子标题/摘要/信号等
   * @return 选题视图；参数无效时 null
   */
  public HotNewsTopicView persistAiThemeSeedTopic(
      String themeId, String radarBatchId, AiThemeSeedPersistRequest request) {
    return seed.persistAiThemeSeedTopic(themeId, radarBatchId, request);
  }

  /**
   * 更新选题状态；status 空白时保持原状态。
   *
   * @param topicId 选题主键
   * @param status  新状态；null/空白则不改 status 字段
   * @return 更新后的选题视图
   */
  @Transactional
  public HotNewsTopicView setTopicStatus(String topicId, String status) {
    return this.status.setTopicStatus(topicId, status);
  }

  /**
   * 列出选题关联的信源证据：关联表 + 条目 + 频道名称/信任等级。
   *
   * @param topicId 选题主键
   * @return 选题来源视图列表；条目已删的关联会被跳过
   */
  public List<HotNewsTopicSourceView> listTopicSources(String topicId) {
    return briefSource.listTopicSources(topicId);
  }

  /**
   * 列出选题证据；结构化证据表已移除，用来源列表映射伪证据。
   *
   * @param topicId 选题主键
   * @return 证据视图列表（source 回退；无来源时为空）
   */
  public List<HotNewsEvidenceView> listEvidence(String topicId) {
    return briefSource.listEvidence(topicId);
  }

  /**
   * 列出选题下的简报，按更新时间倒序。
   *
   * @param topicId 选题主键
   * @return 简报视图列表
   */
  public List<HotNewsBriefView> listBriefs(String topicId) {
    return briefSource.listBriefs(topicId);
  }

  /**
   * 基于选题与当前来源创建一份 draft 状态简报。
   *
   * @param topicId 选题主键
   * @return 新建简报视图
   */
  @Transactional
  public HotNewsBriefView createBrief(String topicId) {
    return briefSource.createBrief(topicId);
  }

  /**
   * 确认简报：状态置 confirmed；可选把确认备注写入 summaryJson。
   *
   * @param briefId 简报主键
   * @param note    确认备注；非空时写入 summary.confirmationNote
   * @return 确认后的简报视图
   */
  @Transactional
  public HotNewsBriefView confirmBrief(String briefId, String note) {
    return briefSource.confirmBrief(briefId, note);
  }

  /**
   * 由选题与可选骨架组装简报 summary 结构。
   *
   * @param topic    选题实体
   * @param skeleton 文章骨架；可空
   * @return summary Map
   */
  public Map<String, Object> briefSummaryFromSkeleton(
      TopicClusterEntity topic,
      HotNewsArticleSkeletonView skeleton) {
    return briefSource.briefSummaryFromSkeleton(topic, skeleton);
  }

  /**
   * 从来源选题关联与资料池组装简报来源快照（跳过视频）。
   *
   * @param topicId       选题 ID
   * @param researchItems 资料池条目
   * @return 来源快照行
   */
  public List<HotNewsBriefSourceRow> briefSourceSnapshotFromPool(
      String topicId,
      List<HotNewsResearchItemView> researchItems) {
    return briefSource.briefSourceSnapshotFromPool(topicId, researchItems);
  }

  /**
   * 将近 7 日信源按标题相似度并入选题簇。
   *
   * @param themeId 主题 ID
   * @return 参与聚合的簇数量
   */
  public int aggregateTopics(String themeId) {
    return briefSource.aggregateTopics(themeId);
  }

  /**
   * 将英文选题提炼为中文标题与事实速览。
   *
   * @param themeId 主题 ID
   * @return 成功写入速览的条数
   */
  public int editorializeTopics(String themeId) {
    return briefSource.editorializeTopics(themeId);
  }

  /**
   * 按自然日执行每日雷达限额。
   *
   * @param themeId 主题 ID
   */
  public void applyDailyTopicLimit(String themeId) {
    status.applyDailyTopicLimit(themeId);
  }

  /**
   * 当选题簇摘要为空或仅等于标题时，用成员信源事实速览拼成可读速览。
   *
   * @param cluster 选题簇
   * @param links   来源关联
   */
  public void fillClusterSummaryFromMemberBriefs(
      TopicClusterEntity cluster, List<TopicSourceEntity> links) {
    briefSource.fillClusterSummaryFromMemberBriefs(cluster, links);
  }

  /**
   * 按主键加载选题；不存在则抛业务异常。
   *
   * @param topicId 选题主键
   * @return 选题实体
   */
  public TopicClusterEntity requireTopic(String topicId) {
    return query.requireTopic(topicId);
  }

  /**
   * 组装主题标题相关性信号：聚合主题下渠道搜索词（优先当前渠道）。
   *
   * @param themeId   主题 ID
   * @param channelId 当前渠道；可空
   * @return Profile；无查询词时为空信号（过滤放行）
   */
  public ThemeTitleRelevance.Profile themeRelevanceProfile(String themeId, String channelId) {
    return query.themeRelevanceProfile(themeId, channelId);
  }

  /**
   * 选题实体转对外视图（含来源站点/媒体标签与发布时间区间）。
   *
   * @param topic 选题实体
   * @return 选题视图
   */
  public HotNewsTopicView topicView(TopicClusterEntity topic) {
    return query.topicView(topic);
  }
}
