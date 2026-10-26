package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicSourceEntity;
import com.chengwenfang.sourcing.domain.entity.SourceChannelEntity;
import com.chengwenfang.sourcing.domain.entity.SourceItemEntity;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefSourceRow;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsEvidenceView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.sourcing.facade.enums.SourceLifecycleStatus;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceChannelMapper;
import com.chengwenfang.sourcing.infrastructure.mapper.SourceItemMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicSourceMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 选题来源、证据、简报与聚合/中文提炼委托。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopicBriefSourceService {

  /** 证据链角色：佐证。 */
  private static final String ROLE_EVIDENCE = "evidence";

  /** 结构化证据缺省状态。 */
  private static final String EVIDENCE_STATUS_AUTO = "auto";

  /** 结构化证据缺省审核人。 */
  private static final String EVIDENCE_REVIEWER_SYSTEM = "system";

  /** 选题-信源关联 Mapper。 */
  private final TopicSourceMapper topicSourceMapper;
  /** 信源条目 Mapper。 */
  private final SourceItemMapper itemMapper;
  /** 频道 Mapper。 */
  private final SourceChannelMapper channelMapper;
  /** 选题聚合能力。 */
  private final HotNewsTopicAggregateService aggregate;
  /** 选题简报能力。 */
  private final HotNewsTopicBriefService briefs;
  /** 中文提炼能力。 */
  private final HotNewsTopicEditorialService editorial;

  public List<HotNewsTopicSourceView> listTopicSources(String topicId) {
    // 按关联创建顺序展开来源
    List<TopicSourceEntity> links = topicSourceMapper.selectList(new LambdaQueryWrapper<TopicSourceEntity>()
        .eq(TopicSourceEntity::getTopicId, topicId)
        .orderByAsc(TopicSourceEntity::getCreatedAt));
    List<HotNewsTopicSourceView> out = new ArrayList<>();
    for (TopicSourceEntity link : links) {
      SourceItemEntity item = itemMapper.selectById(link.getSourceItemId());
      // 条目已删则跳过，避免脏关联
      if (item == null) {
        continue;
      }
      // 弃用或未就绪视频不进入选题来源/证据兜底
      if (SourceLifecycleStatus.from(item.getLifecycleStatus()).shouldSkipAggregate()) {
        continue;
      }
      SourceChannelEntity channel = channelMapper.selectById(item.getChannelId());
      // 展示标签以 URL 为准，避免存量回填的「未知来源/文章」盖住真实站点
      out.add(HotNewsTopicSourceView.builder()
          .id(link.getId())
          .topicId(link.getTopicId())
          .sourceItemId(link.getSourceItemId())
          .role(link.getRole())
          .title(item.getTitle())
          .summary(StrUtil.nullToEmpty(item.getSummary()))
          .url(item.getUrl())
          .sourceName(channel == null ? "" : channel.getName())
          .siteLabel(HotNewsCodec.resolveSiteLabel(item.getUrl(), item.getSiteLabel()))
          .mediaType(HotNewsCodec.resolveMediaType(item.getUrl(), item.getTitle(), item.getMediaType()))
          .lifecycleStatus(SourceLifecycleStatus.from(item.getLifecycleStatus()).getValue())
          .publishedAt(item.getPublishedAt())
          .build());
    }
    return out;
  }

  /**
   * 列出选题证据；结构化证据表已移除，用来源列表映射伪证据。
   *
   * @param topicId 选题主键
   * @return 证据视图列表（source 回退；无来源时为空）
   */
  public List<HotNewsEvidenceView> listEvidence(String topicId) {
    List<HotNewsEvidenceView> fallback = new ArrayList<>();
    for (HotNewsTopicSourceView source : listTopicSources(topicId)) {
      String factBrief = StrUtil.nullToEmpty(source.getSummary()).trim();
      fallback.add(HotNewsEvidenceView.builder()
          .id(source.getId())
          .topicId(topicId)
          .statement(source.getTitle())
          .evidenceType("source")
          .sourceItemId(source.getSourceItemId())
          .sourceTitle(source.getTitle())
          .sourceUrl(source.getUrl())
          .sourceName(source.getSourceName())
          .siteLabel(source.getSiteLabel())
          .mediaType(source.getMediaType())
          .status(EVIDENCE_STATUS_AUTO)
          .reviewer(EVIDENCE_REVIEWER_SYSTEM)
          .note(factBrief)
          .title(source.getTitle())
          .url(source.getUrl())
          .snippet(factBrief)
          .role(source.getRole() == null ? ROLE_EVIDENCE : source.getRole())
          .createdAt(LocalDateTime.now())
          .build());
    }
    return fallback;
  }

  /**
   * 列出选题下的简报，按更新时间倒序。
   *
   * @param topicId 选题主键
   * @return 简报视图列表
   */
  public List<HotNewsBriefView> listBriefs(String topicId) {
    return briefs.listBriefs(topicId);
  }

  /**
   * 基于选题与当前来源创建一份 draft 状态简报。
   *
   * @param topicId 选题主键
   * @return 新建简报视图
   */
  @Transactional
  public HotNewsBriefView createBrief(String topicId) {
    return briefs.createBrief(topicId);
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
    return briefs.confirmBrief(briefId, note);
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
    return briefs.briefSummaryFromSkeleton(topic, skeleton);
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
    return briefs.briefSourceSnapshotFromPool(topicId, researchItems);
  }

  /**
   * 将近 7 日信源按标题相似度并入选题簇。
   *
   * @param themeId 主题 ID
   * @return 参与聚合的簇数量
   */
  public int aggregateTopics(String themeId) {
    return aggregate.aggregateTopics(themeId);
  }

  /**
   * 将英文选题提炼为中文标题与事实速览。
   *
   * @param themeId 主题 ID
   * @return 成功写入速览的条数
   */
  public int editorializeTopics(String themeId) {
    return editorial.editorializeTopics(themeId);
  }

  public void fillClusterSummaryFromMemberBriefs(
      TopicClusterEntity cluster, List<TopicSourceEntity> links) {
    aggregate.fillClusterSummaryFromMemberBriefs(cluster, links);
  }


}
