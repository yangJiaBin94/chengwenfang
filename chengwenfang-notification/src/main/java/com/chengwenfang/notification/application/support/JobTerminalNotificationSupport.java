package com.chengwenfang.notification.application.support;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.job.facade.constant.JobTypes;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsArticleJobKeys;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsGenerationScopes;
import com.chengwenfang.mode.hotnews.facade.constant.HotNewsRewriteJobKeys;
import com.chengwenfang.notification.facade.constant.NotificationMetadataKeys;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Job 终态通知：按任务类型补齐 metadata，并组装中文标题/成功正文。
 */
@Component
@RequiredArgsConstructor
public class JobTerminalNotificationSupport {

  /** 通知目标解析注册表。 */
  private final NotificationTargetResolverRegistry targetResolvers;

  /**
   * 组装通知标题；可选附带展示名（课程名、文章名等）。
   *
   * @param jobType     任务类型
   * @param succeeded   是否成功
   * @param displayName 展示名；空则只用类型文案
   * @return 中文标题
   */
  public String titleFor(String jobType, boolean succeeded, String displayName) {
    return titleFor(jobType, succeeded, displayName, null);
  }

  /**
   * 组装通知标题；可据任务出参区分「全文完成」与「正文落库、后续仍在跑」。
   *
   * @param jobType     任务类型
   * @param succeeded   是否成功
   * @param displayName 展示名；空则只用类型文案
   * @param output      任务出参；可空
   * @return 中文标题
   */
  public String titleFor(
      String jobType, boolean succeeded, String displayName, Map<String, Object> output) {
    String base = baseTitle(jobType, succeeded, output);
    if (StrUtil.isBlank(displayName)) {
      return base;
    }
    return base + "：" + displayName.trim();
  }

  /**
   * 成功终态的短正文。
   *
   * @param jobType 任务类型
   * @return 成功说明
   */
  public String successMessage(String jobType) {
    return successMessage(jobType, null);
  }

  /**
   * 成功终态的短正文；改写若仍有配图重生则不写成「全部完成」。
   *
   * @param jobType 任务类型
   * @param output  任务出参；可空
   * @return 成功说明
   */
  public String successMessage(String jobType, Map<String, Object> output) {
    if (JobTypes.HOT_NEWS_FETCH.equals(jobType)) {
      return "雷达采集已完成";
    }
    if (JobTypes.HOT_NEWS_TOPIC_RESEARCH.equals(jobType)) {
      return "选题研究已完成";
    }
    if (JobTypes.HOT_NEWS_VIDEO_PARSE.equals(jobType)
        || JobTypes.HOT_NEWS_SOURCE_VIDEO_PARSE.equals(jobType)) {
      return "视频解析已完成";
    }
    if (JobTypes.HOT_NEWS_ARTICLE_REWRITE.equals(jobType) && rewriteHasPendingImages(output)) {
      return "正文已更新；配套图片正在重生，请稍候";
    }
    if (JobTypes.HOT_NEWS_ARTICLE_GENERATION.equals(jobType)) {
      String scope = generationScope(output);
      if (HotNewsGenerationScopes.isImages(scope)) {
        return "配套图片已更新";
      }
      if (HotNewsGenerationScopes.isWechat(scope)) {
        return "公众号优化版已更新";
      }
      if (HotNewsGenerationScopes.DRAFT.equals(scope)) {
        return "热点原稿已更新";
      }
      return "任务已成功完成";
    }
    if (JobTypes.HOT_NEWS_ARTICLE_REWRITE.equals(jobType)) {
      return "任务已成功完成";
    }
    if (JobTypes.HOT_ARTICLE_REVIEW.equals(jobType)
        || JobTypes.HOT_ARTICLE_REVIEW_REPAIR.equals(jobType)
        || JobTypes.HOT_ARTICLE_REVIEW_REVALIDATE.equals(jobType)) {
      return "审核任务已完成";
    }
    if (JobTypes.THEME_CURRICULUM_GENERATION.equals(jobType)) {
      return "课表草案已生成";
    }
    if (JobTypes.COURSE_CONTENT_GENERATION.equals(jobType)
        || JobTypes.COURSE_DRAFT_GENERATION.equals(jobType)
        || JobTypes.WECHAT_ARTICLE_GENERATION.equals(jobType)) {
      return "任务已成功完成";
    }
    if (JobTypes.IMAGE_GENERATION.equals(jobType)) {
      return "配图任务已完成";
    }
    if (JobTypes.ARTICLE_REPAIR.equals(jobType)) {
      return "文章修复已完成";
    }
    return "任务已成功完成";
  }

  /**
   * 按任务类型与实体补齐跳转用 metadata，并返回可用于标题的展示名。
   *
   * @param jobType   任务类型
   * @param entityId  业务实体 ID
   * @param metadata  可变 metadata（已含 output 等）
   * @return 展示名；无则空串
   */
  public String enrichMetadata(String jobType, String entityId, Map<String, Object> metadata) {
    metadata.put(NotificationMetadataKeys.JOB_TYPE, jobType);
    if (StrUtil.isBlank(entityId)) {
      return "";
    }
    return targetResolvers.resolve(jobType, entityId, metadata).orElse("");
  }

  /**
   * 类型级基础标题（不含实体名）；可据出参微调改写成功文案。
   *
   * @param jobType   任务类型
   * @param succeeded 是否成功
   * @param output    任务出参；可空
   * @return 基础标题
   */
  private static String baseTitle(String jobType, boolean succeeded, Map<String, Object> output) {
    if (JobTypes.COURSE_CONTENT_GENERATION.equals(jobType)) {
      return succeeded ? "课程内容生成完成" : "课程内容生成失败";
    }
    if (JobTypes.HOT_NEWS_ARTICLE_GENERATION.equals(jobType)) {
      String scope = generationScope(output);
      if (HotNewsGenerationScopes.isImages(scope)) {
        return succeeded ? "热点配套图片生成完成" : "热点配套图片生成失败";
      }
      if (HotNewsGenerationScopes.isWechat(scope)) {
        return succeeded ? "热点公众号版本生成完成" : "热点公众号版本生成失败";
      }
      if (HotNewsGenerationScopes.DRAFT.equals(scope)) {
        return succeeded ? "热点原稿生成完成" : "热点原稿生成失败";
      }
      return succeeded ? "热点文章生成完成" : "热点文章生成失败";
    }
    if (JobTypes.HOT_NEWS_ARTICLE_REWRITE.equals(jobType)) {
      if (!succeeded) {
        return "热点文章改写失败";
      }
      // 改写 Job 结束 ≠ 整条优化流水线结束：仍有配图时避免「完成」误导
      return rewriteHasPendingImages(output) ? "热点文章改写已落库" : "热点文章改写完成";
    }
    if (JobTypes.HOT_NEWS_FETCH.equals(jobType)) {
      return succeeded ? "雷达采集完成" : "雷达采集失败";
    }
    if (JobTypes.HOT_NEWS_TOPIC_RESEARCH.equals(jobType)) {
      return succeeded ? "选题研究完成" : "选题研究失败";
    }
    if (JobTypes.HOT_NEWS_VIDEO_PARSE.equals(jobType)) {
      return succeeded ? "资料池视频解析完成" : "资料池视频解析失败";
    }
    if (JobTypes.HOT_NEWS_SOURCE_VIDEO_PARSE.equals(jobType)) {
      return succeeded ? "信源视频解析完成" : "信源视频解析失败";
    }
    if (JobTypes.HOT_ARTICLE_REVIEW.equals(jobType)) {
      return succeeded ? "热点文章审核完成" : "热点文章审核失败";
    }
    if (JobTypes.HOT_ARTICLE_REVIEW_REPAIR.equals(jobType)) {
      return succeeded ? "热点文章审核修复完成" : "热点文章审核修复失败";
    }
    if (JobTypes.HOT_ARTICLE_REVIEW_REVALIDATE.equals(jobType)) {
      return succeeded ? "热点文章复检完成" : "热点文章复检失败";
    }
    if (JobTypes.COURSE_DRAFT_GENERATION.equals(jobType)) {
      return succeeded ? "课程草稿生成完成" : "课程草稿生成失败";
    }
    if (JobTypes.WECHAT_ARTICLE_GENERATION.equals(jobType)) {
      return succeeded ? "公众号文章生成完成" : "公众号文章生成失败";
    }
    if (JobTypes.IMAGE_GENERATION.equals(jobType)) {
      return succeeded ? "配图生成完成" : "配图生成失败";
    }
    if (JobTypes.ARTICLE_REPAIR.equals(jobType)) {
      return succeeded ? "文章修复完成" : "文章修复失败";
    }
    if (JobTypes.THEME_CURRICULUM_GENERATION.equals(jobType)) {
      return succeeded ? "课表生成完成" : "课表生成失败";
    }
    return succeeded ? "任务已完成" : "任务失败";
  }

  /**
   * 改写出参是否标明仍有配图重生（已入队或待入队）。
   *
   * @param output 任务出参；可空
   * @return 仍有配图后续则为 true
   */
  private static boolean rewriteHasPendingImages(Map<String, Object> output) {
    if (output == null || output.isEmpty()) {
      return false;
    }
    Object pending = output.get(HotNewsRewriteJobKeys.IMAGES_REGEN_PENDING);
    if (Boolean.TRUE.equals(pending)) {
      return true;
    }
    if (pending instanceof String text && "true".equalsIgnoreCase(text.trim())) {
      return true;
    }
    Object imagesJobId = output.get(HotNewsRewriteJobKeys.IMAGES_REGEN_JOB_ID);
    return imagesJobId != null && StrUtil.isNotBlank(String.valueOf(imagesJobId).trim());
  }

  /**
   * 从生成任务出参读取规范化 scope；缺省 full。
   *
   * @param output 任务出参；可空
   * @return scope 字面量
   */
  private static String generationScope(Map<String, Object> output) {
    if (output == null || output.isEmpty()) {
      return HotNewsGenerationScopes.FULL;
    }
    Object scope = output.get(HotNewsArticleJobKeys.SCOPE);
    if (scope == null) {
      return HotNewsGenerationScopes.FULL;
    }
    return HotNewsGenerationScopes.normalize(String.valueOf(scope));
  }
}
