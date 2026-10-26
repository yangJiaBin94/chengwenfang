package com.chengwenfang.mode.hotnews.facade.constant;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.content.facade.constant.OutputTrackKinds;

/**
 * 热点文章双轨内容更新时的 kind 词（开集；与 API path / OutputTrackKinds 对齐）。
 */
public final class HotNewsArticleContentKinds {
  /** 草稿轨。 */
  public static final String DRAFT = OutputTrackKinds.HOT_DRAFT;
  /** 公众号正文轨。 */
  public static final String WECHAT = OutputTrackKinds.HOT_WECHAT;
  /** 公众号正文轨别名（与 wechat 同义）。 */
  public static final String CONTENT = "content";

  private HotNewsArticleContentKinds() {}

  /**
   * 规范化 kind：空白→空串；大小写不敏感。
   *
   * @param kind 原始 kind
   * @return 小写 trim 后的值
   */
  public static String normalize(String kind) {
    return StrUtil.nullToEmpty(kind).trim().toLowerCase();
  }

  /**
   * 是否为草稿轨。
   *
   * @param kind 已规范化或原始 kind
   * @return true 表示改草稿
   */
  public static boolean isDraft(String kind) {
    return DRAFT.equals(normalize(kind));
  }

  /**
   * 是否为公众号正文轨（含 content 别名）。
   *
   * @param kind 已规范化或原始 kind
   * @return true 表示改公众号正文
   */
  public static boolean isWechat(String kind) {
    String field = normalize(kind);
    return WECHAT.equals(field) || CONTENT.equals(field);
  }

  /**
   * 是否为合法更新 kind。
   *
   * @param kind 原始 kind
   * @return true 表示可写入
   */
  public static boolean isValidUpdateKind(String kind) {
    return isDraft(kind) || isWechat(kind);
  }
}
