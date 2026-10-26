package com.workbench.mode.hotnews.application.generation.hotnews;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleSkeletonView;
import com.workbench.mode.hotnews.facade.dto.view.HotNewsArticleView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热点成文工作流图状态（固定结构；与 GraphRuntime 的 Map 状态互转）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotNewsGraphState {
  /** 文章 ID。 */
  private String articleId;
  /** 选题 ID。 */
  private String topicId;
  /** 简报 ID。 */
  private String briefId;
  /** Job ID。 */
  private String jobId;
  /** 标题。 */
  private String title;
  /** 选题摘要。 */
  private String topicSummary;
  /** 主题成文上下文（读者/边界等）。 */
  private String themeContext;
  /** 文章骨架。 */
  private HotNewsArticleSkeletonView skeleton;
  /** 骨架 JSON（供 prompt）。 */
  private String skeletonJson;
  /** 参考资料 JSON（供 prompt）。 */
  private String referencesJson;
  /** Skill 快照摘要。 */
  private String skillSnapshotSummary;
  /** 事实包 JSON。 */
  private String factsJson;
  /** 文章定位说明。 */
  private String positioning;
  /** 原稿结构规划。 */
  private String draftPlan;
  /** 配图规划。 */
  private String imagePlan;
  /** 字数档。 */
  private String wordCountOption;
  /** 插图档。 */
  private String imageCountOption;
  /** 原稿。 */
  private String draft;
  /** 公众号正文。 */
  private String wechat;
  /** 原稿审校是否通过。 */
  private Boolean reviewPassed;
  /** 公众号审校是否通过。 */
  private Boolean wechatReviewPassed;
  /** 是否跳过配图。 */
  private Boolean imagesSkipped;
  /** 配图校验是否通过。 */
  private Boolean imagesValidated;
  /** 最终完整性检查是否通过。 */
  private Boolean finalValidationPassed;
  /** 已生成配图摘要。 */
  @Builder.Default
  private List<HotNewsGeneratedImageSummary> generatedImages = new ArrayList<>();
  /** 提交后的文章视图。 */
  private HotNewsArticleView committedArticle;
  /** 是否已提交版本。 */
  private Boolean committed;

  /**
   * 从 GraphRuntime 状态 Map 构建。
   *
   * @param raw 图状态
   * @return 类型化状态
   */
  public static HotNewsGraphState fromMap(Map<String, Object> raw) {
    Map<String, Object> source = raw == null ? Map.of() : raw;
    HotNewsGraphState state = new HotNewsGraphState();
    state.articleId = Convert.toStr(source.get(HotNewsGraphStateKeys.ARTICLE_ID), "");
    state.topicId = Convert.toStr(source.get(HotNewsGraphStateKeys.TOPIC_ID), "");
    state.briefId = Convert.toStr(source.get(HotNewsGraphStateKeys.BRIEF_ID), "");
    state.jobId = Convert.toStr(source.get(HotNewsGraphStateKeys.JOB_ID), "");
    state.title = Convert.toStr(source.get(HotNewsGraphStateKeys.TITLE), "");
    state.topicSummary = Convert.toStr(source.get(HotNewsGraphStateKeys.TOPIC_SUMMARY), "");
    state.themeContext = Convert.toStr(source.get(HotNewsGraphStateKeys.THEME_CONTEXT), "");
    state.skeletonJson = Convert.toStr(source.get(HotNewsGraphStateKeys.SKELETON_JSON), "");
    // Job 恢复后 skeleton 常为 Map；优先反序列化，否则回退 skeletonJson
    state.skeleton = resolveSkeleton(
        source.get(HotNewsGraphStateKeys.SKELETON), state.skeletonJson);
    state.referencesJson = Convert.toStr(source.get(HotNewsGraphStateKeys.REFERENCES_JSON), "");
    state.skillSnapshotSummary = Convert.toStr(source.get(HotNewsGraphStateKeys.SKILL_SNAPSHOT_SUMMARY), "");
    state.factsJson = Convert.toStr(source.get(HotNewsGraphStateKeys.FACTS_JSON), "");
    state.positioning = Convert.toStr(source.get(HotNewsGraphStateKeys.POSITIONING), "");
    state.draftPlan = Convert.toStr(source.get(HotNewsGraphStateKeys.DRAFT_PLAN), "");
    state.imagePlan = Convert.toStr(source.get(HotNewsGraphStateKeys.IMAGE_PLAN), "");
    state.wordCountOption = Convert.toStr(source.get(HotNewsGraphStateKeys.WORD_COUNT_OPTION), "");
    state.imageCountOption = Convert.toStr(source.get(HotNewsGraphStateKeys.IMAGE_COUNT_OPTION), "");
    state.draft = Convert.toStr(source.get(HotNewsGraphStateKeys.DRAFT), "");
    state.wechat = Convert.toStr(source.get(HotNewsGraphStateKeys.WECHAT), "");
    if (source.get(HotNewsGraphStateKeys.REVIEW_PASSED) instanceof Boolean reviewFlag) {
      state.reviewPassed = reviewFlag;
    }
    if (source.get(HotNewsGraphStateKeys.WECHAT_REVIEW_PASSED) instanceof Boolean wechatFlag) {
      state.wechatReviewPassed = wechatFlag;
    }
    if (source.get(HotNewsGraphStateKeys.IMAGES_SKIPPED) instanceof Boolean skipped) {
      state.imagesSkipped = skipped;
    }
    if (source.get(HotNewsGraphStateKeys.IMAGES_VALIDATED) instanceof Boolean imagesOk) {
      state.imagesValidated = imagesOk;
    }
    if (source.get(HotNewsGraphStateKeys.FINAL_VALIDATION_PASSED) instanceof Boolean finalOk) {
      state.finalValidationPassed = finalOk;
    }
    if (source.get(HotNewsGraphStateKeys.GENERATED_IMAGES) instanceof List<?> imageList) {
      List<HotNewsGeneratedImageSummary> summaries = new ArrayList<>();
      for (Object item : imageList) {
        if (item instanceof HotNewsGeneratedImageSummary summary) {
          summaries.add(summary);
        } else if (item instanceof Map<?, ?> imageMap) {
          summaries.add(HotNewsGeneratedImageSummary.builder()
              .id(Convert.toStr(imageMap.get("id"), ""))
              .kind(Convert.toStr(imageMap.get("kind"), ""))
              .sequenceNo(imageMap.get("sequenceNo") instanceof Number number
                  ? number.intValue() : null)
              .build());
        }
      }
      state.generatedImages = summaries;
    }
    if (source.get(HotNewsGraphStateKeys.ARTICLE) instanceof HotNewsArticleView articleView) {
      state.committedArticle = articleView;
    }
    if (source.get(HotNewsGraphStateKeys.COMMITTED) instanceof Boolean committedFlag) {
      state.committed = committedFlag;
    }
    return state;
  }

  /**
   * 转为 GraphRuntime 可合并的增量 Map。
   *
   * @return 状态增量
   */
  public Map<String, Object> toMap() {
    Map<String, Object> output = new LinkedHashMap<>();
    putIfPresent(output, HotNewsGraphStateKeys.ARTICLE_ID, articleId);
    putIfPresent(output, HotNewsGraphStateKeys.TOPIC_ID, topicId);
    putIfPresent(output, HotNewsGraphStateKeys.BRIEF_ID, briefId);
    putIfPresent(output, HotNewsGraphStateKeys.JOB_ID, jobId);
    putIfPresent(output, HotNewsGraphStateKeys.TITLE, title);
    putIfPresent(output, HotNewsGraphStateKeys.TOPIC_SUMMARY, topicSummary);
    putIfPresent(output, HotNewsGraphStateKeys.THEME_CONTEXT, themeContext);
    if (skeleton != null) {
      output.put(HotNewsGraphStateKeys.SKELETON, skeleton);
    }
    putIfPresent(output, HotNewsGraphStateKeys.SKELETON_JSON, skeletonJson);
    putIfPresent(output, HotNewsGraphStateKeys.REFERENCES_JSON, referencesJson);
    putIfPresent(output, HotNewsGraphStateKeys.SKILL_SNAPSHOT_SUMMARY, skillSnapshotSummary);
    putIfPresent(output, HotNewsGraphStateKeys.FACTS_JSON, factsJson);
    putIfPresent(output, HotNewsGraphStateKeys.POSITIONING, positioning);
    putIfPresent(output, HotNewsGraphStateKeys.DRAFT_PLAN, draftPlan);
    putIfPresent(output, HotNewsGraphStateKeys.IMAGE_PLAN, imagePlan);
    putIfPresent(output, HotNewsGraphStateKeys.WORD_COUNT_OPTION, wordCountOption);
    putIfPresent(output, HotNewsGraphStateKeys.IMAGE_COUNT_OPTION, imageCountOption);
    putIfPresent(output, HotNewsGraphStateKeys.DRAFT, draft);
    putIfPresent(output, HotNewsGraphStateKeys.WECHAT, wechat);
    if (reviewPassed != null) {
      output.put(HotNewsGraphStateKeys.REVIEW_PASSED, reviewPassed);
    }
    if (wechatReviewPassed != null) {
      output.put(HotNewsGraphStateKeys.WECHAT_REVIEW_PASSED, wechatReviewPassed);
    }
    if (imagesSkipped != null) {
      output.put(HotNewsGraphStateKeys.IMAGES_SKIPPED, imagesSkipped);
    }
    if (imagesValidated != null) {
      output.put(HotNewsGraphStateKeys.IMAGES_VALIDATED, imagesValidated);
    }
    if (finalValidationPassed != null) {
      output.put(HotNewsGraphStateKeys.FINAL_VALIDATION_PASSED, finalValidationPassed);
    }
    if (generatedImages != null) {
      output.put(HotNewsGraphStateKeys.GENERATED_IMAGES, generatedImages);
    }
    if (committedArticle != null) {
      output.put(HotNewsGraphStateKeys.ARTICLE, committedArticle);
    }
    if (committed != null) {
      output.put(HotNewsGraphStateKeys.COMMITTED, committed);
    }
    return output;
  }

  /**
   * 非空白字符串才写入状态 Map。
   *
   * @param target 目标 Map
   * @param key    字段名
   * @param value  字符串值
   */
  private static void putIfPresent(Map<String, Object> target, String key, String value) {
    if (StrUtil.isNotBlank(value)) {
      target.put(key, value);
    }
  }

  /**
   * 从图状态中的 skeleton / skeletonJson 还原类型化骨架。
   *
   * @param skeletonRaw  可能是视图、Map 或 JSON 字符串
   * @param skeletonJson 骨架 JSON 备份
   * @return 骨架视图；无法解析时为 null
   */
  private static HotNewsArticleSkeletonView resolveSkeleton(Object skeletonRaw, String skeletonJson) {
    if (skeletonRaw instanceof HotNewsArticleSkeletonView skeletonView) {
      return skeletonView;
    }
    if (skeletonRaw instanceof Map<?, ?> skeletonMap) {
      try {
        return JSONUtil.toBean(JSONUtil.toJsonStr(skeletonMap), HotNewsArticleSkeletonView.class);
      } catch (Exception ignored) {
        // 继续尝试 skeletonJson
      }
    }
    if (skeletonRaw instanceof String skeletonText && StrUtil.isNotBlank(skeletonText)) {
      try {
        return JSONUtil.toBean(skeletonText, HotNewsArticleSkeletonView.class);
      } catch (Exception ignored) {
        // 继续尝试 skeletonJson
      }
    }
    if (StrUtil.isNotBlank(skeletonJson)) {
      try {
        return JSONUtil.toBean(skeletonJson, HotNewsArticleSkeletonView.class);
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }
}
