package com.chengwenfang.content.domain;

import cn.hutool.core.util.StrUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Markdown 正文抽取 {@code /api/images/{id}} 引用（按出现顺序去重）。
 */
public final class MarkdownImageRefs {
  private static final Pattern IMAGE_API_PATH =
      Pattern.compile("/api/images/([^)\\s]+)");

  private MarkdownImageRefs() {}

  /**
   * 按正文出现顺序收集配图 id，重复引用只保留首次。
   *
   * @param markdown 正文；可空
   * @return 有序 id 列表；无引用时为空列表
   */
  public static List<String> imageIdsInOrder(String markdown) {
    if (StrUtil.isBlank(markdown)) {
      return List.of();
    }
    LinkedHashSet<String> ordered = new LinkedHashSet<>();
    Matcher matcher = IMAGE_API_PATH.matcher(markdown);
    while (matcher.find()) {
      String imageId = StrUtil.trim(matcher.group(1));
      if (StrUtil.isNotBlank(imageId)) {
        ordered.add(imageId);
      }
    }
    return new ArrayList<>(ordered);
  }
}
