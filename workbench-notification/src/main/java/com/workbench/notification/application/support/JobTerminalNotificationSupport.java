package com.workbench.notification.application.support;

import cn.hutool.core.util.StrUtil;
import com.workbench.job.facade.constant.JobTypes;
import com.workbench.notification.facade.constant.NotificationMetadataKeys;
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
    String base = baseTitle(jobType, succeeded);
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
    if (JobTypes.HOT_NEWS_ARTICLE_GENERATION.equals(jobType)
        || JobTypes.HOT_NEWS_ARTICLE_REWRITE.equals(jobType)) {
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
   * 类型级基础标题（不含实体名）。
   *
   * @param jobType   任务类型
   * @param succeeded 是否成功
   * @return 基础标题
   */
  private static String baseTitle(String jobType, boolean succeeded) {
    if (JobTypes.COURSE_CONTENT_GENERATION.equals(jobType)) {
      return succeeded ? "课程内容生成完成" : "课程内容生成失败";
    }
    if (JobTypes.HOT_NEWS_ARTICLE_GENERATION.equals(jobType)) {
      return succeeded ? "热点文章生成完成" : "热点文章生成失败";
    }
    if (JobTypes.HOT_NEWS_ARTICLE_REWRITE.equals(jobType)) {
      return succeeded ? "热点文章改写完成" : "热点文章改写失败";
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
}
