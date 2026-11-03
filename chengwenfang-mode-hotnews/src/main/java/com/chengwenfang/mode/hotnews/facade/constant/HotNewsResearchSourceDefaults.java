package com.chengwenfang.mode.hotnews.facade.constant;

import cn.hutool.core.util.StrUtil;
import java.util.List;

/**
 * 资料补充来源策略默认值（无主题策略时的兜底屏蔽域等）。
 */
public final class HotNewsResearchSourceDefaults {
  /** 默认屏蔽：补搜时跳过这些域名的命中（仍保留选题原始来源）。 */
  public static final List<String> DEFAULT_BLOCKED_DOMAINS = List.of(
      "bilibili.com",
      "b23.tv",
      "youtube.com",
      "youtu.be");

  /** 金融类主题常用优选域名。 */
  public static final List<String> FINANCE_PREFERRED_DOMAINS = List.of(
      "eastmoney.com",
      "cninfo.com.cn",
      "cnstock.com",
      "stcn.com",
      "cls.cn",
      "10jqka.com.cn",
      "cs.com.cn",
      "gu.qq.com",
      "finance.sina.com.cn");

  /** 金融类默认角度词。 */
  public static final List<String> FINANCE_QUERY_ANGLES = List.of(
      "上市",
      "发行价",
      "公告",
      "首日");

  private HotNewsResearchSourceDefaults() {}

  /**
   * 是否按金融主题处理（packKey 或主题名启发式）。
   *
   * @param themeName 主题名；可空
   * @param packKey   策略包键；可空
   * @return 金融向时 true
   */
  public static boolean looksFinanceTheme(String themeName, String packKey) {
    if ("finance".equalsIgnoreCase(StrUtil.trim(packKey))) {
      return true;
    }
    String name = StrUtil.nullToEmpty(themeName);
    return name.contains("股票") || name.contains("金融") || name.contains("证券") || name.contains("财经");
  }
}
