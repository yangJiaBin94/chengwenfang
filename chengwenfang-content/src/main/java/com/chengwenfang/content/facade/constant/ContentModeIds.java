package com.chengwenfang.content.facade.constant;

import cn.hutool.core.util.StrUtil;

/**
 * 内容教学模式稳定 id / code（闭集键，禁止业务侧散落字面量）。
 *
 * <p>v2 起模式主键为 code；路径参数仍双受理历史 id（{@code mode-*}）与 code。
 */
public final class ContentModeIds {
  private ContentModeIds() {}

  /** 课程体系教学模式历史 id（路径双受理）。 */
  public static final String COURSE_SYSTEM = "mode-course-system";
  /** 课程体系教学模式 code（主键）。 */
  public static final String COURSE_SYSTEM_CODE = "course_system";
  /** 热点资讯模式历史 id（路径双受理）。 */
  public static final String HOT_NEWS = "mode-hot-news";
  /** 热点资讯模式 code（主键）。 */
  public static final String HOT_NEWS_CODE = "hot_news";

  /**
   * 将路径/入参中的模式 id 或 code 解析为表主键 code。
   *
   * @param modeIdOrCode 模式 id（mode-*）或 code；可空
   * @return 规范化 code；无法识别时返回 trim 后的原值（可能仍查库失败）
   */
  public static String resolveCode(String modeIdOrCode) {
    if (StrUtil.isBlank(modeIdOrCode)) {
      return modeIdOrCode;
    }
    String raw = StrUtil.trim(modeIdOrCode);
    if (COURSE_SYSTEM.equals(raw) || COURSE_SYSTEM_CODE.equals(raw)) {
      return COURSE_SYSTEM_CODE;
    }
    if (HOT_NEWS.equals(raw) || HOT_NEWS_CODE.equals(raw)) {
      return HOT_NEWS_CODE;
    }
    return raw;
  }

  /**
   * 是否为课程体系模式。
   *
   * @param modeId   模式 id 或 code
   * @param modeCode 模式 code（可与 modeId 相同）
   * @return 是课程体系时 true
   */
  public static boolean isCourseSystem(String modeId, String modeCode) {
    return COURSE_SYSTEM.equals(modeId)
        || COURSE_SYSTEM_CODE.equals(modeId)
        || COURSE_SYSTEM.equals(modeCode)
        || COURSE_SYSTEM_CODE.equals(modeCode);
  }

  /**
   * 是否为热点资讯模式。
   *
   * @param modeId   模式 id 或 code
   * @param modeCode 模式 code（可与 modeId 相同）
   * @return 是热点资讯时 true
   */
  public static boolean isHotNews(String modeId, String modeCode) {
    return HOT_NEWS.equals(modeId)
        || HOT_NEWS_CODE.equals(modeId)
        || HOT_NEWS.equals(modeCode)
        || HOT_NEWS_CODE.equals(modeCode);
  }
}
