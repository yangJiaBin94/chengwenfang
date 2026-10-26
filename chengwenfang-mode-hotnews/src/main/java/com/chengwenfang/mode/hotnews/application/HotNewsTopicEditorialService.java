package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.ai.facade.AiTextFacade;
import com.chengwenfang.ai.facade.constant.ModelCallScenes;
import com.chengwenfang.ai.facade.dto.request.TextCompleteRequest;
import com.chengwenfang.ai.facade.dto.view.TextCompleteResult;
import com.chengwenfang.mode.hotnews.application.editorial.TopicEditorialBatchRequest;
import com.chengwenfang.mode.hotnews.application.prompt.TopicEditorialPrompts;
import com.chengwenfang.mode.hotnews.application.editorial.TopicEditorialBatchResponse;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import com.chengwenfang.mode.hotnews.domain.entity.TopicClusterEntity;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.TopicClusterMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 选题中文提炼：把英文热点提炼为雷达可读的中文标题与事实速览。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotNewsTopicEditorialService {
  private static final int BATCH_SIZE = 12;
  private static final int RADAR_LIMIT = 10;
  private static final Pattern CHINESE = Pattern.compile("[\\u4e00-\\u9fff]");
  private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
  private final TopicClusterMapper topicMapper;
  private final AiTextFacade ai;

  /**
   * 对主题下高分可见选题做中文标题/速览提炼；已提炼且源未变则跳过。
   *
   * @param themeId 主题 ID
   * @return 成功写入速览的条数
   */
  public int editorializeTopics(String themeId) {
    if (StrUtil.isBlank(themeId)) {
      return 0;
    }
    List<TopicClusterEntity> radar = topicMapper.selectList(
        new LambdaQueryWrapper<TopicClusterEntity>()
            .eq(TopicClusterEntity::getThemeId, themeId)
            .ne(TopicClusterEntity::getStatus, "expired")
            .orderByDesc(TopicClusterEntity::getWritingScore)
            .orderByDesc(TopicClusterEntity::getUpdatedAt)
            .orderByAsc(TopicClusterEntity::getTitle)
            .orderByAsc(TopicClusterEntity::getId)
            .last("LIMIT " + RADAR_LIMIT));
    if (radar.isEmpty()) {
      return 0;
    }

    List<TopicClusterEntity> candidates = new ArrayList<>();
    for (TopicClusterEntity topic : radar) {
      String sourceHash = editorialHash(topic.getTitle(), topic.getSummary());
      if (sourceHash.equals(topic.getTranslationSourceHash())
          && StrUtil.isNotBlank(topic.getTranslatedTitle())
          && (StrUtil.isBlank(topic.getSummary()) || hasChinese(topic.getTranslatedSummary()))) {
        continue;
      }
      if (!needsTranslation(topic.getTitle(), topic.getSummary())) {
        // 已是中文：不做英译提炼，但仍把繁体标题/速览落成简体
        String simpleTitle = ChineseTextNormalizer.toSimplified(topic.getTitle());
        String simpleSummary = ChineseTextNormalizer.toSimplified(topic.getSummary());
        boolean changed = !StrUtil.equals(simpleTitle, topic.getTitle())
            || !StrUtil.equals(simpleSummary, topic.getSummary());
        topic.setTranslatedTitle(changed ? simpleTitle : null);
        topic.setTranslatedSummary(changed && StrUtil.isNotBlank(simpleSummary) ? simpleSummary : null);
        topic.setTranslationSourceHash(sourceHash);
        topic.setUpdatedAt(LocalDateTime.now());
        topicMapper.updateById(topic);
        continue;
      }
      topic.setTranslationSourceHash(sourceHash);
      candidates.add(topic);
    }
    if (candidates.isEmpty()) {
      return 0;
    }

    int translated = 0;
    for (int i = 0; i < candidates.size(); i += BATCH_SIZE) {
      List<TopicClusterEntity> batch = candidates.subList(i, Math.min(i + BATCH_SIZE, candidates.size()));
      try {
        translated += translateBatch(batch);
      } catch (Exception ex) {
        log.warn("热点中文速览提炼失败 themeId={} batch={} err={}",
            themeId, (i / BATCH_SIZE) + 1, ex.getMessage());
      }
    }
    log.info("热点中文速览提炼完成 themeId={} candidates={} translated={}",
        themeId, candidates.size(), translated);
    return translated;
  }

  private int translateBatch(List<TopicClusterEntity> batch) {
    Map<String, TopicClusterEntity> byId = new HashMap<>();
    List<TopicEditorialBatchRequest.TopicEditorialSourceItem> topics = new ArrayList<>();
    for (TopicClusterEntity topic : batch) {
      byId.put(topic.getId(), topic);
      String summary = topic.getSummary() == null ? "" : topic.getSummary();
      topics.add(TopicEditorialBatchRequest.TopicEditorialSourceItem.builder()
          .id(topic.getId())
          .title(topic.getTitle() == null ? "" : topic.getTitle())
          .summary(summary.length() > 600 ? summary.substring(0, 600) : summary)
          .build());
    }
    TopicEditorialBatchRequest user = TopicEditorialBatchRequest.builder()
        .topics(topics)
        .responseSchema(TopicEditorialBatchRequest.TopicEditorialResponseSchema.builder()
            .translations(List.of(TopicEditorialBatchRequest.TopicEditorialTranslationHint.builder()
                .id("原 id")
                .title("简体中文标题")
                .summary("简体中文摘要")
                .build()))
            .build())
        .build();

    TextCompleteResult result = ai.completeText(TextCompleteRequest.builder()
        .scene(ModelCallScenes.HOT_NEWS_TOPIC_TRANSLATION)
        .systemPrompt(TopicEditorialPrompts.SYSTEM_PROMPT)
        .prompt(JSONUtil.toJsonStr(user))
        .timeoutSeconds(120)
        .build());
    List<TopicEditorialBatchResponse.TopicEditorialTranslation> translations =
        parseTranslations(result == null ? null : result.getText());
    int written = 0;
    LocalDateTime now = LocalDateTime.now();
    for (TopicEditorialBatchResponse.TopicEditorialTranslation item : translations) {
      if (item == null || StrUtil.isBlank(item.getId())) {
        continue;
      }
      TopicClusterEntity topic = byId.get(item.getId());
      if (topic == null) {
        continue;
      }
      String title = StrUtil.trim(item.getTitle());
      String summary = StrUtil.trim(item.getSummary());
      if (title != null && title.length() > 500) {
        title = title.substring(0, 500);
      }
      if (summary != null && summary.length() > 1200) {
        summary = summary.substring(0, 1200);
      }
      if (StrUtil.isBlank(title) || !hasChinese(title)) {
        continue;
      }
      if (StrUtil.isNotBlank(summary) && !hasChinese(summary)) {
        continue;
      }
      topic.setTranslatedTitle(ChineseTextNormalizer.toSimplified(title));
      topic.setTranslatedSummary(ChineseTextNormalizer.toSimplified(summary));
      topic.setUpdatedAt(now);
      topicMapper.updateById(topic);
      written++;
    }
    return written;
  }

  static boolean needsTranslation(String title, String summary) {
    String text = (title == null ? "" : title) + " " + (summary == null ? "" : summary);
    int latin = countMatches(LATIN, text);
    int chinese = countMatches(CHINESE, text);
    return latin >= 12 && latin > chinese * 2;
  }

  static boolean hasChinese(String value) {
    return value != null && CHINESE.matcher(value).find();
  }

  static String editorialHash(String title, String summary) {
    return DigestUtil.sha256Hex(
        "hot-news-editorial-v2\n"
            + StrUtil.nullToEmpty(title)
            + "\n"
            + StrUtil.nullToEmpty(summary));
  }

  private static int countMatches(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static List<TopicEditorialBatchResponse.TopicEditorialTranslation> parseTranslations(String output) {
    if (StrUtil.isBlank(output)) {
      return List.of();
    }
    String json = output.trim();
    Matcher matcher = Pattern.compile("\\{.*\\}", Pattern.DOTALL).matcher(json);
    if (matcher.find()) {
      json = matcher.group(0);
    }
    TopicEditorialBatchResponse root = JSONUtil.toBean(json, TopicEditorialBatchResponse.class);
    if (root == null || root.getTranslations() == null || root.getTranslations().isEmpty()) {
      throw new IllegalArgumentException("翻译结果缺少 translations 数组");
    }
    return root.getTranslations();
  }
}
