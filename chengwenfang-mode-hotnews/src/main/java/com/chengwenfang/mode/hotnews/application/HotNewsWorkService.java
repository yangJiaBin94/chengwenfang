package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.BusinessException;
import com.chengwenfang.common.ErrorCodes;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.job.facade.JobFacade;
import com.chengwenfang.job.facade.constant.JobEntityTypes;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.job.facade.dto.view.JobView;
import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicBriefEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleStatuses;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsBriefStatuses;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsResearchItemView;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicBriefMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点作品创建与查询：骨架、成文入队、列表/详情。
 */
@Slf4j
@Service
public class HotNewsWorkService {

  /** 选题表。 */
  private final TopicClusterMapper topicMapper;
  /** 简报表。 */
  private final TopicBriefMapper briefMapper;
  /** 任务门面。 */
  private final JobFacade jobs;
  /** 骨架 AI 助手。 */
  private final HotNewsTopicResearchAssistService researchAssist;
  /** JSON/哈希助手。 */
  private final HotNewsCodec codec;
  /** 骨架存储。 */
  private final ArticleSkeletonStore skeletonStore;
  /** 简报结构化存储。 */
  private final BriefStructuredStore briefStore;
  /** 选题协作（@Lazy 破环）。 */
  private final HotNewsTopicService topics;
  /** 调研协作（@Lazy 破环）。 */
  private final HotNewsResearchService research;
  /** 视图组装。 */
  private final HotNewsArticleViewAssembler views;
  /** 生成参数与成文入队。 */
  private final HotNewsGenerationSettingsService generationSettings;
  /** 正文与壳投影。 */
  private final HotNewsWorkContentService contents;

  /**
   * @param topicMapper        选题表
   * @param briefMapper        简报表
   * @param jobs               任务门面
   * @param researchAssist     骨架 AI 助手
   * @param codec              JSON/哈希助手
   * @param skeletonStore      骨架存储
   * @param briefStore         简报结构化存储
   * @param topics             选题协作（@Lazy 破环）
   * @param research           调研协作（@Lazy 破环）
   * @param views              视图组装
   * @param generationSettings 生成参数协作
   * @param contents           正文与壳投影
   */
  public HotNewsWorkService(
      TopicClusterMapper topicMapper,
      TopicBriefMapper briefMapper,
      JobFacade jobs,
      HotNewsTopicResearchAssistService researchAssist,
      HotNewsCodec codec,
      ArticleSkeletonStore skeletonStore,
      BriefStructuredStore briefStore,
      @Lazy HotNewsTopicService topics,
      @Lazy HotNewsResearchService research,
      HotNewsArticleViewAssembler views,
      HotNewsGenerationSettingsService generationSettings,
      HotNewsWorkContentService contents) {
    this.topicMapper = topicMapper;
    this.briefMapper = briefMapper;
    this.jobs = jobs;
    this.researchAssist = researchAssist;
    this.codec = codec;
    this.skeletonStore = skeletonStore;
    this.briefStore = briefStore;
    this.topics = topics;
    this.research = research;
    this.views = views;
    this.generationSettings = generationSettings;
    this.contents = contents;
  }

  /**
   * 读取选题上已保存的文章骨架。
   *
   * @param topicId 选题主键
   * @return 骨架视图；无 JSON 则为 null
   */
  public HotNewsArticleSkeletonView getArticleSkeleton(String topicId) {
    topics.requireTopic(topicId);
    return skeletonStore.read(topicId);
  }

  /**
   * 拟定并落库文章骨架（无主题上下文）。
   *
   * @param topicId 选题主键
   * @return 骨架视图
   */
  @Transactional
  public HotNewsArticleSkeletonView buildArticleSkeleton(String topicId) {
    return buildArticleSkeleton(topicId, null);
  }

  /**
   * 拟定并落库文章骨架，同步生成设置与简报。
   *
   * @param topicId      选题主键
   * @param themeContext 主题上下文；可空
   * @return 骨架视图
   */
  @Transactional
  public HotNewsArticleSkeletonView buildArticleSkeleton(String topicId, String themeContext) {
    TopicClusterEntity topic = topics.requireTopic(topicId);
    List<HotNewsResearchItemView> items = research.listResearchItems(topicId);
    HotNewsArticleSkeletonView skeleton = researchAssist.buildArticleSkeleton(
        topicId,
        topic.getTitle(),
        topic.getSummary(),
        items,
        themeContext);
    skeletonStore.write(topicId, skeleton);
    topic.setUpdatedAt(LocalDateTime.now());
    topicMapper.updateById(topic);
    // 骨架拟定后同步到已有文章壳，进入工作区即可默认选中
    generationSettings.syncArticleSettingsFromSkeleton(topicId, skeleton);
    // 同步刷新选题简报：摘要取骨架要点，来源快照取资料池
    syncBriefFromResearchPool(topic, skeleton, items);
    return skeletonStore.read(topicId);
  }

  /**
   * 由简报创建或复用热点文章并入队生成任务。
   *
   * @param briefId 简报 id
   * @return 新建的成文任务
   */
  @Transactional
  public JobView generateArticleFromBrief(String briefId) {
    TopicBriefEntity brief = briefMapper.selectById(briefId);
    if (brief == null) {
      throw new BusinessException(ErrorCodes.BRIEF_NOT_FOUND, "选题简报不存在", 404);
    }
    LocalDateTime now = LocalDateTime.now();
    // 同一简报复用最新一篇文章：优先文档 brief 索引，避免壳列停写后漏查
    HotNewsArticleEntity article = contents.findLatestShellArticleByBrief(briefId);
    if (article == null) {
      // 首次：建文章投影 + 空版本基线
      article = new HotNewsArticleEntity();
      article.setId(IdsUtil.newId());
      article.setTopicId(brief.getTopicId());
      article.setBriefId(briefId);
      article.setTitle(brief.getTitle());
      article.setDraftContent("");
      article.setContent("");
      article.setContentHash(HotNewsCodec.sha256(""));
      article.setDraftContentHash(HotNewsCodec.sha256(""));
      article.setWordCount(0);
      article.setCurrentVersionNo(1);
      article.setStatus(HotNewsArticleStatuses.GENERATING);
      // 默认字数档与配图数，后续可在生成设置里改
      topics.requireTopic(brief.getTopicId());
      article.setGenerationSettingsJson(codec.writeJson(generationSettings.settingsFromSkeleton(
          skeletonStore.read(brief.getTopicId()))));
      article.setSourceSnapshotJson(briefStore.writeSourcesJson(briefId));
      article.setCreatedAt(now);
      article.setUpdatedAt(now);
      contents.persistNewHotArticleDocuments(article);
    } else {
      // 已有文章：仅切状态为 generating，准备重跑（status sole-write 文档表）
      article.setStatus(HotNewsArticleStatuses.GENERATING);
      article.setUpdatedAt(now);
      contents.syncShellMetadata(article);
    }
    return jobs.create(
        JobTypes.HOT_NEWS_ARTICLE_GENERATION,
        JobEntityTypes.HOT_NEWS_ARTICLE,
        article.getId(),
        generationSettings.hotArticleGenerationJobInput(
            article.getId(), briefId, brief.getTopicId()));
  }

  /**
   * 由选题创建或复用热点文章并入队生成；简报仅作外键壳，不要求 confirmed。
   *
   * @param topicId 选题主键
   * @return 新建的成文任务
   */
  @Transactional
  public JobView generateArticleFromTopic(String topicId) {
    TopicClusterEntity topic = topics.requireTopic(topicId);
    LocalDateTime now = LocalDateTime.now();
    HotNewsArticleEntity article = contents.findLatestShellArticleByTopic(topicId);
    if (article != null) {
      article.setStatus(HotNewsArticleStatuses.GENERATING);
      article.setUpdatedAt(now);
      contents.syncShellMetadata(article);
      return jobs.create(
          JobTypes.HOT_NEWS_ARTICLE_GENERATION,
          JobEntityTypes.HOT_NEWS_ARTICLE,
          article.getId(),
          generationSettings.hotArticleGenerationJobInput(
              article.getId(), article.getBriefId(), topicId));
    }
    List<TopicBriefEntity> briefs = briefMapper.selectList(new LambdaQueryWrapper<TopicBriefEntity>()
        .eq(TopicBriefEntity::getTopicId, topicId)
        .orderByDesc(TopicBriefEntity::getUpdatedAt)
        .last("LIMIT 1"));
    String briefId;
    if (briefs.isEmpty()) {
      briefId = topics.createBrief(topicId).getId();
    } else {
      briefId = briefs.get(0).getId();
    }
    TopicBriefEntity brief = briefMapper.selectById(briefId);
    article = new HotNewsArticleEntity();
    article.setId(IdsUtil.newId());
    article.setTopicId(topicId);
    article.setBriefId(briefId);
    article.setTitle(topic.getTitle());
    article.setDraftContent("");
    article.setContent("");
    article.setContentHash(HotNewsCodec.sha256(""));
    article.setDraftContentHash(HotNewsCodec.sha256(""));
    article.setWordCount(0);
    article.setCurrentVersionNo(1);
    article.setStatus(HotNewsArticleStatuses.GENERATING);
    article.setGenerationSettingsJson(codec.writeJson(generationSettings.settingsFromSkeleton(
        skeletonStore.read(topicId))));
    article.setSourceSnapshotJson(brief == null
        ? "[]" : briefStore.writeSourcesJson(brief.getId()));
    article.setCreatedAt(now);
    article.setUpdatedAt(now);
    contents.persistNewHotArticleDocuments(article);

    return jobs.create(
        JobTypes.HOT_NEWS_ARTICLE_GENERATION,
        JobEntityTypes.HOT_NEWS_ARTICLE,
        article.getId(),
        generationSettings.hotArticleGenerationJobInput(article.getId(), briefId, topicId));
  }

  /**
   * 列出选题下的热点文章，按更新时间倒序。
   *
   * @param topicId 选题主键
   * @return 文章视图列表
   */
  public List<HotNewsArticleView> listArticlesByTopic(String topicId) {
    List<HotNewsArticleEntity> articles = contents.listShellArticlesByTopic(topicId);
    return articles.stream().map(views::articleView).toList();
  }

  /**
   * 获取热点文章详情。
   *
   * @param articleId 文章主键
   * @return 文章视图（含草稿/公众号双轨内容与生成参数）
   * @throws BusinessException 文章不存在时（ARTICLE_NOT_FOUND / 404）
   */
  public HotNewsArticleView getHotArticle(String articleId) {
    return views.articleView(contents.requireArticle(articleId));
  }

  /**
   * 确保选题下有一篇热点文章（无则建 draft 基线；有则按骨架合并默认生成设置）。
   *
   * @param topicId 选题主键
   * @return 文章视图
   */
  @Transactional
  public HotNewsArticleView ensureArticleForTopic(String topicId) {
    TopicClusterEntity topic = topics.requireTopic(topicId);
    LocalDateTime now = LocalDateTime.now();
    HotNewsArticleEntity article = contents.findLatestShellArticleByTopic(topicId);
    HotNewsArticleSkeletonView skeleton = skeletonStore.read(topicId);
    Map<String, Object> settings = generationSettings.settingsFromSkeleton(skeleton);
    if (article != null) {
      // 以骨架推荐覆盖字数/插图/风格默认值，进入工作区直接选中
      Map<String, Object> merged = codec.readMap(generationSettings.preferGenerationSettingsJson(article));
      merged.putAll(settings);
      article.setGenerationSettingsJson(codec.writeJson(merged));
      if (StrUtil.isBlank(article.getTitle())
          && skeleton != null
          && StrUtil.isNotBlank(skeleton.getWorkingTitle())) {
        article.setTitle(skeleton.getWorkingTitle());
      }
      article.setUpdatedAt(now);
      // 骨架覆盖后的生成参数双写到文档表
      contents.syncShellMetadata(article);
      return views.articleView(article);
    }
    List<TopicBriefEntity> briefs = briefMapper.selectList(new LambdaQueryWrapper<TopicBriefEntity>()
        .eq(TopicBriefEntity::getTopicId, topicId)
        .orderByDesc(TopicBriefEntity::getUpdatedAt)
        .last("LIMIT 1"));
    String briefId = briefs.isEmpty() ? topics.createBrief(topicId).getId() : briefs.get(0).getId();
    TopicBriefEntity brief = briefMapper.selectById(briefId);
    article = new HotNewsArticleEntity();
    article.setId(IdsUtil.newId());
    article.setTopicId(topicId);
    article.setBriefId(briefId);
    article.setTitle(skeleton != null && StrUtil.isNotBlank(skeleton.getWorkingTitle())
        ? skeleton.getWorkingTitle()
        : topic.getTitle());
    article.setDraftContent("");
    article.setContent("");
    article.setContentHash(HotNewsCodec.sha256(""));
    article.setDraftContentHash(HotNewsCodec.sha256(""));
    article.setWordCount(0);
    article.setCurrentVersionNo(1);
    article.setStatus(HotNewsArticleStatuses.DRAFT);
    article.setGenerationSettingsJson(codec.writeJson(settings));
    article.setSourceSnapshotJson(brief == null
        ? "[]" : briefStore.writeSourcesJson(brief.getId()));
    article.setCreatedAt(now);
    article.setUpdatedAt(now);
    contents.persistNewHotArticleDocuments(article);
    return views.articleView(article);
  }

  /**
   * 按资料池与骨架刷新当前选题简报（无简报则创建）。
   *
   * @param topic         选题
   * @param skeleton      刚拟定的骨架
   * @param researchItems 资料池条目
   */
  private void syncBriefFromResearchPool(
      TopicClusterEntity topic,
      HotNewsArticleSkeletonView skeleton,
      List<HotNewsResearchItemView> researchItems) {
    LocalDateTime now = LocalDateTime.now();
    List<TopicBriefEntity> existing = briefMapper.selectList(new LambdaQueryWrapper<TopicBriefEntity>()
        .eq(TopicBriefEntity::getTopicId, topic.getId())
        .orderByDesc(TopicBriefEntity::getUpdatedAt)
        .last("LIMIT 1"));
    boolean create = existing.isEmpty();
    TopicBriefEntity brief = create ? new TopicBriefEntity() : existing.get(0);
    if (create) {
      brief.setId(IdsUtil.newId());
      brief.setTopicId(topic.getId());
      brief.setThemeId(topic.getThemeId());
      brief.setStatus(HotNewsBriefStatuses.DRAFT);
      brief.setCreatedAt(now);
    }
    // 标题优先用骨架工作标题
    brief.setTitle(StrUtil.blankToDefault(
        skeleton == null ? null : skeleton.getWorkingTitle(),
        topic.getTitle()));
    brief.setUpdatedAt(now);
    if (create) {
      briefMapper.insert(brief);
    }
    Map<String, Object> summary = topics.briefSummaryFromSkeleton(topic, skeleton);
    var sources = topics.briefSourceSnapshotFromPool(topic.getId(), researchItems);
    briefStore.writeChildren(brief, summary, sources);
    briefMapper.updateById(brief);
    String snapshotJson = briefStore.writeSourcesJson(brief.getId());
    // 已挂到该简报的文章：快照 sole-write 到文档表（壳列已停写）
    List<HotNewsArticleEntity> articles = contents.listShellArticlesByBrief(brief.getId());
    for (HotNewsArticleEntity article : articles) {
      article.setSourceSnapshotJson(snapshotJson);
      if (StrUtil.isNotBlank(brief.getTitle())
          && (StrUtil.isBlank(article.getTitle())
              || StrUtil.equals(topic.getTitle(), article.getTitle()))) {
        article.setTitle(brief.getTitle());
      }
      article.setUpdatedAt(now);
      contents.syncShellMetadata(article);
    }
    log.info("选题简报已按资料池刷新 topicId={} briefId={} sources={}",
        topic.getId(), brief.getId(),
        researchItems == null ? 0 : researchItems.size());
  }
}
