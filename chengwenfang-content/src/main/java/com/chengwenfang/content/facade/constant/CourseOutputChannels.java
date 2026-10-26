package com.chengwenfang.content.facade.constant;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.enums.ArticleKind;

/**
 * 课程文章 kind 与产出渠道/格式映射。
 */
public final class CourseOutputChannels {
  private CourseOutputChannels() {}

  /**
   * 由文章 kind 解析渠道 code。
   *
   * @param kind course_draft / wechat_article
   * @return master 或 wechat_mp
   */
  public static String channelCode(String kind) {
    if (ArticleKind.WECHAT_ARTICLE.getValue().equals(StrUtil.trim(kind))) {
      return ChannelCodes.WECHAT_MP;
    }
    return ChannelCodes.MASTER;
  }

  /**
   * 由 {@link ArticleKind} 解析渠道 code。
   *
   * @param kind 文章 kind
   * @return 渠道 code
   */
  public static String channelCode(ArticleKind kind) {
    if (kind == null) {
      return ChannelCodes.MASTER;
    }
    return channelCode(kind.getValue());
  }

  /**
   * 课程产出固定格式。
   *
   * @return article
   */
  public static String formatCode() {
    return OutputFormatCodes.ARTICLE;
  }
}
