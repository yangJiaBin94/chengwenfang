package com.chengwenfang.knowledge.domain.rule;

import cn.hutool.core.util.StrUtil;
import java.util.regex.Pattern;

/**
 * 知识库引用标题展示：把课程 ID 等技术名换成可读标题。
 */
public final class KnowledgeCitationTitles {

  /** 课程作品技术 ID：course-{themeCode}-{uuid…}。 */
  private static final Pattern COURSE_TECHNICAL_ID =
      Pattern.compile("(?i)^course-[a-z0-9_]+-[0-9a-f-]{8,}.*$");

  private KnowledgeCitationTitles() {}

  /**
   * 解析引用展示标题；技术 ID 时优先用章节路径首段。
   *
   * @param storedTitle 入库标题
   * @param headingPath 命中块标题路径
   * @return 展示标题
   */
  public static String resolve(String storedTitle, String headingPath) {
    String raw = StrUtil.nullToEmpty(storedTitle).trim();
    ChannelLabel channel = splitChannelSuffix(raw);
    String base = channel.base();
    if (!isTechnicalTitle(base, null)) {
      return compose(base, channel.suffix());
    }
    String fromPath = firstHeadingSegment(headingPath);
    if (StrUtil.isNotBlank(fromPath)) {
      return compose(fromPath, channel.suffix());
    }
    if (StrUtil.isNotBlank(channel.suffix())) {
      return channel.suffix();
    }
    return "未命名文档";
  }

  /**
   * 入库标题（可含「原稿/公众号」后缀）是否仍为技术 ID，需要换成文章名展示。
   *
   * @param storedTitle 入库标题
   * @return 需要友好展示时 true
   */
  public static boolean looksTechnical(String storedTitle) {
    ChannelLabel channel = splitChannelSuffix(StrUtil.nullToEmpty(storedTitle).trim());
    return isTechnicalTitle(channel.base(), null);
  }

  /**
   * 判断是否为技术向作品标题。
   *
   * @param title  标题（不含渠道后缀）
   * @param workId 作品 ID
   * @return 是技术标题则为 true
   */
  public static boolean isTechnicalTitle(String title, String workId) {
    String text = StrUtil.nullToEmpty(title).trim();
    if (StrUtil.isBlank(text)) {
      return true;
    }
    if (StrUtil.isNotBlank(workId) && text.equals(workId.trim())) {
      return true;
    }
    return COURSE_TECHNICAL_ID.matcher(text).matches();
  }

  /**
   * 取标题路径第一段。
   *
   * @param headingPath 路径
   * @return 首段
   */
  static String firstHeadingSegment(String headingPath) {
    String text = StrUtil.nullToEmpty(headingPath).trim();
    if (StrUtil.isBlank(text)) {
      return "";
    }
    int split = text.indexOf('>');
    if (split < 0) {
      return text;
    }
    return text.substring(0, split).trim();
  }

  /**
   * 拆出「原稿 / 公众号」渠道后缀。
   *
   * @param title 原标题
   * @return 基名与后缀
   */
  private static ChannelLabel splitChannelSuffix(String title) {
    String text = StrUtil.nullToEmpty(title).trim();
    if (text.endsWith(" · 原稿") || text.endsWith(" 原稿")) {
      int cut = text.endsWith(" · 原稿") ? 5 : 3;
      return new ChannelLabel(text.substring(0, text.length() - cut).trim(), "原稿");
    }
    if (text.endsWith(" · 公众号") || text.endsWith(" 公众号")) {
      int cut = text.endsWith(" · 公众号") ? 5 : 4;
      return new ChannelLabel(text.substring(0, text.length() - cut).trim(), "公众号");
    }
    return new ChannelLabel(text, "");
  }

  /**
   * 拼接基名与渠道后缀。
   *
   * @param base   基名
   * @param suffix 渠道后缀
   * @return 展示标题
   */
  private static String compose(String base, String suffix) {
    String name = StrUtil.nullToEmpty(base).trim();
    String channel = StrUtil.nullToEmpty(suffix).trim();
    if (StrUtil.isBlank(name)) {
      return channel;
    }
    if (StrUtil.isBlank(channel)) {
      return name;
    }
    return name + " · " + channel;
  }

  /**
   * 渠道后缀拆分结果。
   *
   * @param base   基名
   * @param suffix 渠道名
   */
  private record ChannelLabel(String base, String suffix) {}
}
