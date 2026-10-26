package com.workbench.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workbench.common.BusinessException;
import com.workbench.common.ErrorCodes;
import com.workbench.common.PageQuery;
import com.workbench.mode.hotnews.domain.ChineseTextNormalizer;
import com.workbench.mode.hotnews.domain.ResearchContentClassifier;
import com.workbench.mode.hotnews.domain.TopicResearchPlanner;
import com.workbench.mode.hotnews.domain.entity.TopicResearchConflictEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchItemEntity;
import com.workbench.mode.hotnews.domain.entity.TopicResearchRunEntity;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsResearchConflictView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsResearchItemPageView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsResearchRunView;
import com.workbench.mode.hotnews.facade.enums.ResearchItemKind;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchConflictMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchItemMapper;
import com.workbench.mode.hotnews.infrastructure.mapper.TopicResearchRunMapper;
import com.workbench.sourcing.facade.enums.VideoParseStatus;
import com.workbench.sourcing.facade.enums.VideoParseStep;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 调研查询：运行列表、资料池分页、冲突与视图映射。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchQueryService {

  /** 调研运行 Mapper。 */
  private final TopicResearchRunMapper researchRunMapper;
  /** 资料池条目 Mapper。 */
  private final TopicResearchItemMapper researchItemMapper;
  /** 调研冲突 Mapper。 */
  private final TopicResearchConflictMapper researchConflictMapper;
  /** 解析状态共享表（列表水合）。 */
  private final VideoParseRunService videoParseRuns;
  /** 视频解析协作（原始文本）。 */
  private final HotNewsVideoParseService videoParse;
  /** 资料评分明细。 */
  private final ResearchItemScoreDetailsStore scoreDetailsStore;

  public List<HotNewsResearchRunView> listResearchRuns(String topicId) {
    return researchRunMapper.selectList(new LambdaQueryWrapper<TopicResearchRunEntity>()
            .eq(TopicResearchRunEntity::getTopicId, topicId)
            .orderByDesc(TopicResearchRunEntity::getCreatedAt))
        .stream()
        .map(this::researchRunView)
        .toList();
  }

  /**
   * 按主键读取选题调研运行。
   *
   * @param runId 调研运行 ID
   * @return 运行视图
   * @throws BusinessException 不存在时 404
   */
  public HotNewsResearchRunView getResearchRun(String runId) {
    TopicResearchRunEntity run = researchRunMapper.selectById(runId);
    if (run == null) {
      throw new BusinessException(ErrorCodes.RESEARCH_RUN_NOT_FOUND, "调研运行不存在: " + runId, 404);
    }
    return researchRunView(run);
  }

  /**
   * 列出选题下的调研材料条目，按 score、创建时间倒序。
   *
   * @param topicId 选题主键
   * @return 调研条目视图列表
   */
  public List<HotNewsResearchItemView> listResearchItems(String topicId) {
    // 每次补充会清空后重写，资料池即最新一次结果；同 URL 再兜底去重
    List<TopicResearchItemEntity> items = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .eq(TopicResearchItemEntity::getTopicId, topicId)
            .orderByDesc(TopicResearchItemEntity::getScore)
            .orderByDesc(TopicResearchItemEntity::getCreatedAt));
    LinkedHashMap<String, TopicResearchItemEntity> unique = new LinkedHashMap<>();
    for (TopicResearchItemEntity item : items) {
      unique.putIfAbsent(researchItemDedupeKey(item), item);
    }
    List<TopicResearchItemEntity> uniqueItems = new ArrayList<>(unique.values());
    // 批量水合解析状态，避免列表 N+1
    videoParseRuns.applyToResearchItems(uniqueItems);
    return uniqueItems.stream().map(this::toResearchItemView).toList();
  }

  /**
   * 分页列出调研材料：关键字与形态在 SQL 侧筛选，再 LIMIT/OFFSET。
   *
   * @param topicId 选题主键
   * @param query   分页与关键字
   * @param media   all / article / video
   * @return 当前页与选题级统计
   */
  public HotNewsResearchItemPageView pageResearchItems(
      String topicId, PageQuery query, String media) {
    PageQuery normalized = query == null ? new PageQuery().normalized() : query.normalized();
    String keyword = StrUtil.trim(normalized.getKeyword());
    String mediaKey = StrUtil.blankToDefault(media, "all").trim().toLowerCase(Locale.ROOT);

    // 列表筛选条件（影响 total / items）；count 与 page 分拆 wrapper，避免 last 污染
    LambdaQueryWrapper<TopicResearchItemEntity> filterQuery =
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .eq(TopicResearchItemEntity::getTopicId, topicId);
    if (StrUtil.isNotBlank(keyword)) {
      filterQuery.and(wrapper -> wrapper
          .like(TopicResearchItemEntity::getTitle, keyword)
          .or()
          .like(TopicResearchItemEntity::getSummary, keyword)
          .or()
          .like(TopicResearchItemEntity::getUrl, keyword));
    }
    appendResearchMediaSqlFilter(filterQuery, mediaKey);

    long total = researchItemMapper.selectCount(filterQuery);
    List<HotNewsResearchItemView> items = List.of();
    if (total > 0) {
      int offset = normalized.offset();
      // 页码超出时夹到最后一页，避免空页
      if (offset >= total) {
        int lastPage = (int) Math.max(1, (total + normalized.getPageSize() - 1) / normalized.getPageSize());
        normalized = PageQuery.builder()
            .page(lastPage)
            .pageSize(normalized.getPageSize())
            .keyword(normalized.getKeyword())
            .build()
            .normalized();
        offset = normalized.offset();
      }
      LambdaQueryWrapper<TopicResearchItemEntity> pageQuery = filterQuery
          .orderByDesc(TopicResearchItemEntity::getScore)
          .orderByDesc(TopicResearchItemEntity::getCreatedAt)
          .last("LIMIT " + normalized.getPageSize() + " OFFSET " + offset);
      List<TopicResearchItemEntity> pageRows = researchItemMapper.selectList(pageQuery);
      videoParseRuns.applyToResearchItems(pageRows);
      items = pageRows.stream().map(this::toResearchItemView).toList();
    }

    // 选题级统计（不受列表筛选影响）：形态判定 + 共享表解析状态
    List<TopicResearchItemEntity> topicRows = researchItemMapper.selectList(
        new LambdaQueryWrapper<TopicResearchItemEntity>()
            .eq(TopicResearchItemEntity::getTopicId, topicId)
            .select(
                TopicResearchItemEntity::getId,
                TopicResearchItemEntity::getUrl,
                TopicResearchItemEntity::getTitle));
    videoParseRuns.applyToResearchItems(topicRows);
    long articleCount = 0;
    long videoCount = 0;
    long pendingVideoCount = 0;
    for (TopicResearchItemEntity row : topicRows) {
      boolean video = ResearchContentClassifier.isVideo(row.getUrl(), row.getTitle());
      if (video) {
        videoCount++;
        if (VideoParseStatus.from(row.getVideoParseStatus()).needsParse()) {
          pendingVideoCount++;
        }
      } else {
        articleCount++;
      }
    }

    return HotNewsResearchItemPageView.builder()
        .items(items)
        .total(total)
        .page(normalized.getPage())
        .pageSize(normalized.getPageSize())
        .itemCount(topicRows.size())
        .articleCount(articleCount)
        .videoCount(videoCount)
        .pendingVideoCount(pendingVideoCount)
        .build();
  }

  /**
   * 按内容形态追加 SQL 条件：视频看 URL 域名/路径与标题关键词，文章为取反。
   *
   * @param wrapper 查询条件
   * @param mediaKey all / article / video（已小写）
   */
  private void appendResearchMediaSqlFilter(
      LambdaQueryWrapper<TopicResearchItemEntity> wrapper, String mediaKey) {
    if (StrUtil.isBlank(mediaKey) || "all".equals(mediaKey)) {
      return;
    }
    if ("video".equals(mediaKey)) {
      wrapper.and(this::appendResearchVideoSqlCondition);
      return;
    }
    if ("article".equals(mediaKey)) {
      wrapper.and(inner -> inner.not(this::appendResearchVideoSqlCondition));
    }
  }

  /**
   * 视频形态的 SQL OR 组（与 {@link ResearchContentClassifier} 启发式对齐）。
   * <p>供 {@code and(this::…)} / {@code not(this::…)} 作为嵌套条件消费者使用。
   *
   * @param wrapper 嵌套条件容器
   */
  private void appendResearchVideoSqlCondition(LambdaQueryWrapper<TopicResearchItemEntity> wrapper) {
    for (String host : ResearchContentClassifier.videoHostSqlKeywords()) {
      wrapper.or().like(TopicResearchItemEntity::getUrl, "%" + host + "%");
    }
    for (String path : ResearchContentClassifier.videoPathSqlKeywords()) {
      wrapper.or().like(TopicResearchItemEntity::getUrl, "%" + path + "%");
    }
    for (String titleKeyword : ResearchContentClassifier.videoTitleSqlKeywords()) {
      wrapper.or().like(TopicResearchItemEntity::getTitle, "%" + titleKeyword + "%");
    }
  }

  public List<HotNewsResearchItemView> listArticleReferenceItems(String topicId) {
    return HotNewsTopicResearchAssistService.filterArticleReferences(listResearchItems(topicId));
  }

  /**
   * 列出选题下的调研冲突提示，按创建时间倒序。
   *
   * @param topicId 选题主键
   * @return 冲突视图列表
   */
  public List<HotNewsResearchConflictView> listResearchConflicts(String topicId) {
    return researchConflictMapper.selectList(new LambdaQueryWrapper<TopicResearchConflictEntity>()
            .eq(TopicResearchConflictEntity::getTopicId, topicId)
            .orderByDesc(TopicResearchConflictEntity::getCreatedAt))
        .stream()
        .map(this::researchConflictView)
        .toList();
  }

  HotNewsResearchRunView researchRunView(TopicResearchRunEntity run) {
    return HotNewsResearchRunView.builder()
        .id(run.getId())
        .topicId(run.getTopicId())
        .jobId(run.getJobId())
        .status(run.getStatus())
        .errorMessage(run.getErrorMessage())
        .createdAt(run.getCreatedAt())
        .finishedAt(run.getFinishedAt())
        .build();
  }

  /** 资料池去重键：优先规范化 URL，无 URL 时用标题。 */
  static String researchItemDedupeKey(TopicResearchItemEntity item) {
    return TopicResearchPlanner.materialDedupeKey(
        item == null ? null : item.getUrl(),
        item == null ? null : item.getTitle());
  }
  /**
   * 单条资料池视图（先水合再映射）。
   *
   * @param item 资料池实体
   * @return 视图
   */
  HotNewsResearchItemView researchItemView(TopicResearchItemEntity item) {
    videoParseRuns.applyToResearchItem(item);
    return toResearchItemView(item);
  }

  /**
   * 实体转视图（调用方已完成水合时使用，避免重复查共享表）。
   *
   * @param item 资料池实体
   * @return 视图
   */
  HotNewsResearchItemView toResearchItemView(TopicResearchItemEntity item) {
    ResearchItemKind itemKind = ResearchItemKind.from(item.getKind());
    String sourceLabel = itemKind.getSourceLabel();
    String mediaLabel = ResearchContentClassifier.mediaLabel(item.getUrl(), item.getTitle());
    String siteLabel = ResearchContentClassifier.siteLabel(item.getUrl());
    boolean isVideo = "视频".equals(mediaLabel);
    VideoParseStatus parseStatus = isVideo
        ? VideoParseStatus.from(item.getVideoParseStatus())
        : VideoParseStatus.NONE;
    VideoParseStep parseStep = VideoParseStep.from(item.getVideoParseStep());
    return HotNewsResearchItemView.builder()
        .id(item.getId())
        .runId(item.getRunId())
        // 兼容前端别名字段
        .researchRunId(item.getRunId())
        .topicId(item.getTopicId())
        .title(item.getTitle())
        .url(item.getUrl())
        .summary(item.getSummary())
        .contentSummary(item.getSummary())
        .score(item.getScore())
        .overallScore(item.getScore())
        .scoreDetails(scoreDetailsStore.read(item.getId()))
        .kind(item.getKind())
        .sourceLabel(sourceLabel)
        .siteLabel(siteLabel)
        .mediaLabel(mediaLabel)
        .videoParseStatus(isVideo ? parseStatus.getValue() : null)
        .videoParseStep(item.getVideoParseStep())
        .videoParseStepLabel(parseStep == null ? null : parseStep.getLabel())
        .videoParseError(item.getVideoParseError())
        .videoRawText(ChineseTextNormalizer.toSimplified(
            videoParse.resolveResearchVideoRawText(item)))
        .videoParsedAt(item.getVideoParsedAt())
        .videoContentKind(item.getVideoContentKind())
        .videoDurationSeconds(isVideo ? item.getVideoDurationSeconds() : null)
        .createdAt(item.getCreatedAt())
        .fetchedAt(item.getCreatedAt())
        .build();
  }

  /**
   * 将调研冲突实体转为对外视图。
   *
   * @param conflict 冲突实体
   * @return 冲突视图
   */
  HotNewsResearchConflictView researchConflictView(TopicResearchConflictEntity conflict) {
    return HotNewsResearchConflictView.builder()
        .id(conflict.getId())
        .runId(conflict.getRunId())
        .researchRunId(conflict.getRunId())
        .topicId(conflict.getTopicId())
        .claim(conflict.getClaim())
        .detail(conflict.getDetail())
        .createdAt(conflict.getCreatedAt())
        .build();
  }


}
