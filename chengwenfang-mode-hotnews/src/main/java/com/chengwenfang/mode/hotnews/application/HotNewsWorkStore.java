package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.content.application.WorkContentStoreImpl;
import com.chengwenfang.content.domain.entity.WorkEntity;
import com.chengwenfang.content.domain.entity.WorkGenerationSettingsEntity;
import com.chengwenfang.content.domain.entity.WorkOutputEntity;
import com.chengwenfang.content.domain.entity.WorkOutputVersionEntity;
import com.chengwenfang.content.facade.constant.ChannelCodes;
import com.chengwenfang.content.facade.constant.OutputFormatCodes;
import com.chengwenfang.content.facade.dto.DefaultOutputSpec;
import com.chengwenfang.content.facade.dto.WorkExtensionPayload;
import com.chengwenfang.content.facade.dto.view.WorkOutputVersionSnapshot;
import com.chengwenfang.content.infrastructure.mapper.WorkGenerationSettingsMapper;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleContentKinds;
import com.chengwenfang.content.infrastructure.mapper.WorkMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputMapper;
import com.chengwenfang.content.infrastructure.mapper.WorkOutputVersionMapper;
import com.chengwenfang.mode.hotnews.application.mode.HotNewsModeDefinition;
import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsWorkEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsWorkSourceEntity;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsBriefSourceRow;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsWorkMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsWorkSourceMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 热点作品持久化：works / hotnews_works / work_outputs / settings / sources。
 */
@Service
@RequiredArgsConstructor
public class HotNewsWorkStore {
  private final WorkMapper workMapper;
  private final WorkOutputMapper outputMapper;
  private final WorkOutputVersionMapper versionMapper;
  private final WorkGenerationSettingsMapper settingsMapper;
  private final WorkContentStoreImpl workContents;
  private final HotnewsWorkMapper hotnewsWorkMapper;
  private final HotnewsWorkSourceMapper sourceMapper;
  private final TopicClusterMapper topicMapper;
  private final HotNewsCodec codec;
  /** 热点模式定义：扩展行与默认产出。 */
  private final HotNewsModeDefinition hotNewsMode;

  /**
   * 按作品 id 加载热点文章投影；缺失返回 null。
   *
   * @param articleId 作品/文章 id
   * @return 投影；无则 null
   */
  public HotNewsArticleEntity load(String articleId) {
    if (StrUtil.isBlank(articleId)) {
      return null;
    }
    HotnewsWorkEntity hot = hotnewsWorkMapper.selectById(articleId);
    WorkEntity work = workMapper.selectById(articleId);
    if (hot == null || work == null) {
      return null;
    }
    WorkOutputEntity draft = findOutput(articleId, ChannelCodes.MASTER);
    WorkOutputEntity wechat = findOutput(articleId, ChannelCodes.WECHAT_MP);
    WorkGenerationSettingsEntity settings = settingsMapper.selectById(articleId);
    HotNewsArticleEntity article = new HotNewsArticleEntity();
    article.setId(articleId);
    article.setTopicId(hot.getTopicId());
    article.setBriefId(hot.getBriefId());
    article.setTitle(work.getTitle());
    article.setStatus(StrUtil.blankToDefault(hot.getStatus(), "draft"));
    article.setCreatedAt(work.getCreatedAt());
    article.setUpdatedAt(work.getUpdatedAt());
    if (wechat != null) {
      article.setContentHash(wechat.getContentHash());
      article.setWordCount(wechat.getWordCount());
      article.setCurrentVersionNo(wechat.getCurrentVersionNo());
      article.setWorkflowRunId(wechat.getWorkflowRunId());
      article.setContent(workContents.readCurrent(wechat.getId()));
    } else {
      article.setContent("");
      article.setWordCount(0);
      article.setCurrentVersionNo(0);
    }
    if (draft != null) {
      article.setDraftContentHash(draft.getContentHash());
      article.setDraftContent(workContents.readCurrent(draft.getId()));
      if (article.getCurrentVersionNo() == null || article.getCurrentVersionNo() == 0) {
        article.setCurrentVersionNo(draft.getCurrentVersionNo());
      }
      if (StrUtil.isBlank(article.getWorkflowRunId())) {
        article.setWorkflowRunId(draft.getWorkflowRunId());
      }
    } else {
      article.setDraftContent("");
    }
    article.setGenerationSettingsJson(codec.writeJson(settingsToMap(settings)));
    article.setSourceSnapshotJson(codec.writeJson(readSources(articleId)));
    return article;
  }

  /**
   * 按选题列出作品 id（按 works.updated_at 降序）。
   *
   * @param topicId 选题 id
   * @return 作品 id 列表
   */
  public List<String> listIdsByTopic(String topicId) {
    if (StrUtil.isBlank(topicId)) {
      return List.of();
    }
    List<HotnewsWorkEntity> rows = hotnewsWorkMapper.selectList(new LambdaQueryWrapper<HotnewsWorkEntity>()
        .eq(HotnewsWorkEntity::getTopicId, topicId));
    return sortWorkIdsByUpdatedAt(rows.stream().map(HotnewsWorkEntity::getWorkId).toList());
  }

  /**
   * 按简报列出作品 id（按 works.updated_at 降序）。
   *
   * @param briefId 简报 id
   * @return 作品 id 列表
   */
  public List<String> listIdsByBrief(String briefId) {
    if (StrUtil.isBlank(briefId)) {
      return List.of();
    }
    List<HotnewsWorkEntity> rows = hotnewsWorkMapper.selectList(new LambdaQueryWrapper<HotnewsWorkEntity>()
        .eq(HotnewsWorkEntity::getBriefId, briefId));
    return sortWorkIdsByUpdatedAt(rows.stream().map(HotnewsWorkEntity::getWorkId).toList());
  }

  /**
   * 选题下是否存在给定状态的热点作品。
   *
   * @param topicIds 选题 id 列表
   * @param status   状态
   * @return 存在为 true
   */
  public boolean hasStatus(List<String> topicIds, String status) {
    if (CollUtil.isEmpty(topicIds) || StrUtil.isBlank(status)) {
      return false;
    }
    Long count = hotnewsWorkMapper.selectCount(new LambdaQueryWrapper<HotnewsWorkEntity>()
        .in(HotnewsWorkEntity::getTopicId, topicIds)
        .eq(HotnewsWorkEntity::getStatus, status));
    return count != null && count > 0;
  }

  /**
   * 确保 works / hotnews_works / 双轨产出 / settings / sources 存在，并可选追加正文版本。
   *
   * @param article 文章投影
   * @param source  版本来源
   * @param reason  原因
   * @param writeBodies 是否写正文版本
   */
  @Transactional
  public void persist(HotNewsArticleEntity article, String source, String reason, boolean writeBodies) {
    if (article == null || StrUtil.isBlank(article.getId())) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    String workId = article.getId();
    String themeId = resolveThemeId(article.getTopicId());
    WorkEntity work = workMapper.selectById(workId);
    if (work == null) {
      work = WorkEntity.builder()
          .id(workId)
          .modeCode(hotNewsMode.modeCode())
          .themeId(themeId)
          .title(StrUtil.blankToDefault(article.getTitle(), ""))
          .createdAt(article.getCreatedAt() == null ? now : article.getCreatedAt())
          .updatedAt(now)
          .build();
      workMapper.insert(work);
    } else {
      work.setTitle(StrUtil.blankToDefault(article.getTitle(), work.getTitle()));
      if (StrUtil.isNotBlank(themeId)) {
        work.setThemeId(themeId);
      }
      work.setUpdatedAt(now);
      workMapper.updateById(work);
    }

    String status = StrUtil.blankToDefault(article.getStatus(), "draft");
    // 热点扩展行交由模式定义创建/补齐
    hotNewsMode.createExtension(WorkExtensionPayload.builder()
        .workId(workId)
        .themeId(themeId)
        .title(article.getTitle())
        .status(status)
        .updatedAt(now)
        .hotNews(WorkExtensionPayload.HotNewsWorkExtension.builder()
            .topicId(article.getTopicId())
            .briefId(article.getBriefId())
            .build())
        .build());

    String title = StrUtil.blankToDefault(article.getTitle(), "");
    // 按模式默认产出组合确保双轨壳
    String draftOutputId = null;
    String wechatOutputId = null;
    for (DefaultOutputSpec spec : hotNewsMode.defaultOutputs()) {
      String outputTitle = StrUtil.isBlank(spec.getTitleSuffix())
          ? title
          : title + spec.getTitleSuffix();
      String outputId = ensureOutputId(
          workId, spec.getChannelCode(), spec.getFormatCode(), outputTitle, status);
      if (ChannelCodes.MASTER.equals(spec.getChannelCode())) {
        draftOutputId = outputId;
      } else if (ChannelCodes.WECHAT_MP.equals(spec.getChannelCode())) {
        wechatOutputId = outputId;
      }
    }

    // 同步产出壳元数据（标题/状态/工作流）
    syncOutputShell(draftOutputId, title, status, article.getWorkflowRunId(), now);
    syncOutputShell(wechatOutputId, title, status, article.getWorkflowRunId(), now);

    upsertSettings(workId, article.getGenerationSettingsJson(), now);
    replaceSources(workId, codec.readBriefSourceRows(article.getSourceSnapshotJson()));

    if (writeBodies) {
      String draft = article.getDraftContent() == null ? "" : article.getDraftContent();
      String wechat = article.getContent() == null ? "" : article.getContent();
      workContents.appendVersion(
          draftOutputId, draft, source, reason, article.getWorkflowRunId());
      workContents.appendVersion(
          wechatOutputId, wechat, source, reason, article.getWorkflowRunId());
      // 公众号轨字数以投影为准回写
      WorkOutputEntity wechatOutput = outputMapper.selectById(wechatOutputId);
      if (wechatOutput != null && article.getWordCount() != null) {
        wechatOutput.setWordCount(article.getWordCount());
        if (StrUtil.isNotBlank(article.getContentHash())) {
          wechatOutput.setContentHash(article.getContentHash());
        }
        wechatOutput.setUpdatedAt(now);
        outputMapper.updateById(wechatOutput);
      }
    }
  }

  /**
   * 仅同步壳元数据（不追加正文版本）。
   *
   * @param article 文章投影
   */
  @Transactional
  public void syncMetadata(HotNewsArticleEntity article) {
    persist(article, null, null, false);
  }

  /**
   * 读取生成设置 Map（空列不输出键）。
   *
   * @param workId 作品 id
   * @return 设置 Map
   */
  public Map<String, Object> readSettingsMap(String workId) {
    return settingsToMap(settingsMapper.selectById(workId));
  }

  /**
   * 读取来源快照行。
   *
   * @param workId 作品 id
   * @return 行列表
   */
  public List<HotNewsBriefSourceRow> readSources(String workId) {
    if (StrUtil.isBlank(workId)) {
      return List.of();
    }
    List<HotnewsWorkSourceEntity> rows = sourceMapper.selectList(new LambdaQueryWrapper<HotnewsWorkSourceEntity>()
        .eq(HotnewsWorkSourceEntity::getWorkId, workId)
        .orderByAsc(HotnewsWorkSourceEntity::getSeq));
    List<HotNewsBriefSourceRow> out = new ArrayList<>();
    for (HotnewsWorkSourceEntity row : rows) {
      out.add(HotNewsBriefSourceRow.builder()
          .id(row.getRowId())
          .topicId(row.getTopicId())
          .sourceItemId(row.getSourceItemId())
          .role(row.getRole())
          .title(row.getTitle())
          .name(row.getName())
          .sourceName(row.getSourceName())
          .sourceTitle(row.getSourceTitle())
          .url(row.getUrl())
          .sourceUrl(row.getSourceUrl())
          .summary(row.getSummary())
          .build());
    }
    return out;
  }

  /**
   * 解析某轨正文。
   *
   * @param articleId 作品 id
   * @param kind      draft / wechat
   * @return 正文；无产出返回空串
   */
  public String readBody(String articleId, String kind) {
    String channel = HotNewsArticleContentKinds.isWechat(kind)
        ? ChannelCodes.WECHAT_MP
        : ChannelCodes.MASTER;
    WorkOutputEntity output = findOutput(articleId, channel);
    if (output == null) {
      return "";
    }
    return workContents.readCurrent(output.getId());
  }

  /**
   * 列出某轨版本快照（按 versionNo）。
   *
   * @param articleId 作品 id
   * @param kind      draft / wechat
   * @return 版本列表
   */
  public List<WorkOutputVersionSnapshot> listVersions(String articleId, String kind) {
    String channel = HotNewsArticleContentKinds.isWechat(kind)
        ? ChannelCodes.WECHAT_MP
        : ChannelCodes.MASTER;
    WorkOutputEntity output = findOutput(articleId, channel);
    if (output == null) {
      return List.of();
    }
    List<WorkOutputVersionEntity> versions = versionMapper.selectList(
        new LambdaQueryWrapper<WorkOutputVersionEntity>()
            .eq(WorkOutputVersionEntity::getOutputId, output.getId())
            .orderByDesc(WorkOutputVersionEntity::getVersionNo));
    List<WorkOutputVersionSnapshot> out = new ArrayList<>();
    for (WorkOutputVersionEntity version : versions) {
      String body = workContents.readVersion(output.getId(), version.getVersionNo());
      out.add(WorkOutputVersionSnapshot.builder()
          .id(version.getId())
          .outputId(output.getId())
          .versionNo(version.getVersionNo())
          .bodyMd(body)
          .contentHash(version.getContentHash())
          .source(version.getSource())
          .reason(version.getReason())
          .workflowRunId(version.getWorkflowRunId())
          .createdAt(version.getCreatedAt())
          .build());
    }
    return out;
  }

  /**
   * 草稿产出 id（查库；无产出返回 null）。
   *
   * @param articleId 作品 id
   * @return 产出 id
   */
  public String draftOutputId(String articleId) {
    WorkOutputEntity output = findOutput(articleId, ChannelCodes.MASTER);
    return output == null ? null : output.getId();
  }

  /**
   * 公众号产出 id（查库；无产出返回 null）。
   *
   * @param articleId 作品 id
   * @return 产出 id
   */
  public String wechatOutputId(String articleId) {
    WorkOutputEntity output = findOutput(articleId, ChannelCodes.WECHAT_MP);
    return output == null ? null : output.getId();
  }

  /**
   * 确保某渠道×格式产出存在并返回其 id。
   *
   * @param workId      作品 id
   * @param channelCode 渠道
   * @param formatCode  格式
   * @param title       标题
   * @param status      状态
   * @return 产出 id
   */
  private String ensureOutputId(
      String workId, String channelCode, String formatCode, String title, String status) {
    WorkOutputEntity existing = findOutput(workId, channelCode);
    if (existing != null) {
      return existing.getId();
    }
    String outputId = IdsUtil.newId();
    workContents.ensureOutput(outputId, workId, channelCode, formatCode, title, status);
    return outputId;
  }

  private WorkOutputEntity findOutput(String workId, String channelCode) {
    return outputMapper.selectOne(new LambdaQueryWrapper<WorkOutputEntity>()
        .eq(WorkOutputEntity::getWorkId, workId)
        .eq(WorkOutputEntity::getChannelCode, channelCode)
        .eq(WorkOutputEntity::getFormatCode, OutputFormatCodes.ARTICLE)
        .last("LIMIT 1"));
  }

  private void syncOutputShell(
      String outputId, String title, String status, String workflowRunId, LocalDateTime now) {
    WorkOutputEntity output = outputMapper.selectById(outputId);
    if (output == null) {
      return;
    }
    output.setTitle(StrUtil.blankToDefault(title, output.getTitle()));
    output.setStatus(StrUtil.blankToDefault(status, output.getStatus()));
    if (workflowRunId != null) {
      output.setWorkflowRunId(workflowRunId);
    }
    output.setUpdatedAt(now);
    outputMapper.updateById(output);
  }

  private void upsertSettings(String workId, String settingsJson, LocalDateTime now) {
    Map<String, Object> map = codec.readMap(settingsJson);
    WorkGenerationSettingsEntity existing = settingsMapper.selectById(workId);
    WorkGenerationSettingsEntity row = existing == null
        ? WorkGenerationSettingsEntity.builder().workId(workId).build()
        : existing;
    if (map.containsKey("wordCountOption")) {
      row.setWordCountOption(asString(map.get("wordCountOption")));
    }
    if (map.containsKey("imageCountOption")) {
      row.setImageCountOption(asString(map.get("imageCountOption")));
    }
    if (map.containsKey("workingTitle")) {
      row.setWorkingTitle(asString(map.get("workingTitle")));
    }
    if (map.containsKey("illustrationStyle")) {
      row.setIllustrationStyle(asString(map.get("illustrationStyle")));
    }
    if (map.containsKey("coverBrief")) {
      row.setCoverBrief(asString(map.get("coverBrief")));
    }
    if (map.containsKey("imagePromptRequirements")) {
      row.setImagePromptRequirements(asString(map.get("imagePromptRequirements")));
    }
    if (map.containsKey("lastRewriteInstruction")) {
      row.setLastRewriteInstruction(asString(map.get("lastRewriteInstruction")));
    }
    if (map.containsKey("rewrittenAt")) {
      row.setRewrittenAt(asString(map.get("rewrittenAt")));
    }
    row.setUpdatedAt(now);
    if (existing == null) {
      settingsMapper.insert(row);
    } else {
      settingsMapper.updateById(row);
    }
  }

  private void replaceSources(String workId, List<HotNewsBriefSourceRow> sources) {
    sourceMapper.delete(new LambdaQueryWrapper<HotnewsWorkSourceEntity>()
        .eq(HotnewsWorkSourceEntity::getWorkId, workId));
    if (CollUtil.isEmpty(sources)) {
      return;
    }
    int seq = 0;
    for (HotNewsBriefSourceRow source : sources) {
      if (source == null) {
        continue;
      }
      sourceMapper.insert(HotnewsWorkSourceEntity.builder()
          .id(IdsUtil.newId())
          .workId(workId)
          .seq(seq++)
          .rowId(source.getId())
          .topicId(source.getTopicId())
          .sourceItemId(source.getSourceItemId())
          .role(source.getRole())
          .title(source.getTitle())
          .name(source.getName())
          .sourceName(source.getSourceName())
          .sourceTitle(source.getSourceTitle())
          .url(source.getUrl())
          .sourceUrl(source.getSourceUrl())
          .summary(source.getSummary())
          .build());
    }
  }

  private Map<String, Object> settingsToMap(WorkGenerationSettingsEntity settings) {
    Map<String, Object> map = new LinkedHashMap<>();
    if (settings == null) {
      return map;
    }
    putIfPresent(map, "wordCountOption", settings.getWordCountOption());
    putIfPresent(map, "imageCountOption", settings.getImageCountOption());
    putIfPresent(map, "workingTitle", settings.getWorkingTitle());
    putIfPresent(map, "illustrationStyle", settings.getIllustrationStyle());
    putIfPresent(map, "coverBrief", settings.getCoverBrief());
    putIfPresent(map, "imagePromptRequirements", settings.getImagePromptRequirements());
    putIfPresent(map, "lastRewriteInstruction", settings.getLastRewriteInstruction());
    putIfPresent(map, "rewrittenAt", settings.getRewrittenAt());
    return map;
  }

  private static void putIfPresent(Map<String, Object> map, String key, String value) {
    if (StrUtil.isNotBlank(value)) {
      map.put(key, value);
    }
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private String resolveThemeId(String topicId) {
    if (StrUtil.isBlank(topicId)) {
      return null;
    }
    TopicClusterEntity topic = topicMapper.selectById(topicId);
    return topic == null ? null : topic.getThemeId();
  }

  private List<String> sortWorkIdsByUpdatedAt(List<String> workIds) {
    if (CollUtil.isEmpty(workIds)) {
      return List.of();
    }
    List<WorkEntity> works = workMapper.selectList(new LambdaQueryWrapper<WorkEntity>()
        .in(WorkEntity::getId, workIds));
    return works.stream()
        .sorted(Comparator.comparing(
            WorkEntity::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
        .map(WorkEntity::getId)
        .toList();
  }
}
