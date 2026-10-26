package com.chengwenfang.mode.hotnews.application;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.domain.HotNewsMarkdownUtil;
import com.chengwenfang.mode.hotnews.domain.entity.HotNewsArticleEntity;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleContentKinds;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.chengwenfang.mode.hotnews.facade.dto.view.HotNewsArticleView;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsImageCountOption;
import com.chengwenfang.mode.hotnews.facade.enums.HotNewsWordCountOption;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 热点文章与骨架视图组装。
 */
@Component
@RequiredArgsConstructor
public class HotNewsArticleViewAssembler {

  private final HotNewsWorkStore workStore;
  private final HotNewsCodec codec;

  /**
   * 将文章实体转为对外视图；解析快照/生成参数，并计算草稿词数。
   *
   * @param article 文章实体
   * @return 文章视图
   */
  public HotNewsArticleView articleView(HotNewsArticleEntity article) {
    String draftContent = StrUtil.nullToEmpty(article.getDraftContent());
    String wechatContent = StrUtil.nullToEmpty(article.getContent());
    if (StrUtil.isNotBlank(article.getId())) {
      String draftBody = workStore.readBody(article.getId(), HotNewsArticleContentKinds.DRAFT);
      if (draftBody != null) {
        draftContent = draftBody;
      }
      String wechatBody = workStore.readBody(article.getId(), HotNewsArticleContentKinds.WECHAT);
      if (wechatBody != null) {
        wechatContent = wechatBody;
      }
    }
    String wechatHash = StrUtil.blankToDefault(article.getContentHash(), HotNewsCodec.sha256(wechatContent));
    String draftHash = StrUtil.blankToDefault(article.getDraftContentHash(), HotNewsCodec.sha256(draftContent));
    Integer wechatWordCount = article.getWordCount() == null
        ? (StrUtil.isBlank(wechatContent) ? 0 : wechatContent.trim().split("\\s+").length)
        : article.getWordCount();
    Map<String, Object> settings = workStore.readSettingsMap(article.getId());
    if (settings.isEmpty()) {
      settings = codec.readMap(article.getGenerationSettingsJson());
    }
    return HotNewsArticleView.builder()
        .id(article.getId())
        .documentId(workStore.wechatOutputId(article.getId()))
        .draftDocumentId(workStore.draftOutputId(article.getId()))
        .topicId(article.getTopicId())
        .briefId(article.getBriefId())
        .title(article.getTitle())
        .draftContent(draftContent)
        .draftContentHash(draftHash)
        .draftWordCount(HotNewsMarkdownUtil.countBodyChars(draftContent))
        .content(wechatContent)
        .contentHash(wechatHash)
        .wordCount(wechatWordCount)
        .currentVersionNo(article.getCurrentVersionNo() == null ? 1 : article.getCurrentVersionNo())
        .status(article.getStatus())
        .workflowRunId(article.getWorkflowRunId())
        .sourceSnapshot(workStore.readSources(article.getId()))
        .generationSettings(settings)
        .createdAt(article.getCreatedAt())
        .updatedAt(article.getUpdatedAt())
        .build();
  }

  /**
   * 将骨架 JSON 解析为视图；解析失败时回退为字段投影。
   *
   * @param topicId 选题主键
   * @param json    骨架 JSON
   * @return 骨架视图；空白 JSON 为 null
   */
  public HotNewsArticleSkeletonView skeletonView(String topicId, String json) {
    if (StrUtil.isBlank(json)) {
      return null;
    }
    HotNewsArticleSkeletonView parsed =
        HotNewsTopicResearchAssistService.parseSkeleton(topicId, json);
    if (parsed != null) {
      return parsed;
    }
    Map<String, Object> raw = codec.readMap(json);
    return HotNewsArticleSkeletonView.builder()
        .topicId(topicId)
        .workingTitle(String.valueOf(raw.getOrDefault("workingTitle", "")))
        .scope(String.valueOf(raw.getOrDefault("scope", "")))
        .coreThesis(String.valueOf(raw.getOrDefault("coreThesis", "")))
        .illustrationStyle(String.valueOf(raw.getOrDefault(
            "illustrationStyle", HotNewsMarkdownUtil.DEFAULT_ILLUSTRATION_STYLE)))
        .coverBrief(String.valueOf(raw.getOrDefault("coverBrief", "")))
        .imagePromptRequirements(String.valueOf(raw.getOrDefault(
            "imagePromptRequirements", HotNewsMarkdownUtil.defaultImagePromptRequirements())))
        .recommendedWordCountOption(HotNewsWordCountOption.normalize(
            String.valueOf(raw.getOrDefault("recommendedWordCountOption", ""))))
        .recommendedImageCountOption(HotNewsImageCountOption.normalize(
            String.valueOf(raw.getOrDefault("recommendedImageCountOption", ""))))
        .architecture(List.of())
        .flow(List.of())
        .modules(List.of())
        .referenceHints(List.of())
        .raw(raw)
        .build();
  }
}
