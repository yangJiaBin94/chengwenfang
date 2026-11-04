package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengwenfang.common.IdsUtil;
import com.chengwenfang.file.facade.FileFacade;
import com.chengwenfang.file.facade.dto.view.StoredFileView;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsArticleSkeletonEntity;
import com.chengwenfang.mode.hotnews.domain.entity.HotnewsArticleSkeletonItemEntity;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.facade.constant.HotnewsSkeletonItemTypes;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsWordCountOption;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsArticleSkeletonItemMapper;
import com.chengwenfang.mode.hotnews.infrastructure.mapper.HotnewsArticleSkeletonMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文章骨架：原始 JSON 文件 + 列/列表项覆盖组装视图。
 */
@Component
@RequiredArgsConstructor
public class ArticleSkeletonStore {
  private final HotnewsArticleSkeletonMapper skeletonMapper;
  private final HotnewsArticleSkeletonItemMapper itemMapper;
  private final FileFacade files;
  private final HotNewsCodec codec;

  /**
   * 读取骨架视图；无行返回 null。
   *
   * @param topicId 选题 id
   * @return 视图；缺失 null
   */
  public HotNewsArticleSkeletonView read(String topicId) {
    if (StrUtil.isBlank(topicId)) {
      return null;
    }
    HotnewsArticleSkeletonEntity row = skeletonMapper.selectById(topicId);
    if (row == null) {
      return null;
    }
    Map<String, Object> raw = new LinkedHashMap<>();
    if (StrUtil.isNotBlank(row.getRawFileId())) {
      try {
        String text = files.readText(row.getRawFileId());
        raw.putAll(codec.readMap(text));
      } catch (Exception ignored) {
        // 文件缺失时仍用列覆盖
      }
    }
    overlayScalars(raw, row);
    overlayLists(raw, topicId);
    String json = codec.writeJson(raw);
    HotNewsArticleSkeletonView parsed =
        HotNewsTopicResearchAssistService.parseSkeleton(topicId, json);
    if (parsed != null) {
      return parsed;
    }
    return HotNewsArticleSkeletonView.builder()
        .topicId(topicId)
        .workingTitle(String.valueOf(raw.getOrDefault("workingTitle", "")))
        .scope(String.valueOf(raw.getOrDefault("scope", "")))
        .coreThesis(String.valueOf(raw.getOrDefault("coreThesis", "")))
        .coreEvent(String.valueOf(raw.getOrDefault("coreEvent", "")))
        .targetAudience(String.valueOf(raw.getOrDefault("targetAudience", "")))
        .readerIntents(HotNewsCodec.asStringList(raw.get("readerIntents")))
        .readerGaps(HotNewsCodec.asStringList(raw.get("readerGaps")))
        .keyInsights(HotNewsCodec.asStringList(raw.get("keyInsights")))
        .impacts(HotNewsCodec.asStringList(raw.get("impacts")))
        .readerSignals(HotNewsCodec.asStringList(raw.get("readerSignals")))
        .narrativeShape(String.valueOf(raw.getOrDefault("narrativeShape", "")))
        .sectionPlan(HotNewsCodec.asStringList(raw.get("sectionPlan")))
        .illustrationStyle(String.valueOf(raw.getOrDefault(
            "illustrationStyle", HotNewsMarkdownUtil.DEFAULT_ILLUSTRATION_STYLE)))
        .coverBrief(String.valueOf(raw.getOrDefault("coverBrief", "")))
        .imagePromptRequirements(String.valueOf(raw.getOrDefault(
            "imagePromptRequirements", HotNewsMarkdownUtil.defaultImagePromptRequirements())))
        .recommendedWordCountOption(HotNewsWordCountOption.normalize(
            String.valueOf(raw.getOrDefault("recommendedWordCountOption", ""))))
        .recommendedImageCountOption(HotNewsImageCountOption.normalize(
            String.valueOf(raw.getOrDefault("recommendedImageCountOption", ""))))
        .architecture(HotNewsCodec.asStringList(raw.get("architecture")))
        .flow(HotNewsCodec.asStringList(raw.get("flow")))
        .modules(modulesOf(raw.get("modules")))
        .referenceHints(HotNewsCodec.asStringList(raw.get("referenceHints")))
        .raw(raw)
        .build();
  }

  /**
   * 落库骨架：写 raw 文件、标量列与列表项。
   *
   * @param topicId  选题 id
   * @param skeleton 视图
   */
  @Transactional
  public void write(String topicId, HotNewsArticleSkeletonView skeleton) {
    if (StrUtil.isBlank(topicId) || skeleton == null) {
      return;
    }
    Map<String, Object> payload = skeleton.getRaw() == null
        ? new LinkedHashMap<>()
        : new LinkedHashMap<>(skeleton.getRaw());
    payload.put("workingTitle", skeleton.getWorkingTitle());
    payload.put("scope", skeleton.getScope());
    payload.put("coreThesis", skeleton.getCoreThesis());
    payload.put("coreEvent", skeleton.getCoreEvent());
    payload.put("targetAudience", skeleton.getTargetAudience());
    payload.put("readerIntents", skeleton.getReaderIntents());
    payload.put("readerGaps", skeleton.getReaderGaps());
    payload.put("keyInsights", skeleton.getKeyInsights());
    payload.put("impacts", skeleton.getImpacts());
    payload.put("readerSignals", skeleton.getReaderSignals());
    payload.put("narrativeShape", skeleton.getNarrativeShape());
    payload.put("sectionPlan", skeleton.getSectionPlan());
    payload.put("illustrationStyle", skeleton.getIllustrationStyle());
    payload.put("coverBrief", skeleton.getCoverBrief());
    payload.put("imagePromptRequirements", skeleton.getImagePromptRequirements());
    payload.put(
        "recommendedWordCountOption",
        HotNewsWordCountOption.normalize(skeleton.getRecommendedWordCountOption()));
    payload.put(
        "recommendedImageCountOption",
        HotNewsImageCountOption.normalize(skeleton.getRecommendedImageCountOption()));
    payload.put("architecture", skeleton.getArchitecture());
    payload.put("flow", skeleton.getFlow());
    payload.put("modules", skeleton.getModules());
    payload.put("referenceHints", skeleton.getReferenceHints());
    String updatedAtText = skeleton.getUpdatedAt() == null
        ? LocalDateTime.now().toString()
        : skeleton.getUpdatedAt().toString();
    payload.put("updatedAt", updatedAtText);

    String objectKey = "hotnews/topics/" + topicId + "/skeleton-raw.json";
    StoredFileView stored = files.writeText(objectKey, "skeleton-raw.json", codec.writeJson(payload));
    LocalDateTime now = LocalDateTime.now();
    HotnewsArticleSkeletonEntity row = HotnewsArticleSkeletonEntity.builder()
        .topicId(topicId)
        .rawFileId(stored.getId())
        .workingTitle(skeleton.getWorkingTitle())
        .scope(skeleton.getScope())
        .coreThesis(skeleton.getCoreThesis())
        .coreEvent(skeleton.getCoreEvent())
        .narrativeShape(skeleton.getNarrativeShape())
        .illustrationStyle(skeleton.getIllustrationStyle())
        .coverBrief(skeleton.getCoverBrief())
        .imagePromptRequirements(skeleton.getImagePromptRequirements())
        .recommendedWordCountOption(HotNewsWordCountOption.normalize(
            skeleton.getRecommendedWordCountOption()))
        .recommendedImageCountOption(HotNewsImageCountOption.normalize(
            skeleton.getRecommendedImageCountOption()))
        .updatedAtText(updatedAtText)
        .updatedAt(now)
        .build();
    if (skeletonMapper.selectById(topicId) == null) {
      skeletonMapper.insert(row);
    } else {
      skeletonMapper.updateById(row);
    }
    itemMapper.delete(new LambdaQueryWrapper<HotnewsArticleSkeletonItemEntity>()
        .eq(HotnewsArticleSkeletonItemEntity::getTopicId, topicId));
    writeList(topicId, HotnewsSkeletonItemTypes.KEY_INSIGHT, skeleton.getKeyInsights());
    writeList(topicId, HotnewsSkeletonItemTypes.IMPACT, skeleton.getImpacts());
    writeList(topicId, HotnewsSkeletonItemTypes.READER_SIGNAL, skeleton.getReaderSignals());
    writeList(topicId, HotnewsSkeletonItemTypes.READER_INTENT, skeleton.getReaderIntents());
    writeList(topicId, HotnewsSkeletonItemTypes.READER_GAP, skeleton.getReaderGaps());
    writeList(topicId, HotnewsSkeletonItemTypes.SECTION_PLAN, skeleton.getSectionPlan());
    writeList(topicId, HotnewsSkeletonItemTypes.ARCHITECTURE, skeleton.getArchitecture());
    writeList(topicId, HotnewsSkeletonItemTypes.FLOW, skeleton.getFlow());
    writeList(topicId, HotnewsSkeletonItemTypes.REFERENCE_HINT, skeleton.getReferenceHints());
  }

  private void overlayScalars(Map<String, Object> raw, HotnewsArticleSkeletonEntity row) {
    putIfPresent(raw, "workingTitle", row.getWorkingTitle());
    putIfPresent(raw, "scope", row.getScope());
    putIfPresent(raw, "coreThesis", row.getCoreThesis());
    putIfPresent(raw, "coreEvent", row.getCoreEvent());
    putIfPresent(raw, "narrativeShape", row.getNarrativeShape());
    putIfPresent(raw, "illustrationStyle", row.getIllustrationStyle());
    putIfPresent(raw, "coverBrief", row.getCoverBrief());
    putIfPresent(raw, "imagePromptRequirements", row.getImagePromptRequirements());
    putIfPresent(raw, "recommendedWordCountOption", row.getRecommendedWordCountOption());
    putIfPresent(raw, "recommendedImageCountOption", row.getRecommendedImageCountOption());
    putIfPresent(raw, "updatedAt", row.getUpdatedAtText());
  }

  /**
   * 用 items 表覆盖 raw 中的列表字段；库中无行时保留 raw 原值，避免空列表抹掉文件里的诊断字段。
   *
   * @param raw     已加载的 raw map
   * @param topicId 选题 id
   */
  private void overlayLists(Map<String, Object> raw, String topicId) {
    putListIfPresent(raw, "keyInsights", readList(topicId, HotnewsSkeletonItemTypes.KEY_INSIGHT));
    putListIfPresent(raw, "impacts", readList(topicId, HotnewsSkeletonItemTypes.IMPACT));
    putListIfPresent(raw, "readerSignals", readList(topicId, HotnewsSkeletonItemTypes.READER_SIGNAL));
    putListIfPresent(raw, "readerIntents", readList(topicId, HotnewsSkeletonItemTypes.READER_INTENT));
    putListIfPresent(raw, "readerGaps", readList(topicId, HotnewsSkeletonItemTypes.READER_GAP));
    putListIfPresent(raw, "sectionPlan", readList(topicId, HotnewsSkeletonItemTypes.SECTION_PLAN));
    putListIfPresent(raw, "architecture", readList(topicId, HotnewsSkeletonItemTypes.ARCHITECTURE));
    putListIfPresent(raw, "flow", readList(topicId, HotnewsSkeletonItemTypes.FLOW));
    putListIfPresent(raw, "referenceHints", readList(topicId, HotnewsSkeletonItemTypes.REFERENCE_HINT));
  }

  /**
   * 仅当库中存在非空列表时覆盖 raw，避免空结果冲掉文件内容。
   *
   * @param raw   raw map
   * @param key   JSON 字段名
   * @param values items 表读出的列表
   */
  private static void putListIfPresent(Map<String, Object> raw, String key, List<String> values) {
    if (CollUtil.isNotEmpty(values)) {
      raw.put(key, values);
    }
  }

  private List<String> readList(String topicId, String itemType) {
    return itemMapper.selectList(new LambdaQueryWrapper<HotnewsArticleSkeletonItemEntity>()
            .eq(HotnewsArticleSkeletonItemEntity::getTopicId, topicId)
            .eq(HotnewsArticleSkeletonItemEntity::getItemType, itemType)
            .orderByAsc(HotnewsArticleSkeletonItemEntity::getSeq))
        .stream()
        .map(HotnewsArticleSkeletonItemEntity::getContent)
        .filter(StrUtil::isNotBlank)
        .toList();
  }

  private void writeList(String topicId, String itemType, List<String> values) {
    if (CollUtil.isEmpty(values)) {
      return;
    }
    int seq = 0;
    for (String value : values) {
      if (StrUtil.isBlank(value)) {
        continue;
      }
      itemMapper.insert(HotnewsArticleSkeletonItemEntity.builder()
          .id(IdsUtil.newId())
          .topicId(topicId)
          .itemType(itemType)
          .seq(seq++)
          .content(value)
          .build());
    }
  }

  private static void putIfPresent(Map<String, Object> raw, String key, String value) {
    if (value != null) {
      raw.put(key, value);
    }
  }

  private static List<Map<String, Object>> modulesOf(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object entry : list) {
      if (entry instanceof Map<?, ?> map) {
        Map<String, Object> copied = new LinkedHashMap<>();
        map.forEach((key, val) -> copied.put(String.valueOf(key), val));
        out.add(copied);
      } else if (entry != null) {
        try {
          Map<String, Object> parsed = JSONUtil.parseObj(String.valueOf(entry));
          out.add(new LinkedHashMap<>(parsed));
        } catch (Exception ignored) {
          // skip
        }
      }
    }
    return out;
  }
}
