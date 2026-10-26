package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.mode.hotnews.domain.TopicResearchPlanner;
import com.chengwenfang.mode.hotnews.domain.entity.TopicBriefEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsBriefStatuses;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefSourceRow;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsTopicSourceView;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicBriefMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点选题简报：创建/确认/列表与来源快照组装。
 * <p>由 {@link HotNewsTopicService} 薄委托；选题加载与来源列表经 {@link HotNewsTopicService}。
 */
@Slf4j
@Service
public class HotNewsTopicBriefService {

  /** 证据链角色：佐证。 */
  private static final String ROLE_EVIDENCE = "evidence";

  /** 简报文章类型：热点。 */
  private static final String ARTICLE_TYPE_HOT_NEWS = "hot_news";

  private final TopicBriefMapper briefMapper;
  private final BriefStructuredStore briefStore;
  private final HotNewsResearchService research;
  private final HotNewsTopicService topics;

  /**
   * @param briefMapper 简报 Mapper
   * @param briefStore  简报结构化存储
   * @param research    资料池（@Lazy 破环）
   * @param topics      选题查询（@Lazy 破环）
   */
  public HotNewsTopicBriefService(
      TopicBriefMapper briefMapper,
      BriefStructuredStore briefStore,
      @Lazy HotNewsResearchService research,
      @Lazy HotNewsTopicService topics) {
    this.briefMapper = briefMapper;
    this.briefStore = briefStore;
    this.research = research;
    this.topics = topics;
  }

  /**
   * 列出选题下的简报，按更新时间倒序。
   *
   * @param topicId 选题主键
   * @return 简报视图列表
   */
  public List<HotNewsBriefView> listBriefs(String topicId) {
    return briefMapper.selectList(new LambdaQueryWrapper<TopicBriefEntity>()
            .eq(TopicBriefEntity::getTopicId, topicId)
            .orderByDesc(TopicBriefEntity::getUpdatedAt))
        .stream()
        .map(this::briefView)
        .toList();
  }

  /**
   * 基于选题与当前来源创建一份 draft 状态简报。
   *
   * @param topicId 选题主键
   * @return 新建简报视图
   * @throws BusinessException 选题不存在时（TOPIC_NOT_FOUND / 404）
   */
  @Transactional
  public HotNewsBriefView createBrief(String topicId) {
    TopicClusterEntity topic = topics.requireTopic(topicId);
    LocalDateTime now = LocalDateTime.now();
    TopicBriefEntity brief = new TopicBriefEntity();
    brief.setId(IdsUtil.newId());
    brief.setTopicId(topicId);
    brief.setThemeId(topic.getThemeId());
    brief.setStatus(HotNewsBriefStatuses.DRAFT);
    brief.setTitle(topic.getTitle());
    // 优先资料池；无资料时回退选题原始来源
    List<HotNewsResearchItemView> poolItems = research.listResearchItems(topicId);
    Map<String, Object> summary = briefSummaryFromSkeleton(topic, null);
    List<HotNewsBriefSourceRow> sources = briefSourceSnapshotFromPool(topicId, poolItems);
    brief.setCreatedAt(now);
    brief.setUpdatedAt(now);
    briefMapper.insert(brief);
    briefStore.writeChildren(brief, summary, sources);
    briefMapper.updateById(brief);
    return briefView(brief);
  }

  /**
   * 确认简报：状态置 confirmed；可选把确认备注写入 summaryJson。
   *
   * @param briefId 简报主键
   * @param note    确认备注；非空时写入 summary.confirmationNote
   * @return 确认后的简报视图
   * @throws BusinessException 简报不存在时（BRIEF_NOT_FOUND / 404）
   */
  @Transactional
  public HotNewsBriefView confirmBrief(String briefId, String note) {
    TopicBriefEntity brief = briefMapper.selectById(briefId);
    if (brief == null) {
      throw new BusinessException(ErrorCodes.BRIEF_NOT_FOUND, "选题简报不存在", 404);
    }
    LocalDateTime now = LocalDateTime.now();
    brief.setStatus(HotNewsBriefStatuses.CONFIRMED);
    brief.setConfirmedAt(now);
    brief.setUpdatedAt(now);
    if (StrUtil.isNotBlank(note)) {
      brief.setConfirmationNote(note);
    }
    briefMapper.updateById(brief);
    return briefView(brief);
  }

  /**
   * 由选题与可选骨架拼出简报 summary 结构。
   *
   * @param topic    选题簇
   * @param skeleton 文章骨架；可为 null
   * @return summary Map
   */
  public Map<String, Object> briefSummaryFromSkeleton(
      TopicClusterEntity topic,
      HotNewsArticleSkeletonView skeleton) {
    Map<String, Object> summary = new LinkedHashMap<>();
    String overview = skeleton == null
        ? ""
        : StrUtil.firstNonBlank(
            skeleton.getScope(),
            skeleton.getCoreThesis(),
            skeleton.getCoreEvent(),
            topic.getSummary());
    if (StrUtil.isBlank(overview)) {
      overview = StrUtil.nullToEmpty(topic.getSummary());
    }
    summary.put("overview", overview);
    summary.put("coreThesis", skeleton == null ? "" : StrUtil.nullToEmpty(skeleton.getCoreThesis()));
    summary.put("coreEvent", skeleton == null ? "" : StrUtil.nullToEmpty(skeleton.getCoreEvent()));
    summary.put(
        "confirmedFacts",
        skeleton == null || skeleton.getKeyInsights() == null ? List.of() : skeleton.getKeyInsights());
    summary.put(
        "readerImpact",
        skeleton == null || skeleton.getImpacts() == null ? List.of() : skeleton.getImpacts());
    summary.put(
        "recommendedAngles",
        skeleton == null || skeleton.getReferenceHints() == null
            ? List.of()
            : skeleton.getReferenceHints());
    summary.put("unknowns", List.of());
    if (skeleton != null && StrUtil.isNotBlank(skeleton.getNarrativeShape())) {
      summary.put("narrativeShape", skeleton.getNarrativeShape());
    }
    return summary;
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
    List<HotNewsBriefSourceRow> snapshot = new ArrayList<>();
    Set<String> seenUrls = new LinkedHashSet<>();
    List<HotNewsResearchItemView> refs =
        HotNewsTopicResearchAssistService.filterArticleReferences(researchItems);
    for (HotNewsResearchItemView item : refs) {
      if (item == null) {
        continue;
      }
      String url = StrUtil.nullToEmpty(item.getUrl());
      String urlKey = TopicResearchPlanner.normalizeUrl(url);
      if (StrUtil.isNotBlank(urlKey) && !seenUrls.add(urlKey)) {
        continue;
      }
      snapshot.add(toBriefSourceRow(
          item.getId(),
          topicId,
          item.getId(),
          StrUtil.blankToDefault(item.getTitle(), url),
          url,
          StrUtil.blankToDefault(item.getSourceLabel(), "公开资料"),
          StrUtil.nullToEmpty(item.getSummary()),
          ROLE_EVIDENCE));
    }
    // 资料池为空时回退选题原始来源
    if (snapshot.isEmpty()) {
      for (HotNewsTopicSourceView source : topics.listTopicSources(topicId)) {
        String url = StrUtil.nullToEmpty(source.getUrl());
        String urlKey = TopicResearchPlanner.normalizeUrl(url);
        if (StrUtil.isNotBlank(urlKey) && !seenUrls.add(urlKey)) {
          continue;
        }
        snapshot.add(toBriefSourceRow(
            source.getId(),
            topicId,
            source.getSourceItemId(),
            StrUtil.blankToDefault(source.getTitle(), url),
            url,
            StrUtil.blankToDefault(source.getSourceName(), "选题来源"),
            "",
            StrUtil.blankToDefault(source.getRole(), ROLE_EVIDENCE)));
      }
    }
    return snapshot;
  }

  /**
   * 组装单条简报/审核用的来源快照行。
   *
   * @param id           行 ID
   * @param topicId      选题 ID
   * @param sourceItemId 来源条目 ID
   * @param title        标题
   * @param url          链接
   * @param sourceName   来源名
   * @param summary      摘要
   * @param role         角色
   * @return 快照行
   */
  private static HotNewsBriefSourceRow toBriefSourceRow(
      String id,
      String topicId,
      String sourceItemId,
      String title,
      String url,
      String sourceName,
      String summary,
      String role) {
    return HotNewsBriefSourceRow.builder()
        .id(id)
        .topicId(topicId)
        .sourceItemId(sourceItemId)
        .role(role)
        .title(title)
        .name(sourceName)
        .sourceName(sourceName)
        .sourceTitle(title)
        .url(url)
        .sourceUrl(url)
        .summary(summary)
        .build();
  }

  /**
   * 将简报实体转为对外视图；summary / sourceSnapshot 从结构化子表组装。
   *
   * @param brief 简报实体
   * @return 简报视图
   */
  private HotNewsBriefView briefView(TopicBriefEntity brief) {
    Map<String, Object> summary = briefStore.readSummary(brief);
    return HotNewsBriefView.builder()
        .id(brief.getId())
        .topicId(brief.getTopicId())
        .status(brief.getStatus())
        .title(brief.getTitle())
        .articleType(ARTICLE_TYPE_HOT_NEWS)
        .targetAudience("")
        .angle("")
        .summary(summary)
        .sourceSnapshot(briefStore.readSources(brief.getId()))
        // 顶栏确认备注保持 null；备注在 summary.confirmationNote
        .confirmationNote(null)
        .createdAt(brief.getCreatedAt())
        .updatedAt(brief.getUpdatedAt())
        .confirmedAt(brief.getConfirmedAt())
        .build();
  }
}
