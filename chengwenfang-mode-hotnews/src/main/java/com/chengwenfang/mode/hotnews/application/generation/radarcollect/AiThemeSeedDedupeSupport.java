package com.chengwenfang.mode.hotnews.application.generation.radarcollect;

import cn.hutool.core.util.StrUtil;
import com.chengwenfang.mode.hotnews.application.generation.radarcollect.model.AiThemeSeedAiResult;
import com.chengwenfang.mode.hotnews.domain.ChineseTextNormalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 主题种子标题规范化与去重自检。
 */
public final class AiThemeSeedDedupeSupport {
  private AiThemeSeedDedupeSupport() {}

  /** 首次生成不足或撞题后的最大补生成次数。 */
  public static final int MAX_REFILL_RETRIES = 2;

  /**
   * 标题去重键：简体 + trim；空标题返回空串。
   *
   * @param title 原始标题
   * @return 规范化键
   */
  public static String titleKey(String title) {
    return StrUtil.trim(StrUtil.nullToEmpty(ChineseTextNormalizer.toSimplified(title)));
  }

  /**
   * 从模型结果中取出互不重复、且不落在已占用键集合内的条目。
   *
   * @param items        模型条目
   * @param reservedKeys 已占用标题键（本批已有 + 本轮已收下）
   * @param limit        最多收取条数
   * @return 去重后的新条目（不修改 reservedKeys）
   */
  public static List<AiThemeSeedAiResult> takeUnique(
      List<AiThemeSeedAiResult> items, Set<String> reservedKeys, int limit) {
    if (items == null || items.isEmpty() || limit <= 0) {
      return List.of();
    }
    Set<String> reserved = reservedKeys == null ? Set.of() : reservedKeys;
    Set<String> seenInBatch = new LinkedHashSet<>();
    List<AiThemeSeedAiResult> out = new ArrayList<>();
    for (AiThemeSeedAiResult item : items) {
      if (item == null
          || StrUtil.isBlank(item.getTitle())
          || StrUtil.isBlank(item.getSummary())) {
        continue;
      }
      String key = titleKey(item.getTitle());
      if (StrUtil.isBlank(key)) {
        continue;
      }
      if (reserved.contains(key) || !seenInBatch.add(key)) {
        continue;
      }
      out.add(item);
      if (out.size() >= limit) {
        break;
      }
    }
    return out;
  }

  /**
   * 将标题列表转为去重键集合（保持插入顺序）。
   *
   * @param titles 标题
   * @return 键集合
   */
  public static Set<String> toKeySet(Collection<String> titles) {
    Set<String> keys = new LinkedHashSet<>();
    if (titles == null) {
      return keys;
    }
    for (String title : titles) {
      String key = titleKey(title);
      if (StrUtil.isNotBlank(key)) {
        keys.add(key);
      }
    }
    return keys;
  }
}
