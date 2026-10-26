package com.workbench.common;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将任务失败的技术错误信息转成用户可读的中文说明（通知、执行页摘要等）。
 */
public final class JobFailureMessages {
  /** 正文超限：捕获标签与字数。 */
  private static final Pattern BODY_TOO_LONG = Pattern.compile(
      "COURSE_BODY_TOO_LONG:\\s*(\\S+)\\s*正文\\s*(\\d+)\\s*超过\\s*(\\d+)",
      Pattern.CASE_INSENSITIVE);

  /** 配图槽位：第 N/M 张。 */
  private static final Pattern IMAGE_SLOT = Pattern.compile("第\\s*(\\d+)\\s*/\\s*(\\d+)\\s*张");

  private JobFailureMessages() {}

  /**
   * 转成直白中文失败说明。
   *
   * @param rawMessage 原始错误信息（可为英文异常或业务码前缀）
   * @return 用户可读中文；空输入返回通用失败文案
   */
  public static String toUserMessage(String rawMessage) {
    if (rawMessage == null || rawMessage.isBlank()) {
      return "任务没有完成，请稍后重新执行。";
    }
    String text = rawMessage.strip();
    Matcher bodyTooLong = BODY_TOO_LONG.matcher(text);
    if (bodyTooLong.find()) {
      String label = bodyTooLong.group(1);
      String current = bodyTooLong.group(2);
      String limit = bodyTooLong.group(3);
      String kind = label.contains("公众号")
          ? "公众号文章"
          : (label.contains("原稿") ? "课程原稿" : "正文");
      return kind + "写得有点长了（约 " + current + " 字，上限 " + limit + " 字），请缩短后再生成。";
    }
    String lower = text.toLowerCase(Locale.ROOT);
    Matcher imageSlot = IMAGE_SLOT.matcher(text);
    boolean hasImageSlot = imageSlot.find();
    String slotLabel = hasImageSlot
        ? ("第 " + imageSlot.group(1) + "/" + imageSlot.group(2) + " 张")
        : null;
    if (containsAny(lower, "read timed out", "timeout", "timed out", "sockettimeout")
        || text.contains("请求超时")
        || text.contains("配图服务请求超时")) {
      if (slotLabel != null || text.contains("生成配套图片") || text.contains("配图")) {
        return slotLabel == null
            ? "配图服务请求超时，请稍后重新执行失败步骤。"
            : slotLabel + "未生成成功。原因：配图服务请求超时，请稍后重新执行失败步骤。";
      }
      return "请求超时了（常见于配图或模型调用），请稍后重新执行。";
    }
    if (containsAny(lower, "connection refused", "connect timed out", "unknownhost", "network is unreachable")) {
      if (slotLabel != null) {
        return slotLabel + "未生成成功。原因：连不上配图服务，请检查网络后重新执行。";
      }
      return "网络连不上服务，请检查网络后重新执行。";
    }
    if (text.contains("生成配套图片") && looksMostlyChinese(text)) {
      // 去掉冗余的「第 N 步「生成配套图片」失败：」前缀（步骤标题已标明）
      String trimmed = text.replaceFirst("^第\\s*\\d+\\s*步「生成配套图片」失败：\\s*", "").strip();
      if (trimmed.isBlank()) {
        trimmed = text;
      }
      return trimmed.length() > 220 ? trimmed.substring(0, 220).strip() + "…" : trimmed;
    }
    if (slotLabel != null && (text.contains("配图") || text.contains("生成配套图片") || text.contains("插图"))) {
      return slotLabel + "未生成成功，请重新执行失败步骤。";
    }
    if (containsAny(text, "COURSE_IMAGE", "IMAGE_GENERATION", "配图")
        && containsAny(lower, "fail", "invalid", "error", "失败")) {
      return "配图没有生成成功，请重新执行配图步骤。";
    }
    if (containsAny(text, "WORD_COUNT", "字数")) {
      return "字数不符合当前设置的范围，请调整字数档或缩短正文后重试。";
    }
    if (containsAny(text, "WECHAT_CONTENT", "WECHAT_REVIEW", "公众号审校")) {
      return "公众号文章检查没通过，请重新生成或审校后再试。";
    }
    if (containsAny(text, "GENERATED_DRAFT", "COURSE_CONTENT_STRUCTURE", "原稿不完整")) {
      return "课程原稿还不完整，请重新生成。";
    }
    if (containsAny(text, "FINAL_VALIDATION", "完整性")) {
      return "最终检查没通过，请查看执行轨迹里的失败步骤后重试。";
    }
    if (containsAny(text, "MATERIAL_REQUEST", "索要素材")) {
      return "生成内容向用户索要了素材，已拦截，请重新生成。";
    }
    // 去掉英文业务码前缀，只留中文说明
    String withoutCode = text.replaceFirst("^[A-Z][A-Z0-9_]+:\\s*", "").strip();
    if (!withoutCode.isBlank() && looksMostlyChinese(withoutCode)) {
      return withoutCode;
    }
    if (!withoutCode.isBlank() && !withoutCode.equals(text) && withoutCode.length() < 120) {
      return withoutCode;
    }
    return "任务没有完成，请打开执行轨迹查看失败步骤后重新执行。";
  }

  private static boolean containsAny(String haystack, String... needles) {
    for (String needle : needles) {
      if (haystack.contains(needle)) {
        return true;
      }
    }
    return false;
  }

  private static boolean looksMostlyChinese(String text) {
    int chinese = 0;
    int letters = 0;
    for (int index = 0; index < text.length(); index++) {
      char ch = text.charAt(index);
      if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
        chinese++;
      } else if (Character.isLetter(ch)) {
        letters++;
      }
    }
    return chinese > 0 && chinese >= letters;
  }
}
