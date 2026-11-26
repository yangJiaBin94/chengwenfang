package com.chengwenfang.mode.hotnews.facade.enums;

import cn.hutool.core.util.StrUtil;
import java.util.Locale;

/**
 * 选题素材形态（AI 写作评分时打标，非渠道 type）。
 */
public enum TopicMaterialKind {
  /** 学术论文 / 预印本 / 论文库条目。 */
  PAPER("paper"),
  /** 产品发布、官方更新、功能上线。 */
  PRODUCT("product"),
  /** 新闻报道、媒体解读。 */
  NEWS("news"),
  /** 开源仓库 / 项目发布。 */
  OSS("oss"),
  /** 其它或难以归类。 */
  OTHER("other");

  private final String value;

  TopicMaterialKind(String value) {
    this.value = value;
  }

  /** @return JSON / 落库字面量 */
  public String getValue() {
    return value;
  }

  /**
   * 解析字面量；未知或空返回 {@link #OTHER}。
   *
   * @param raw AI 或库内字符串
   * @return 枚举
   */
  public static TopicMaterialKind from(String raw) {
    if (StrUtil.isBlank(raw)) {
      return OTHER;
    }
    String key = raw.trim().toLowerCase(Locale.ROOT);
    for (TopicMaterialKind kind : values()) {
      if (kind.value.equals(key)) {
        return kind;
      }
    }
    return OTHER;
  }

  /**
   * 是否为论文/预印本类素材（单源时不得 lead）。
   *
   * @return true 表示学术论文族
   */
  public boolean isAcademicPaper() {
    return this == PAPER;
  }
}
