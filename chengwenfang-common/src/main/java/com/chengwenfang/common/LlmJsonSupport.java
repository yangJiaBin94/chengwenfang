package com.chengwenfang.common;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

/**
 * 从模型自由文本中截取首尾花括号 JSON，并反序列化为类型（模型常夹杂说明文字）。
 */
public final class LlmJsonSupport {
  private LlmJsonSupport() {}

  /**
   * 截取文本中第一段 `{...}` 并解析为指定类型。
   *
   * @param text       模型原文；可空
   * @param beanClass  目标类型
   * @param <T>        类型参数
   * @return 解析结果；失败返回 null
   */
  public static <T> T parseObject(String text, Class<T> beanClass) {
    if (StrUtil.isBlank(text) || beanClass == null) {
      return null;
    }
    String raw = text.trim();
    int start = raw.indexOf('{');
    int end = raw.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return null;
    }
    try {
      return JSONUtil.toBean(raw.substring(start, end + 1), beanClass);
    } catch (Exception ignored) {
      return null;
    }
  }
}
