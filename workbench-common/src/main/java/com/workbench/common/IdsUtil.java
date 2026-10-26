package com.workbench.common;

/** 生成业务侧主键（UUID 字符串）的工具类。 */
public final class IdsUtil {
  private IdsUtil() {}

  /** 生成新的 UUID 字符串主键。 */
  public static String newId() {
    return java.util.UUID.randomUUID().toString();
  }
}
